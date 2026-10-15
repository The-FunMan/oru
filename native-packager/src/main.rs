use anyhow::{bail, Context, Result};
use native_packager::{package, ApkSigner, Options};
use std::collections::BTreeMap;
use std::fs;
use std::path::PathBuf;

const USAGE: &str = "usage: native-packager --input <apk> --output <apk> --key <pkcs8.pem> \
--cert <cert.pem> [--entry NAME=FILE]...";

fn main() -> Result<()> {
    let mut input = None;
    let mut output = None;
    let mut key = None;
    let mut cert = None;
    let mut entries = BTreeMap::new();

    let mut args = std::env::args().skip(1);
    while let Some(flag) = args.next() {
        let value = args
            .next()
            .with_context(|| format!("{flag} needs a value\n{USAGE}"))?;
        match flag.as_str() {
            "--input" => input = Some(PathBuf::from(value)),
            "--output" => output = Some(PathBuf::from(value)),
            "--key" => key = Some(PathBuf::from(value)),
            "--cert" => cert = Some(PathBuf::from(value)),
            "--entry" => {
                let (name, path) = value
                    .split_once('=')
                    .with_context(|| format!("--entry wants NAME=FILE, got {value}"))?;
                entries.insert(name.to_owned(), PathBuf::from(path));
            }
            _ => bail!("unknown argument {flag}\n{USAGE}"),
        }
    }

    let (Some(input), Some(output), Some(key), Some(cert)) = (input, output, key, cert) else {
        bail!("{USAGE}");
    };
    let signer = ApkSigner::from_pem(
        &fs::read_to_string(&key).with_context(|| format!("read {}", key.display()))?,
        &fs::read_to_string(&cert).with_context(|| format!("read {}", cert.display()))?,
    )?;

    let report = package(&Options {
        input: &input,
        output: &output,
        entries,
        signer: Some(&signer),
    })?;
    eprintln!(
        "copied {} rewritten {} replaced {} added {} output {} bytes",
        report.copied, report.rewritten, report.replaced, report.added, report.output_bytes
    );
    Ok(())
}
