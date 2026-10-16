//! APK Signature Scheme v2: chunked content digests, the signed data, and the
//! APK Signing Block that is inserted between the entries and the central
//! directory.

use anyhow::{anyhow, ensure, Context, Result};
use p256::ecdsa::DerSignature;
use rsa::pkcs1v15::SigningKey as RsaSigningKey;
use rsa::pkcs8::{DecodePrivateKey, EncodePublicKey};
use rsa::signature::{SignatureEncoding, Signer};
use rsa::RsaPrivateKey;
use sha2::{Digest, Sha256};
use std::fs::OpenOptions;
use std::io::{Seek, SeekFrom, Write};
use std::ops::Deref;
use std::panic::{self, AssertUnwindSafe};
use std::path::Path;
use std::ptr;
use std::sync::atomic::{compiler_fence, Ordering};
use std::thread;

use crate::ProgressThrottle;

const CHUNK_SIZE: usize = 1 << 20;
const CHUNK_PREFIX: u8 = 0xa5;
const TOP_PREFIX: u8 = 0x5a;
const SIG_ALG_RSA_PKCS1_SHA256: u32 = 0x0103;
const SIG_ALG_ECDSA_SHA256: u32 = 0x0201;
const V2_BLOCK_ID: u32 = 0x7109_871a;
const SIG_BLOCK_MAGIC: &[u8; 16] = b"APK Sig Block 42";
const EOCD_SIG: u32 = 0x0605_4b50;
const EOCD_MIN_LEN: usize = 22;
const MAX_DIGEST_WORKERS: usize = 8;
/// Sections smaller than this many chunks are hashed on the calling thread.
const MIN_CHUNKS_PER_WORKER: usize = 4;

pub type Digest32 = [u8; 32];

fn chunk_digest(chunk: &[u8]) -> Digest32 {
    let mut hasher = Sha256::new();
    hasher.update([CHUNK_PREFIX]);
    hasher.update((chunk.len() as u32).to_le_bytes());
    hasher.update(chunk);
    hasher.finalize().into()
}

/// Number of [`CHUNK_SIZE`] chunks that cover `len` bytes.
fn chunk_count(len: usize) -> usize {
    len.div_ceil(CHUNK_SIZE)
}

/// Chunked digest of one section (entries, central directory or EOCD). Each
/// 1 MiB chunk is hashed as `0xa5 || len (u32 LE) || bytes`. Chunks are fixed by
/// file offset and their results are stored by index, so the output does not
/// depend on how the work is split across threads.
#[cfg(test)]
fn digest_section(data: &[u8]) -> Result<Vec<Digest32>> {
    digest_section_with_progress(data, |_| ())
}

/// [`digest_section`] that calls `on_chunks_done` with the number of chunks finished each time a
/// batch completes. A failed worker makes the result an error.
fn digest_section_with_progress<F: FnMut(usize)>(
    data: &[u8],
    on_chunks_done: F,
) -> Result<Vec<Digest32>> {
    let workers = thread::available_parallelism()
        .map_or(1, |n| n.get())
        .min(MAX_DIGEST_WORKERS)
        .min(chunk_count(data.len()) / MIN_CHUNKS_PER_WORKER)
        .max(1);
    digest_with(data, workers, on_chunks_done, chunk_digest)
}

/// [`digest_section_with_progress`] with the worker count and the hash function injected, so the
/// failure paths can be tested. With more than one worker, each window of
/// `workers * MIN_CHUNKS_PER_WORKER` chunks is hashed on scoped threads. A panicking `hash`
/// makes the whole call an error, and no digests are returned.
fn digest_with<H>(
    data: &[u8],
    workers: usize,
    mut on_chunks_done: impl FnMut(usize),
    hash: H,
) -> Result<Vec<Digest32>>
where
    H: Fn(&[u8]) -> Digest32 + Sync,
{
    let chunks: Vec<&[u8]> = data.chunks(CHUNK_SIZE).collect();
    if workers <= 1 {
        let mut digests = Vec::with_capacity(chunks.len());
        let hashed = panic::catch_unwind(AssertUnwindSafe(|| {
            for &chunk in &chunks {
                digests.push(hash(chunk));
                on_chunks_done(1);
            }
        }));
        hashed.map_err(|_| anyhow!("digest worker panicked"))?;
        return Ok(digests);
    }

    let window_size = workers * MIN_CHUNKS_PER_WORKER;
    let mut digests = Vec::with_capacity(chunks.len());
    for window in chunks.chunks(window_size) {
        let window_workers = workers.min(window.len());
        let hash = &hash;
        let parts: Vec<Vec<(usize, Digest32)>> = thread::scope(|scope| {
            let handles: Vec<_> = (0..window_workers)
                .map(|worker| {
                    scope.spawn(move || -> Vec<(usize, Digest32)> {
                        (worker..window.len())
                            .step_by(window_workers)
                            .map(|index| (index, hash(window[index])))
                            .collect()
                    })
                })
                .collect();
            // Every handle is joined before returning, so the scope never sees an unjoined panic.
            let joined: Vec<_> = handles
                .into_iter()
                .map(|handle| handle.join().map_err(|_| anyhow!("digest worker panicked")))
                .collect();
            joined.into_iter().collect::<Result<Vec<_>>>()
        })?;
        let mut window_digests = vec![[0u8; 32]; window.len()];
        for (index, digest) in parts.into_iter().flatten() {
            window_digests[index] = digest;
        }
        digests.extend(window_digests);
        on_chunks_done(window.len());
    }
    Ok(digests)
}

pub fn top_digest(chunks: &[Digest32]) -> Digest32 {
    let mut hasher = Sha256::new();
    hasher.update([TOP_PREFIX]);
    hasher.update((chunks.len() as u32).to_le_bytes());
    for chunk in chunks {
        hasher.update(chunk);
    }
    hasher.finalize().into()
}

/// Overwrites a borrowed secret buffer with zeros when the guard is dropped. The writes are
/// volatile and followed by a compiler fence, so the optimizer cannot drop them as dead stores.
pub struct ZeroOnDrop<'a>(&'a mut [u8]);

impl<'a> ZeroOnDrop<'a> {
    pub fn new(bytes: &'a mut [u8]) -> Self {
        Self(bytes)
    }
}

impl Deref for ZeroOnDrop<'_> {
    type Target = [u8];

    fn deref(&self) -> &[u8] {
        &self.0[..]
    }
}

impl Drop for ZeroOnDrop<'_> {
    fn drop(&mut self) {
        for byte in self.0.iter_mut() {
            // SAFETY: `byte` is a valid, aligned, exclusive reference to an initialized u8.
            unsafe { ptr::write_volatile(byte, 0) };
        }
        compiler_fence(Ordering::SeqCst);
    }
}

enum Key {
    Rsa(Box<RsaSigningKey<Sha256>>),
    EcdsaP256(p256::ecdsa::SigningKey),
}

/// A PKCS#8 private key (RSA or P-256) with its certificate.
pub struct ApkSigner {
    key: Key,
    certificate: Vec<u8>,
    public_key: Vec<u8>,
}

impl ApkSigner {
    /// Loads a PEM key and a PEM certificate.
    pub fn from_pem(key_pem: &str, cert_pem: &str) -> Result<Self> {
        let certificate = pem::parse_many(cert_pem)
            .context("parse certificate PEM")?
            .into_iter()
            .find(|block| block.tag() == "CERTIFICATE")
            .ok_or_else(|| anyhow!("no CERTIFICATE block in certificate PEM"))?
            .into_contents();

        let mut key_bytes = pem::parse(key_pem)
            .context("parse key PEM")?
            .into_contents();
        let key_der = ZeroOnDrop::new(&mut key_bytes);
        Self::from_der(&key_der, &certificate)
    }

    /// Loads a PKCS#8 DER key and a DER certificate, as Java hands them out. The caller keeps
    /// ownership of `key_der` and is responsible for wiping it.
    pub fn from_der(key_der: &[u8], certificate: &[u8]) -> Result<Self> {
        if let Ok(rsa) = RsaPrivateKey::from_pkcs8_der(key_der) {
            return Self::from_rsa(rsa, certificate.to_vec());
        }
        let ec = p256::ecdsa::SigningKey::from_pkcs8_der(key_der)
            .context("key is neither an RSA nor a P-256 PKCS#8 private key")?;
        Self::from_ec(ec, certificate.to_vec())
    }

    fn from_rsa(rsa: RsaPrivateKey, certificate: Vec<u8>) -> Result<Self> {
        let public_key = rsa.to_public_key().to_public_key_der()?.as_bytes().to_vec();
        Ok(Self {
            key: Key::Rsa(Box::new(RsaSigningKey::<Sha256>::new(rsa))),
            certificate,
            public_key,
        })
    }

    fn from_ec(ec: p256::ecdsa::SigningKey, certificate: Vec<u8>) -> Result<Self> {
        let public_key = ec.verifying_key().to_public_key_der()?.as_bytes().to_vec();
        Ok(Self {
            key: Key::EcdsaP256(ec),
            certificate,
            public_key,
        })
    }
    fn algorithm(&self) -> u32 {
        match self.key {
            Key::Rsa(_) => SIG_ALG_RSA_PKCS1_SHA256,
            Key::EcdsaP256(_) => SIG_ALG_ECDSA_SHA256,
        }
    }

    fn sign(&self, data: &[u8]) -> Vec<u8> {
        match &self.key {
            Key::Rsa(key) => key.sign(data).to_bytes().into_vec(),
            Key::EcdsaP256(key) => {
                let signature: DerSignature = key.sign(data);
                signature.to_bytes().into_vec()
            }
        }
    }

    /// The complete APK Signing Block for an APK whose v2 top-level digest is `top`.
    pub fn v2_block(&self, top: &Digest32) -> Vec<u8> {
        let mut digest = Vec::with_capacity(4 + 4 + top.len());
        digest.extend_from_slice(&self.algorithm().to_le_bytes());
        push_lp(&mut digest, top);

        let mut signed_data = Vec::with_capacity(8 + digest.len() + 8 + self.certificate.len() + 4);
        push_lp_nested(&mut signed_data, &digest);
        push_lp_nested(&mut signed_data, &self.certificate);
        signed_data.extend_from_slice(&0u32.to_le_bytes());

        let raw_signature = self.sign(&signed_data);
        let mut signature = Vec::with_capacity(4 + 4 + raw_signature.len());
        signature.extend_from_slice(&self.algorithm().to_le_bytes());
        push_lp(&mut signature, &raw_signature);

        let mut signer_body =
            Vec::with_capacity(4 + signed_data.len() + 8 + signature.len() + 4 + self.public_key.len());
        push_lp(&mut signer_body, &signed_data);
        push_lp_nested(&mut signer_body, &signature);
        push_lp(&mut signer_body, &self.public_key);
        let mut signer = Vec::with_capacity(4 + signer_body.len());
        push_lp(&mut signer, &signer_body);

        let mut pair = Vec::with_capacity(8 + 4 + 4 + signer.len());
        pair.extend_from_slice(&((4 + 4 + signer.len()) as u64).to_le_bytes());
        pair.extend_from_slice(&V2_BLOCK_ID.to_le_bytes());
        push_lp(&mut pair, &signer);

        let size = (pair.len() + 8 + SIG_BLOCK_MAGIC.len()) as u64;
        let mut block = Vec::with_capacity(8 + pair.len() + 8 + SIG_BLOCK_MAGIC.len());
        block.extend_from_slice(&size.to_le_bytes());
        block.extend_from_slice(&pair);
        block.extend_from_slice(&size.to_le_bytes());
        block.extend_from_slice(SIG_BLOCK_MAGIC);
        block
    }
}

/// Appends `data` to `out` as a u32 LE length followed by the bytes.
fn push_lp(out: &mut Vec<u8>, data: &[u8]) {
    out.extend_from_slice(&(data.len() as u32).to_le_bytes());
    out.extend_from_slice(data);
}

/// Appends `data` to `out` length-prefixed twice, as the v2 signed data nests its fields.
fn push_lp_nested(out: &mut Vec<u8>, data: &[u8]) {
    out.extend_from_slice(&((data.len() + 4) as u32).to_le_bytes());
    push_lp(out, data);
}

pub fn check_length(len: u64, what: &str) -> Result<u32> {
    u32::try_from(len).with_context(|| format!("{what} is {len} bytes, above the 4 GiB ZIP limit"))
}

/// Locates the EOCD record, searching back over the longest possible comment.
pub fn find_eocd(bytes: &[u8]) -> Result<usize> {
    let last = bytes
        .len()
        .checked_sub(EOCD_MIN_LEN)
        .context("input is shorter than an EOCD record")?;
    let first = last.saturating_sub(u16::MAX as usize);
    (first..=last)
        .rev()
        .find(|&i| bytes[i..i + 4] == EOCD_SIG.to_le_bytes())
        .context("no end of central directory record")
}

fn read_u32(bytes: &[u8], at: usize) -> u32 {
    u32::from_le_bytes([bytes[at], bytes[at + 1], bytes[at + 2], bytes[at + 3]])
}

/// Copies an EOCD record with its central directory offset replaced.
fn eocd_with_offset(eocd: &[u8], central_offset: u64) -> Result<Vec<u8>> {
    let mut out = eocd.to_vec();
    out[16..20].copy_from_slice(&check_length(central_offset, "central directory offset")?.to_le_bytes());
    Ok(out)
}

/// Signs an unsigned APK in place. The APK Signing Block goes where the central
/// directory starts, and the central directory and EOCD follow it. Chunk digests
/// are computed in parallel over a read-only mapping of the contents.
pub fn sign_in_place(path: &Path, signer: &ApkSigner) -> Result<u64> {
    sign_in_place_with_progress(path, signer, |_| {})
}

/// Like [`sign_in_place`], but reports progress fraction [0, 1] during hashing and signing. Reports
/// advance by at least 0.01 at a time, the first is always reported, and the last is always 1.0.
pub fn sign_in_place_with_progress<F>(
    path: &Path,
    signer: &ApkSigner,
    mut progress: F,
) -> Result<u64>
where
    F: FnMut(f32),
{
    let mut file = OpenOptions::new()
        .read(true)
        .write(true)
        .open(path)
        .with_context(|| format!("open {} for signing", path.display()))?;
    let mut throttle = ProgressThrottle::new();
    let (central_offset, block, central, end) = {
        // SAFETY: the mapping is only read, and it is dropped before the file is written.
        let map = unsafe { memmap2::Mmap::map(&file) }.context("memory-map APK for signing")?;
        let eocd_at = find_eocd(&map)?;
        let central_len = read_u32(&map, eocd_at + 12) as usize;
        let central_offset = read_u32(&map, eocd_at + 16) as usize;
        ensure!(
            central_offset.checked_add(central_len) == Some(eocd_at),
            "central directory does not end at the EOCD record (ZIP64 or trailing data)"
        );
        ensure!(
            central_offset < 16 || &map[central_offset - 16..central_offset] != SIG_BLOCK_MAGIC,
            "APK is already signed"
        );

        let central = &map[central_offset..eocd_at];
        let eocd = &map[eocd_at..];
        let signed_eocd = eocd_with_offset(eocd, central_offset as u64)?;

        let entries = &map[..central_offset];
        let total_chunks = chunk_count(entries.len())
            + chunk_count(central.len())
            + chunk_count(signed_eocd.len());

        let mut chunks = Vec::with_capacity(total_chunks);
        let mut done = 0;
        for section in [entries, central, signed_eocd.as_slice()] {
            let section_digests = digest_section_with_progress(section, |count| {
                done += count;
                let fraction = done as f32 / total_chunks as f32;
                if throttle.should_report(fraction) {
                    progress(fraction);
                }
            })?;
            chunks.extend(section_digests);
        }

        let block = signer.v2_block(&top_digest(&chunks));

        let new_offset = central_offset as u64 + block.len() as u64;
        let end = eocd_with_offset(eocd, new_offset)?;
        (central_offset as u64, block, central.to_vec(), end)
    };

    file.seek(SeekFrom::Start(central_offset))?;
    file.write_all(&block)?;
    file.write_all(&central)?;
    file.write_all(&end)?;
    file.flush()?;
    if throttle.should_report(1.0) {
        progress(1.0);
    }
    Ok(central_offset + (block.len() + central.len() + end.len()) as u64)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_section_has_no_chunks() {
        assert!(digest_section(&[]).unwrap().is_empty());
    }

    #[test]
    fn chunks_split_at_one_mebibyte() {
        assert_eq!(digest_section(&vec![0u8; CHUNK_SIZE]).unwrap().len(), 1);
        assert_eq!(digest_section(&vec![0u8; CHUNK_SIZE + 1]).unwrap().len(), 2);
        assert_eq!(digest_section(&vec![0u8; 3 * CHUNK_SIZE + 5]).unwrap().len(), 4);
    }

    #[test]
    fn parallel_digest_matches_one_chunk_at_a_time() {
        let data: Vec<u8> = (0..(20 * CHUNK_SIZE + 999))
            .map(|i| (i % 251) as u8)
            .collect();
        let serial: Vec<Digest32> = data.chunks(CHUNK_SIZE).map(chunk_digest).collect();
        assert_eq!(digest_section(&data).unwrap(), serial);
    }

    #[test]
    fn zero_on_drop_wipes_the_borrowed_buffer() {
        let mut key = vec![0xa5u8; 48];
        {
            let guard = ZeroOnDrop::new(&mut key);
            assert_eq!(guard.len(), 48);
            assert_eq!(guard[0], 0xa5);
        }
        assert!(key.iter().all(|&byte| byte == 0));
    }

    #[test]
    fn rejects_non_key_pem() {
        assert!(ApkSigner::from_pem("not a key", "not a cert").is_err());
    }

    #[test]
    fn rejects_non_key_der() {
        assert!(ApkSigner::from_der(b"not a key", b"not a cert").is_err());
    }

    /// A section of `chunks` full chunks, where chunk `i` starts with byte `i`.
    fn tagged_section(chunks: usize) -> Vec<u8> {
        let mut data = vec![0u8; chunks * CHUNK_SIZE];
        for index in 0..chunks {
            data[index * CHUNK_SIZE] = index as u8;
        }
        data
    }

    fn hash_failing_on_chunk_three(chunk: &[u8]) -> Digest32 {
        if chunk[0] == 3 {
            panic!("injected digest failure");
        }
        chunk_digest(chunk)
    }

    #[test]
    fn failing_worker_yields_err() {
        let data = tagged_section(8);
        let error = digest_with(&data, 2, |_| (), hash_failing_on_chunk_three)
            .expect_err("a panicking worker must not yield digests");
        assert_eq!(error.to_string(), "digest worker panicked");
    }

    #[test]
    fn failing_serial_digest_yields_err() {
        let data = tagged_section(8);
        let error = digest_with(&data, 1, |_| (), hash_failing_on_chunk_three)
            .expect_err("a panicking serial digest must not yield digests");
        assert_eq!(error.to_string(), "digest worker panicked");
    }
}
