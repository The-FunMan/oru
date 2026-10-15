//! Entry-level parity against a real APK. Ignored by default because it needs an
//! input outside the repository. Run with:
//!
//! ```text
//! NATIVE_PACKAGER_INPUT_APK=<path> NATIVE_PACKAGER_KEY=<pkcs8.pem> \
//! NATIVE_PACKAGER_CERT=<cert.pem> cargo test --release --test parity -- --ignored
//! ```

use native_packager::{package, ApkSigner, Options};
use std::collections::BTreeMap;
use std::env;
use std::fs;
use std::io::Read;
use std::path::Path;
use zip::{CompressionMethod, ZipArchive};

fn env_path(name: &str) -> std::path::PathBuf {
    env::var_os(name)
        .map(Into::into)
        .unwrap_or_else(|| panic!("set {name} to run the parity test"))
}

fn contents(path: &Path) -> BTreeMap<String, (Vec<u8>, CompressionMethod)> {
    let mut archive = ZipArchive::new(fs::File::open(path).unwrap()).unwrap();
    let mut out = BTreeMap::new();
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).unwrap();
        let method = entry.compression();
        let mut data = Vec::with_capacity(entry.size() as usize);
        entry.read_to_end(&mut data).unwrap();
        out.insert(entry.name().to_string(), (data, method));
    }
    out
}

#[test]
#[ignore]
fn stock_apk_entries_match_after_packaging() {
    let input = env_path("NATIVE_PACKAGER_INPUT_APK");
    let signer = ApkSigner::from_pem(
        &fs::read_to_string(env_path("NATIVE_PACKAGER_KEY")).unwrap(),
        &fs::read_to_string(env_path("NATIVE_PACKAGER_CERT")).unwrap(),
    )
    .unwrap();
    let output = env::temp_dir().join("np-parity-out.apk");

    let report = package(&Options {
        input: &input,
        output: &output,
        entries: BTreeMap::new(),
        signer: Some(&signer),
    })
    .unwrap();
    eprintln!("{report:?}");

    let before = contents(&input);
    let after = contents(&output);
    assert_eq!(
        before.keys().collect::<Vec<_>>(),
        after.keys().collect::<Vec<_>>()
    );
    let mismatches: Vec<&String> = before
        .iter()
        .filter(|(name, (data, _))| after[*name].0 != *data)
        .map(|(name, _)| name)
        .collect();
    assert!(mismatches.is_empty(), "entries differ: {mismatches:?}");
    let _ = fs::remove_file(&output);
}
