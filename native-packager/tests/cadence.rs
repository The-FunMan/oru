//! Tests for the progress callback cadence in native-packager.

use native_packager::{
    package_with_progress, sign_in_place_with_progress, ApkSigner, Options, Plan,
};
use std::collections::BTreeMap;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

struct Scratch(PathBuf);

impl Scratch {
    fn new(tag: &str) -> Self {
        let dir = std::env::temp_dir().join(format!("np-cadence-{}-{tag}", std::process::id()));
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

/// The openssl binary: `openssl` from PATH, or the program named by the `OPENSSL` environment variable.
fn openssl_cmd() -> Command {
    Command::new(std::env::var_os("OPENSSL").unwrap_or_else(|| "openssl".into()))
}

fn test_signer(dir: &Path) -> ApkSigner {
    let key = dir.join("key.pem");
    let cert = dir.join("cert.pem");
    let status = openssl_cmd()
        .args(["genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out"])
        .arg(&key)
        .status()
        .expect("openssl must be on PATH or named by the OPENSSL environment variable");
    assert!(status.success());
    let status = openssl_cmd()
        .args(["req", "-new", "-x509", "-days", "3650", "-subj", "/CN=cadence test", "-key"])
        .arg(&key)
        .arg("-out")
        .arg(&cert)
        .status()
        .expect("openssl req failed to start");
    assert!(status.success());
    ApkSigner::from_pem(&fs::read_to_string(key).unwrap(), &fs::read_to_string(cert).unwrap())
        .unwrap()
}

#[test]
fn pack_progress_cadence_is_monotonic_and_ends_at_one() {
    let scratch = Scratch::new("pack-cadence");
    let input = scratch.path().join("input.zip");
    let output = scratch.path().join("output.apk");

    {
        let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
        let deflated = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
        for i in 0..100 {
            zip.start_file(format!("entry_{i}.bin"), deflated).unwrap();
            zip.write_all(format!("content of entry {i}").as_bytes()).unwrap();
        }
        zip.finish().unwrap();
    }

    let mut entries = BTreeMap::new();
    for i in 0..20 {
        let entry_path = scratch.path().join(format!("new_{i}.dex"));
        fs::write(&entry_path, format!("new dex content {i}").repeat(50)).unwrap();
        entries.insert(format!("classes{i}.dex"), entry_path);
    }

    let mut fractions = Vec::new();
    let report = package_with_progress(
        &Options {
            input: &input,
            output: &output,
            entries,
            signer: None,
        },
        &Plan::default(),
        |fraction| fractions.push(fraction),
    )
    .unwrap();

    assert!(report.output_bytes > 0);
    assert!(
        fractions.len() >= 2,
        "expected multiple cadence updates, got: {fractions:?}"
    );

    for window in fractions.windows(2) {
        assert!(
            window[0] <= window[1],
            "fraction decreased from {} to {}",
            window[0],
            window[1]
        );
    }

    for &f in &fractions {
        assert!(f > 0.0 && f <= 1.0, "fraction out of bounds: {f}");
    }

    assert_eq!(*fractions.last().unwrap(), 1.0);
}

#[test]
fn sign_progress_cadence_is_monotonic_and_ends_at_one() {
    let scratch = Scratch::new("sign-cadence");
    let input = scratch.path().join("unsigned.apk");

    {
        let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
        let deflated = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
        zip.start_file("AndroidManifest.xml", deflated).unwrap();
        zip.write_all(b"<manifest/>").unwrap();
        // Create an entry that spans multiple 1 MiB chunks
        let large_payload = vec![0x42u8; 4 * 1024 * 1024 + 128];
        let stored = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
        zip.start_file("assets/large.bin", stored).unwrap();
        zip.write_all(&large_payload).unwrap();
        zip.finish().unwrap();
    }

    let signer = test_signer(scratch.path());
    let mut fractions = Vec::new();
    let signed_bytes = sign_in_place_with_progress(&input, &signer, |fraction| {
        fractions.push(fraction);
    })
    .unwrap();

    assert!(signed_bytes > 0);
    assert!(
        fractions.len() >= 3,
        "expected multiple chunk digest reports, got: {fractions:?}"
    );

    for window in fractions.windows(2) {
        assert!(
            window[0] <= window[1],
            "fraction decreased from {} to {}",
            window[0],
            window[1]
        );
    }

    for &f in &fractions {
        assert!(f > 0.0 && f <= 1.0, "fraction out of bounds: {f}");
    }

    assert_eq!(*fractions.last().unwrap(), 1.0);
}

#[test]
fn pack_progress_throttles_callback_frequency() {
    let scratch = Scratch::new("pack-throttle");
    let input = scratch.path().join("input.zip");
    let output = scratch.path().join("output.apk");

    // About 10,000 copy steps. The writer runs 64 steps per window, so without a throttle the
    // callback would fire about 157 times. With a 0.01 step it fires at most about 101 times.
    const ENTRIES: usize = 10_000;
    {
        let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
        let deflated = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
        for i in 0..ENTRIES {
            zip.start_file(format!("entry_{i}.bin"), deflated).unwrap();
            zip.write_all(format!("content of entry {i}").as_bytes())
                .unwrap();
        }
        zip.finish().unwrap();
    }

    let mut fractions = Vec::new();
    let report = package_with_progress(
        &Options {
            input: &input,
            output: &output,
            entries: BTreeMap::new(),
            signer: None,
        },
        &Plan::default(),
        |fraction| fractions.push(fraction),
    )
    .unwrap();

    assert_eq!(report.copied, ENTRIES);
    assert!(
        fractions.len() <= 102,
        "expected throttled callbacks (<= 102), got {}",
        fractions.len()
    );
    for window in fractions.windows(2) {
        assert!(
            window[0] <= window[1],
            "fraction decreased from {} to {}",
            window[0],
            window[1]
        );
    }
    assert_eq!(*fractions.last().unwrap(), 1.0);
    assert_eq!(fractions.iter().filter(|&&f| f == 1.0).count(), 1);
}
