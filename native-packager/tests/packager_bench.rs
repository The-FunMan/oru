//! Packaging time on a real APK: three entries replaced and one asset added,
//! the rest copied through. Ignored by default because it needs an input APK
//! outside the repository and runs for seconds. Run with:
//!
//! ```text
//! KZ_BENCH_APK=<stock.apk> KZ_BENCH_DIR=<scratch dir> cargo test --release --test packager_bench -- --ignored --nocapture
//! ```
//!
//! Prints one line per timed run, then the median and the spread. The last
//! output is left in KZ_BENCH_DIR so its hash can be compared across builds.

use native_packager::{package, ApkSigner, Options};
use std::collections::BTreeMap;
use std::env;
use std::fs::{self, File};
use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Instant;
use zip::ZipArchive;

const WARMUP: usize = 2;
const RUNS: usize = 7;
const REPLACED: [&str; 2] = ["classes.dex", "resources.arsc"];
const ADDED_NAME: &str = "assets/kz-bench.bin";
const ADDED_LEN: usize = 4_000_000;

fn bench_dir() -> PathBuf {
    let dir = PathBuf::from(env::var_os("KZ_BENCH_DIR").expect("set KZ_BENCH_DIR"));
    fs::create_dir_all(&dir).unwrap();
    dir
}

fn signer(dir: &Path) -> ApkSigner {
    let key = dir.join("key.pem");
    let cert = dir.join("cert.pem");
    if !cert.exists() {
        let ok = Command::new("openssl")
            .args(["genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out"])
            .arg(&key)
            .status()
            .expect("openssl on PATH")
            .success();
        assert!(ok, "openssl genpkey failed");
        let ok = Command::new("openssl")
            .args(["req", "-new", "-x509", "-key"])
            .arg(&key)
            .args(["-days", "2", "-subj", "/CN=bench", "-out"])
            .arg(&cert)
            .status()
            .expect("openssl on PATH")
            .success();
        assert!(ok, "openssl req failed");
    }
    ApkSigner::from_pem(
        &fs::read_to_string(&key).unwrap(),
        &fs::read_to_string(&cert).unwrap(),
    )
    .unwrap()
}

/// Writes the replaced entries of the input to files in `dir`, so the benchmark
/// replaces them with their own content.
fn extract_inputs(apk: &Path, dir: &Path) -> BTreeMap<String, PathBuf> {
    let mut archive = ZipArchive::new(File::open(apk).unwrap()).unwrap();
    let mut entries = BTreeMap::new();
    for name in REPLACED {
        let mut entry = archive.by_name(name).unwrap();
        let path = dir.join(name.replace('/', "_"));
        io::copy(&mut entry, &mut File::create(&path).unwrap()).unwrap();
        entries.insert(name.to_owned(), path);
    }
    let added = dir.join("added.bin");
    let mut x: u64 = 0x9e37_79b9_7f4a_7c15;
    let data: Vec<u8> = (0..ADDED_LEN)
        .map(|_| {
            x ^= x << 13;
            x ^= x >> 7;
            x ^= x << 17;
            (x >> 56) as u8
        })
        .collect();
    File::create(&added).unwrap().write_all(&data).unwrap();
    entries.insert(ADDED_NAME.to_owned(), added);
    entries
}

#[test]
#[ignore]
fn packaging_time_on_stock_apk() {
    let apk = PathBuf::from(env::var_os("KZ_BENCH_APK").expect("set KZ_BENCH_APK"));
    let dir = bench_dir();
    let signer = signer(&dir);
    let entries = extract_inputs(&apk, &dir);
    let output = dir.join("bench-out.apk");

    let mut seconds = Vec::new();
    for run in 0..(WARMUP + RUNS) {
        let _ = fs::remove_file(&output);
        let start = Instant::now();
        let report = package(&Options {
            input: &apk,
            output: &output,
            entries: entries.clone(),
            signer: Some(&signer),
        })
        .unwrap();
        let elapsed = start.elapsed().as_secs_f64();
        if run >= WARMUP {
            println!("run {}: {elapsed:.4} s", run - WARMUP + 1);
            seconds.push(elapsed);
        }
        assert_eq!(report.replaced, REPLACED.len());
        assert_eq!(report.added, 1);
    }
    seconds.sort_by(f64::total_cmp);
    println!(
        "median {:.4} s, min {:.4} s, max {:.4} s over {RUNS} runs",
        seconds[RUNS / 2],
        seconds[0],
        seconds[RUNS - 1]
    );
}
