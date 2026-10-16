//! Timing of packaging with new entries, ignored by default because it writes
//! tens of megabytes and runs for seconds. Run with:
//!
//! ```text
//! KZ_BENCH_DIR=<scratch dir> cargo test --release --test bench -- --ignored --nocapture
//! ```
//!
//! The input is synthetic: eight deflated `classes*.dex` entries that are all
//! replaced, plus one added asset, roughly 55 MB of new content in total.

use native_packager::{package, ApkSigner, Options};
use std::collections::BTreeMap;
use std::env;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Instant;
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipWriter};

const DEX_COUNT: usize = 8;
const DEX_LEN: usize = 6_600_000;
const ADDED_LEN: usize = 4_000_000;
const WARMUP: usize = 2;
const RUNS: usize = 9;

/// Deterministic data about half of which is repetitive, similar to dex code.
fn synthetic(seed: u64, len: usize) -> Vec<u8> {
    let mut x = seed | 1;
    let mut out = Vec::with_capacity(len + 8);
    while out.len() < len {
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        if x >> 63 == 0 {
            out.extend_from_slice(&(x % 64).to_le_bytes());
        } else {
            out.extend_from_slice(&x.to_le_bytes());
        }
    }
    out.truncate(len);
    out
}

fn signer(dir: &Path) -> ApkSigner {
    let key = dir.join("key.pem");
    let cert = dir.join("cert.pem");
    let ok = Command::new("openssl")
        .args([
            "genpkey",
            "-algorithm",
            "RSA",
            "-pkeyopt",
            "rsa_keygen_bits:2048",
            "-out",
        ])
        .arg(&key)
        .status()
        .expect("openssl on PATH")
        .success();
    assert!(ok);
    let ok = Command::new("openssl")
        .args([
            "req",
            "-new",
            "-x509",
            "-days",
            "30",
            "-subj",
            "/CN=bench",
            "-key",
        ])
        .arg(&key)
        .arg("-out")
        .arg(&cert)
        .status()
        .expect("openssl on PATH")
        .success();
    assert!(ok);
    ApkSigner::from_pem(
        &fs::read_to_string(key).unwrap(),
        &fs::read_to_string(cert).unwrap(),
    )
    .unwrap()
}

#[test]
#[ignore]
fn package_new_entries_timing() {
    let dir = PathBuf::from(env::var_os("KZ_BENCH_DIR").expect("set KZ_BENCH_DIR"));
    fs::create_dir_all(&dir).unwrap();
    let input = dir.join("bench-in.zip");
    let output = dir.join("bench-out.apk");

    let mut zip = ZipWriter::new(fs::File::create(&input).unwrap());
    let mut entries = BTreeMap::new();
    for i in 0..DEX_COUNT {
        let name = format!("classes{}.dex", i + 1);
        zip.start_file(
            name.as_str(),
            SimpleFileOptions::default().compression_method(CompressionMethod::Deflated),
        )
        .unwrap();
        zip.write_all(&synthetic(i as u64 + 1, DEX_LEN)).unwrap();
        let path = dir.join(format!("new-{name}"));
        fs::write(&path, synthetic(100 + i as u64, DEX_LEN)).unwrap();
        entries.insert(name, path);
    }
    zip.start_file("AndroidManifest.xml", SimpleFileOptions::default())
        .unwrap();
    zip.write_all(&b"<manifest/>".repeat(1000)).unwrap();
    zip.finish().unwrap();

    let added = dir.join("new-assets-added.bin");
    fs::write(&added, synthetic(999, ADDED_LEN)).unwrap();
    entries.insert("assets/added.bin".to_string(), added);

    let signer = signer(&dir);
    let mut times = Vec::new();
    for run in 0..WARMUP + RUNS {
        let start = Instant::now();
        package(&Options {
            input: &input,
            output: &output,
            entries: entries.clone(),
            signer: Some(&signer),
        })
        .unwrap();
        let seconds = start.elapsed().as_secs_f64();
        if run >= WARMUP {
            times.push(seconds);
        }
    }
    times.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let median = times[times.len() / 2];
    println!(
        "package with {} new entries: median {median:.3} s, min {:.3} s, max {:.3} s, {} runs",
        entries.len(),
        times[0],
        times[times.len() - 1],
        RUNS
    );
    let _ = fs::remove_file(&output);
}
