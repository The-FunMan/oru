//! Deterministic mutation test of the input zip path. A synthetic APK is truncated at every
//! offset and has bytes flipped with a fixed seed. Every case must return (never panic, never
//! hang), and a truncated archive must be rejected.

use native_packager::{package, ApkSigner, Options};
use std::collections::BTreeMap;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::mpsc;
use std::thread;
use std::time::Duration;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

/// Longest a single packaging attempt may take before the test calls it a hang.
const BUDGET: Duration = Duration::from_secs(10);
const FLIP_CASES: usize = 200;
const SEED: u64 = 0x9E37_79B9_7F4A_7C15;

struct Scratch(PathBuf);

impl Scratch {
    fn new() -> Self {
        let dir = std::env::temp_dir().join(format!("np-mutation-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        Scratch(dir)
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
        .args(["genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-256", "-out"])
        .arg(&key)
        .status()
        .expect("openssl must be on PATH to generate test keys");
    assert!(status.success());
    let status = Command::new("openssl")
        .args(["req", "-new", "-x509", "-days", "3650", "-subj", "/CN=mutation test", "-key"])
        .arg(&key)
        .arg("-out")
        .arg(&cert)
        .status()
        .expect("openssl req failed to start");
    assert!(status.success());
    ApkSigner::from_pem(
        &fs::read_to_string(key).unwrap(),
        &fs::read_to_string(cert).unwrap(),
    )
    .unwrap()
}

fn synthetic_apk(path: &Path) -> Vec<u8> {
    let entries: [(&str, CompressionMethod, Vec<u8>); 3] = [
        ("AndroidManifest.xml", CompressionMethod::Deflated, b"<manifest/>".repeat(40)),
        ("resources.arsc", CompressionMethod::Stored, (0..120u8).collect()),
        ("assets/a.txt", CompressionMethod::Deflated, b"original asset".repeat(8)),
    ];
    let mut zip = ZipWriter::new(fs::File::create(path).unwrap());
    for (name, method, data) in &entries {
        zip.start_file(*name, SimpleFileOptions::default().compression_method(*method))
            .unwrap();
        zip.write_all(data).unwrap();
    }
    zip.finish().unwrap();
    fs::read(path).unwrap()
}

/// xorshift64, so the flipped offsets and values are the same on every run.
fn next(state: &mut u64) -> u64 {
    *state ^= *state << 13;
    *state ^= *state >> 7;
    *state ^= *state << 17;
    *state
}

enum Mutation {
    Truncate(usize),
    Flip { offset: usize, mask: u8 },
}

fn mutations(len: usize) -> Vec<Mutation> {
    let mut out: Vec<Mutation> = (0..len).map(Mutation::Truncate).collect();
    let mut state = SEED;
    for _ in 0..FLIP_CASES {
        let offset = next(&mut state) as usize % len;
        let mask = (next(&mut state) % 255) as u8 + 1;
        out.push(Mutation::Flip { offset, mask });
    }
    out
}

fn apply(base: &[u8], mutation: &Mutation) -> Vec<u8> {
    match *mutation {
        Mutation::Truncate(len) => base[..len].to_vec(),
        Mutation::Flip { offset, mask } => {
            let mut bytes = base.to_vec();
            bytes[offset] ^= mask;
            bytes
        }
    }
}

#[test]
fn mutated_input_is_rejected_or_packaged_without_panic_or_hang() {
    let dir = Scratch::new();
    let base_path = dir.0.join("base.apk");
    let base = synthetic_apk(&base_path);
    let signer = signer(&dir.0);
    let cases = mutations(base.len());
    let total = cases.len();
    let base_len = base.len();

    let (tx, rx) = mpsc::channel();
    let work_dir = dir.0.clone();
    thread::spawn(move || {
        let input = work_dir.join("mutated.apk");
        let output = work_dir.join("mutated-out.apk");
        for (index, mutation) in cases.iter().enumerate() {
            fs::write(&input, apply(&base, mutation)).unwrap();
            let _ = fs::remove_file(&output);
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                package(&Options {
                    input: &input,
                    output: &output,
                    entries: BTreeMap::new(),
                    signer: Some(&signer),
                })
                .is_ok()
            }));
            let outcome = match result {
                Ok(packaged) => Some(packaged),
                Err(_) => None,
            };
            if tx.send((index, outcome)).is_err() {
                return;
            }
        }
    });

    for expected in 0..total {
        let (index, outcome) = rx
            .recv_timeout(BUDGET)
            .unwrap_or_else(|_| panic!("case {expected} of {total} did not finish within {BUDGET:?}"));
        assert_eq!(index, expected);
        let packaged = outcome.unwrap_or_else(|| panic!("case {index} panicked"));
        if index < base_len {
            assert!(!packaged, "truncation at {index} of {base_len} was packaged");
        }
    }
}
