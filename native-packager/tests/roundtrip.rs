//! Integration tests on synthetic zips. Signing keys are generated with `openssl`
//! into a temp directory at test time, so the repository holds no key material.

use native_packager::{package, ApkSigner, Options};
use std::collections::BTreeMap;
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

const SIG_MAGIC: &[u8; 16] = b"APK Sig Block 42";

/// A unique scratch directory under the system temp dir, removed on drop.
struct Scratch(PathBuf);

impl Scratch {
    fn new() -> Self {
        static COUNTER: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
        let n = COUNTER.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
        let dir = std::env::temp_dir().join(format!("np-test-{}-{n}", std::process::id()));
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

fn gen_keys(dir: &Path, ec: bool) -> (PathBuf, PathBuf) {
    let key = dir.join("key.pem");
    let cert = dir.join("cert.pem");
    let algorithm: &[&str] = if ec {
        &["-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-256"]
    } else {
        &["-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048"]
    };
    let status = Command::new("openssl")
        .args(["genpkey"])
        .args(algorithm)
        .arg("-out")
        .arg(&key)
        .status()
        .expect("openssl must be on PATH to generate test keys");
    assert!(status.success());
    let status = Command::new("openssl")
        .args([
            "req",
            "-new",
            "-x509",
            "-days",
            "3650",
            "-subj",
            "/CN=native-packager test",
        ])
        .arg("-key")
        .arg(&key)
        .arg("-out")
        .arg(&cert)
        .status()
        .expect("openssl req failed to start");
    assert!(status.success());
    (key, cert)
}

fn signer(dir: &Path, ec: bool) -> ApkSigner {
    let (key, cert) = gen_keys(dir, ec);
    ApkSigner::from_pem(
        &fs::read_to_string(key).unwrap(),
        &fs::read_to_string(cert).unwrap(),
    )
    .unwrap()
}

fn synthetic_input(path: &Path) -> BTreeMap<String, Vec<u8>> {
    let mut contents = BTreeMap::new();
    contents.insert(
        "AndroidManifest.xml".to_string(),
        b"<manifest/>".repeat(300),
    );
    contents.insert(
        "resources.arsc".to_string(),
        (0..5000u32).flat_map(|i| i.to_le_bytes()).collect(),
    );
    contents.insert(
        "lib/arm64-v8a/libdeflated.so".to_string(),
        vec![7u8; 40_000],
    );
    contents.insert(
        "lib/x86/libstored.so".to_string(),
        (0..30_001u32).map(|i| (i % 253) as u8).collect(),
    );
    contents.insert("assets/a.txt".to_string(), b"original".to_vec());
    contents.insert(
        "assets/odd-name-length-entry.bin".to_string(),
        vec![1u8; 4097],
    );
    contents.insert("res/empty.bin".to_string(), Vec::new());

    let file = fs::File::create(path).unwrap();
    let mut zip = ZipWriter::new(file);
    for (name, data) in &contents {
        let method = match name.as_str() {
            "resources.arsc" | "lib/x86/libstored.so" | "assets/odd-name-length-entry.bin" => {
                CompressionMethod::Stored
            }
            _ => CompressionMethod::Deflated,
        };
        zip.start_file(
            name.as_str(),
            SimpleFileOptions::default().compression_method(method),
        )
        .unwrap();
        zip.write_all(data).unwrap();
    }
    zip.finish().unwrap();
    contents
}

fn read_all(path: &Path) -> BTreeMap<String, (Vec<u8>, CompressionMethod)> {
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

fn run(
    input: &Path,
    output: &Path,
    signer: &ApkSigner,
    entries: BTreeMap<String, PathBuf>,
) -> native_packager::Report {
    package(&Options {
        input,
        output,
        entries,
        signer: Some(signer),
    })
    .unwrap()
}

#[test]
fn untouched_entries_keep_their_decompressed_content() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    let original = synthetic_input(&input);
    let signer = signer(dir.path(), false);

    let report = run(&input, &output, &signer, BTreeMap::new());
    assert_eq!(report.copied, original.len());
    assert_eq!(report.rewritten, 0);

    let result = read_all(&output);
    assert_eq!(result.len(), original.len());
    for (name, data) in &original {
        assert_eq!(&result[name].0, data, "content of {name}");
    }
}

#[test]
fn replaced_and_added_entries_take_the_new_content() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    let original = synthetic_input(&input);
    let signer = signer(dir.path(), false);

    let replacement = dir.path().join("replacement.txt");
    fs::write(&replacement, b"replaced content").unwrap();
    let native = dir.path().join("libnew.so");
    fs::write(&native, vec![3u8; 50_000]).unwrap();
    let mut entries = BTreeMap::new();
    entries.insert("assets/a.txt".to_string(), replacement);
    entries.insert("lib/arm64-v8a/libnew.so".to_string(), native);

    let report = run(&input, &output, &signer, entries);
    assert_eq!((report.replaced, report.added), (1, 1));

    let result = read_all(&output);
    assert_eq!(result["assets/a.txt"].0, b"replaced content");
    assert_eq!(result["lib/arm64-v8a/libnew.so"].0, vec![3u8; 50_000]);
    assert_eq!(
        result["lib/arm64-v8a/libnew.so"].1,
        CompressionMethod::Stored
    );
    for (name, data) in &original {
        if name != "assets/a.txt" {
            assert_eq!(&result[name].0, data, "content of {name}");
        }
    }
}

#[test]
fn empty_replacement_and_added_files_are_written_as_empty_entries() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    synthetic_input(&input);
    let signer = signer(dir.path(), false);

    let empty = dir.path().join("empty.bin");
    fs::write(&empty, b"").unwrap();
    let mut entries = BTreeMap::new();
    entries.insert("assets/a.txt".to_string(), empty.clone());
    entries.insert("lib/arm64-v8a/libempty.so".to_string(), empty);

    let report = run(&input, &output, &signer, entries);
    assert_eq!((report.replaced, report.added), (1, 1));

    let result = read_all(&output);
    assert!(result["assets/a.txt"].0.is_empty());
    assert!(result["lib/arm64-v8a/libempty.so"].0.is_empty());
}

#[test]
fn replaced_entries_keep_the_input_stored_policy() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
    zip.start_file(
        "res/drawable/icon.png",
        SimpleFileOptions::default().compression_method(CompressionMethod::Stored),
    )
    .unwrap();
    zip.write_all(&[1u8; 2048]).unwrap();
    zip.start_file("assets/b.txt", SimpleFileOptions::default())
        .unwrap();
    zip.write_all(&[2u8; 2048]).unwrap();
    zip.finish().unwrap();

    let replacement = dir.path().join("icon.png");
    fs::write(&replacement, vec![9u8; 2048]).unwrap();
    let text = dir.path().join("b.txt");
    fs::write(&text, vec![8u8; 2048]).unwrap();
    let mut entries = BTreeMap::new();
    entries.insert("res/drawable/icon.png".to_string(), replacement);
    entries.insert("assets/b.txt".to_string(), text);

    let signer = signer(dir.path(), false);
    run(&input, &output, &signer, entries);
    let result = read_all(&output);
    let (icon, icon_method) = &result["res/drawable/icon.png"];
    assert!(icon == &vec![9u8; 2048], "replaced icon content");
    assert_eq!(*icon_method, CompressionMethod::Stored);
    let (text, text_method) = &result["assets/b.txt"];
    assert!(text == &vec![8u8; 2048], "replaced text content");
    assert_eq!(*text_method, CompressionMethod::Deflated);
}

#[test]
fn new_entries_are_deflated_at_level_3_in_order_and_deterministic() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    let original = synthetic_input(&input);
    let signer = signer(dir.path(), false);

    // Enough new entries to fill several parallel windows, with varied content.
    let mut entries = BTreeMap::new();
    let mut contents = BTreeMap::new();
    for i in 0..12u32 {
        let name = format!("assets/new-{i:02}.bin");
        let data: Vec<u8> = (0..150_000u32)
            .map(|j| ((j / (i + 1)) ^ (j * 31 + i)) as u8)
            .collect();
        let path = dir.path().join(format!("new-{i:02}"));
        fs::write(&path, &data).unwrap();
        entries.insert(name.clone(), path);
        contents.insert(name, data);
    }
    let replacement = dir.path().join("replacement.txt");
    fs::write(&replacement, b"replaced content").unwrap();
    entries.insert("assets/a.txt".to_string(), replacement);

    run(&input, &output, &signer, entries.clone());
    let first = fs::read(&output).unwrap();
    run(&input, &output, &signer, entries);
    assert!(
        fs::read(&output).unwrap() == first,
        "output differs between runs"
    );

    let mut archive = ZipArchive::new(fs::File::open(&output).unwrap()).unwrap();
    let names: Vec<String> = (0..archive.len())
        .map(|i| archive.by_index_raw(i).unwrap().name().to_string())
        .collect();
    let mut expected: Vec<String> = original.keys().cloned().collect();
    expected.extend(contents.keys().cloned());
    let mut sorted_names = names.clone();
    sorted_names.sort();
    expected.sort();
    assert_eq!(sorted_names, expected);
    assert_eq!(
        names[..original.len()],
        original.keys().cloned().collect::<Vec<_>>()[..]
    );

    for (name, data) in &contents {
        let index = names.iter().position(|n| n == name).unwrap();
        let mut raw = Vec::new();
        archive
            .by_index_raw(index)
            .unwrap()
            .read_to_end(&mut raw)
            .unwrap();
        let mut encoder =
            flate2::write::DeflateEncoder::new(Vec::new(), flate2::Compression::new(3));
        encoder.write_all(data).unwrap();
        assert!(
            raw == encoder.finish().unwrap(),
            "payload of {name} is not level 3"
        );
    }
}

#[test]
fn resources_arsc_is_stored_and_four_byte_aligned() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    synthetic_input(&input);
    let signer = signer(dir.path(), false);
    run(&input, &output, &signer, BTreeMap::new());
    assert_stored_alignment(&output);
}

#[test]
fn stored_native_libraries_are_16k_aligned() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    synthetic_input(&input);
    let signer = signer(dir.path(), true);
    let native = dir.path().join("libnew.so");
    fs::write(&native, vec![9u8; 70_000]).unwrap();
    let mut entries = BTreeMap::new();
    entries.insert("lib/arm64-v8a/libnew.so".to_string(), native);
    run(&input, &output, &signer, entries);

    assert_stored_alignment(&output);
    let mut archive = ZipArchive::new(fs::File::open(&output).unwrap()).unwrap();
    let so = archive.by_name("lib/arm64-v8a/libnew.so").unwrap();
    assert_eq!(so.data_start() % 16384, 0);
}

/// Every stored entry starts its data on 4 bytes, and stored `.so` on 16 KiB.
fn assert_stored_alignment(path: &Path) {
    let mut archive = ZipArchive::new(fs::File::open(path).unwrap()).unwrap();
    for i in 0..archive.len() {
        let entry = archive.by_index_raw(i).unwrap();
        if entry.compression() != CompressionMethod::Stored {
            continue;
        }
        let align = if entry.name().starts_with("lib/") && entry.name().ends_with(".so") {
            16384
        } else {
            4
        };
        assert_eq!(
            entry.data_start() % align,
            0,
            "{} data offset",
            entry.name()
        );
    }
}

#[test]
fn output_ends_with_signing_block_before_central_directory() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    synthetic_input(&input);
    let signer = signer(dir.path(), false);
    run(&input, &output, &signer, BTreeMap::new());

    let bytes = fs::read(&output).unwrap();
    let eocd = &bytes[bytes.len() - 22..];
    assert_eq!(&eocd[0..4], &0x0605_4b50u32.to_le_bytes());
    let cd_offset = u32::from_le_bytes(eocd[16..20].try_into().unwrap()) as usize;
    assert_eq!(&bytes[cd_offset - 16..cd_offset], SIG_MAGIC);
    let block_size = u64::from_le_bytes(bytes[cd_offset - 24..cd_offset - 16].try_into().unwrap());
    let block_start = cd_offset - 8 - block_size as usize;
    assert_eq!(
        u64::from_le_bytes(bytes[block_start..block_start + 8].try_into().unwrap()),
        block_size
    );
    assert_eq!(
        &bytes[block_start + 8 + 8..block_start + 8 + 12],
        &0x7109_871au32.to_le_bytes()
    );
}

#[test]
fn ec_key_produces_a_readable_archive() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let output = dir.path().join("out.apk");
    let original = synthetic_input(&input);
    let signer = signer(dir.path(), true);
    run(&input, &output, &signer, BTreeMap::new());
    let result = read_all(&output);
    assert_eq!(result.len(), original.len());
}

#[test]
fn deflated_resources_arsc_is_rewritten_stored() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let arsc: Vec<u8> = (0..4000u32).flat_map(|i| (i * 7).to_le_bytes()).collect();
    let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
    zip.start_file("resources.arsc", SimpleFileOptions::default())
        .unwrap();
    zip.write_all(&arsc).unwrap();
    zip.finish().unwrap();

    let output = dir.path().join("out.apk");
    let signer = signer(dir.path(), false);
    let report = run(&input, &output, &signer, BTreeMap::new());
    assert_eq!(report.rewritten, 1);
    let result = read_all(&output);
    assert_eq!(result["resources.arsc"], (arsc, CompressionMethod::Stored));
    assert_stored_alignment(&output);
}

#[test]
fn rejects_duplicate_names_and_unreadable_keys() {
    let dir = Scratch::new();
    let input = dir.path().join("in.zip");
    let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
    for name in ["dupa.txt", "dupb.txt"] {
        zip.start_file(name, SimpleFileOptions::default()).unwrap();
        zip.write_all(b"x").unwrap();
    }
    zip.finish().unwrap();
    // Renaming the second entry to the first, in both headers, makes a duplicate name.
    let mut bytes = fs::read(&input).unwrap();
    for i in 0..=bytes.len() - 4 {
        if &bytes[i..i + 4] == b"dupb" {
            bytes[i..i + 4].copy_from_slice(b"dupa");
        }
    }
    fs::write(&input, bytes).unwrap();

    let signer = signer(dir.path(), false);
    let err = package(&Options {
        input: &input,
        output: &dir.path().join("out.apk"),
        entries: BTreeMap::new(),
        signer: Some(&signer),
    });
    assert!(err.is_err());
    assert!(ApkSigner::from_pem("garbage", "garbage").is_err());
}
