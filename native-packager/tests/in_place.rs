//! Unsigned writes, the plan (removed and stored entries) and in-place signing.
//! Keys are generated with `openssl` at test time, as in `roundtrip.rs`.

use native_packager::{package, package_with, ApkSigner, Options, Plan};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

struct Scratch(PathBuf);

impl Scratch {
    fn new(tag: &str) -> Self {
        let dir = std::env::temp_dir().join(format!("np-inplace-{}-{tag}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        Scratch(dir)
    }

    fn path(&self) -> &Path {
        &self.0
    }
}

impl Drop for Scratch {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.0);
    }
}

fn signer(dir: &Path) -> ApkSigner {
    let key = dir.join("key.pem");
    let cert = dir.join("cert.pem");
    let status = Command::new("openssl")
        .args(["genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out"])
        .arg(&key)
        .status()
        .expect("openssl must be on PATH");
    assert!(status.success());
    let status = Command::new("openssl")
        .args(["req", "-new", "-x509", "-days", "3650", "-subj", "/CN=in-place test", "-key"])
        .arg(&key)
        .arg("-out")
        .arg(&cert)
        .status()
        .expect("openssl req failed to start");
    assert!(status.success());
    ApkSigner::from_pem(&fs::read_to_string(key).unwrap(), &fs::read_to_string(cert).unwrap())
        .unwrap()
}

/// A zip whose large entry spans more than two 1 MiB digest chunks.
fn synthetic_input(path: &Path) {
    let mut zip = ZipWriter::new(fs::File::create(path).unwrap());
    let deflated = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
    let stored = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
    zip.start_file("AndroidManifest.xml", deflated).unwrap();
    zip.write_all(b"<manifest/>").unwrap();
    zip.start_file("resources.arsc", stored).unwrap();
    zip.write_all(&[7u8; 4096]).unwrap();
    zip.start_file("classes.dex", deflated).unwrap();
    zip.write_all(b"dex one").unwrap();
    zip.start_file("classes2.dex", deflated).unwrap();
    zip.write_all(b"dex two").unwrap();
    zip.start_file("assets/a.txt", deflated).unwrap();
    zip.write_all(b"asset").unwrap();
    zip.start_file("lib/arm64-v8a/libx.so", stored).unwrap();
    zip.write_all(&[1u8; 333]).unwrap();
    zip.start_file("assets/big.bin", deflated).unwrap();
    let big: Vec<u8> = (0..(3 << 20) + 17).map(|i| (i * 31 % 251) as u8).collect();
    zip.write_all(&big).unwrap();
    zip.finish().unwrap();
}

fn entries(path: &Path) -> BTreeMap<String, (Vec<u8>, CompressionMethod)> {
    let mut archive = ZipArchive::new(fs::File::open(path).unwrap()).unwrap();
    let mut out = BTreeMap::new();
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).unwrap();
        let method = entry.compression();
        let mut data = Vec::new();
        entry.read_to_end(&mut data).unwrap();
        out.insert(entry.name().to_string(), (data, method));
    }
    out
}

#[test]
fn signing_in_place_matches_signing_during_the_write() {
    let dir = Scratch::new("same");
    let signer = signer(dir.path());
    let input = dir.path().join("in.apk");
    synthetic_input(&input);
    let entries_map = BTreeMap::from([("assets/a.txt".to_string(), input.clone())]);

    let signed = dir.path().join("signed.apk");
    package(&Options {
        input: &input,
        output: &signed,
        entries: entries_map.clone(),
        signer: Some(&signer),
    })
    .unwrap();

    let unsigned = dir.path().join("unsigned.apk");
    let report = package_with(
        &Options {
            input: &input,
            output: &unsigned,
            entries: entries_map,
            signer: None,
        },
        &Plan::default(),
    )
    .unwrap();
    assert_eq!(report.output_bytes, fs::metadata(&unsigned).unwrap().len());
    let end = native_packager::sign_in_place(&unsigned, &signer).unwrap();
    assert_eq!(end, fs::metadata(&unsigned).unwrap().len());

    assert_eq!(fs::read(&signed).unwrap(), fs::read(&unsigned).unwrap());
}

#[test]
fn signing_a_signed_apk_in_place_is_refused() {
    let dir = Scratch::new("twice");
    let signer = signer(dir.path());
    let input = dir.path().join("in.apk");
    synthetic_input(&input);
    let signed = dir.path().join("signed.apk");
    package(&Options {
        input: &input,
        output: &signed,
        entries: BTreeMap::new(),
        signer: Some(&signer),
    })
    .unwrap();
    let before = fs::read(&signed).unwrap();
    assert!(native_packager::sign_in_place(&signed, &signer).is_err());
    assert_eq!(fs::read(&signed).unwrap(), before, "a refused sign must not touch the file");
}

#[test]
fn plan_removes_entries_and_sets_compression_by_name() {
    let dir = Scratch::new("plan");
    let input = dir.path().join("in.apk");
    synthetic_input(&input);
    let output = dir.path().join("out.apk");
    let plan = Plan {
        remove: BTreeSet::from(["classes2.dex".to_string()]),
        stored: BTreeMap::from([("assets/a.txt".to_string(), true)]),
    };
    let report = package_with(
        &Options {
            input: &input,
            output: &output,
            entries: BTreeMap::from([
                ("classes.dex".to_string(), input.clone()),
                ("assets/a.txt".to_string(), input.clone()),
            ]),
            signer: None,
        },
        &plan,
    )
    .unwrap();
    assert_eq!(report.removed, 1);
    assert_eq!(report.replaced, 2);

    let out = entries(&output);
    assert!(!out.contains_key("classes2.dex"));
    assert_eq!(out["assets/a.txt"].1, CompressionMethod::Stored);
    assert_eq!(out["classes.dex"].1, CompressionMethod::Deflated);
    assert_eq!(out["classes.dex"].0, fs::read(&input).unwrap());
}

#[test]
fn new_content_that_deflate_does_not_shrink_is_stored() {
    let dir = Scratch::new("small");
    let input = dir.path().join("in.apk");
    synthetic_input(&input);
    let tiny = dir.path().join("tiny.txt");
    fs::write(&tiny, b"patched dex").unwrap();
    let repeated = dir.path().join("repeated.txt");
    fs::write(&repeated, "abcdefgh".repeat(512)).unwrap();
    let output = dir.path().join("out.apk");
    package_with(
        &Options {
            input: &input,
            output: &output,
            entries: BTreeMap::from([
                ("classes9.dex".to_string(), tiny),
                ("assets/repeated.txt".to_string(), repeated),
            ]),
            signer: None,
        },
        &Plan::default(),
    )
    .unwrap();

    let out = entries(&output);
    assert_eq!(out["classes9.dex"].1, CompressionMethod::Stored);
    assert_eq!(out["assets/repeated.txt"].1, CompressionMethod::Deflated);
}
