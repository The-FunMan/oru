//! Native APK writer and packager for Oru.
//!
//! The input zip is memory-mapped and read once, front to back. Untouched entries
//! are copied as their stored (compressed) bytes without recompression. Entries
//! named in [`Options::entries`] replace the input entry of the same name or are
//! appended, and entries named in [`Plan::remove`] are dropped. Stored entries are
//! aligned while writing: 4 bytes in general and 16 KiB for `lib/*/*.so`. The
//! signature is added afterwards by [`signing::sign_in_place`], which hashes the
//! written file in parallel.
//!
//! Limits: no ZIP64 (inputs and outputs must stay under 4 GiB and 65535 entries),
//! no encrypted entries, UTF-8 entry names only, no v1 or v3 signing.

mod signing;

pub use signing::{sign_in_place, sign_in_place_with_progress, ApkSigner, ZeroOnDrop};

use anyhow::{anyhow, bail, ensure, Context, Result};
use flate2::{write::DeflateEncoder, Compression};
use signing::{check_length, find_eocd};
use std::collections::{BTreeMap, BTreeSet};
use std::fs::File;
use std::io::{self, BufWriter, Cursor, Read, Seek, Write};
use std::ops::Deref;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::thread;
use zip::{CompressionMethod, ZipArchive};

const METHOD_STORED: u16 = 0;
const METHOD_DEFLATED: u16 = 8;
const VERSION_NEEDED: u16 = 20;
const VERSION_MADE_BY: u16 = 0x031e;
const UTF8_NAME_FLAG: u16 = 0x0800;
const LOCAL_HEADER_LEN: u64 = 30;
const LOCAL_SIG: u32 = 0x0403_4b50;
const CENTRAL_SIG: u32 = 0x0201_4b50;
const EOCD_SIG: u32 = 0x0605_4b50;
const PADDING_EXTRA_ID: u16 = 0xd935;
const STORED_ALIGN: usize = 4;
const NATIVE_LIB_ALIGN: usize = 16 * 1024;
const UNIX_REGULAR_FILE: u32 = 0o100644;
const DEFLATE_LEVEL: u32 = 3;
const MAX_WORKERS: usize = 8;
/// The smallest advance of the progress fraction that is reported, except the final 1.0.
const MIN_PROGRESS_STEP: f32 = 0.01;

/// Decides which progress fractions reach the callback, for both the write and the sign stage.
/// A fraction is reported once it is at least [`MIN_PROGRESS_STEP`] past the last report (the first
/// report always passes), and the final 1.0 is always reported. The same fraction is never reported
/// twice, so 1.0 is reported once.
struct ProgressThrottle {
    last: f32,
}

impl ProgressThrottle {
    fn new() -> Self {
        Self { last: -1.0 }
    }

    /// Returns whether `fraction` should be reported, and records it as the last report if so.
    fn should_report(&mut self, fraction: f32) -> bool {
        if fraction == self.last {
            return false;
        }
        if fraction < 1.0 && fraction - self.last < MIN_PROGRESS_STEP {
            return false;
        }
        self.last = fraction;
        true
    }
}

pub struct Options<'a> {
    pub input: &'a Path,
    pub output: &'a Path,
    /// Entry name to file on disk. Replaces an input entry or adds a new one.
    pub entries: BTreeMap<String, PathBuf>,
    /// Signs the output with v2 when set. An unsigned output has no signing block.
    pub signer: Option<&'a ApkSigner>,
}

/// What to change beyond the entries of [`Options`]. The default keeps the input
/// entries and stores or deflates new content by name.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Plan {
    /// Input entries to drop. An entry also named in [`Options::entries`] is replaced.
    pub remove: BTreeSet<String>,
    /// Compression for new content, by entry name: `true` stores it. Names not
    /// listed use the default: stored for `resources.arsc` and `lib/*/*.so`.
    pub stored: BTreeMap<String, bool>,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct Report {
    pub copied: usize,
    pub rewritten: usize,
    pub replaced: usize,
    pub added: usize,
    pub removed: usize,
    pub output_bytes: u64,
}

pub fn package(opts: &Options) -> Result<Report> {
    package_with_progress(opts, &Plan::default(), |_| {})
}

pub fn package_with(opts: &Options, plan: &Plan) -> Result<Report> {
    package_with_progress(opts, plan, |_| {})
}

/// Like [`package_with`], but reports the fraction [0, 1] of entries written. Reports advance by at
/// least 0.01 at a time, the first is always reported, and the last is always 1.0.
pub fn package_with_progress<F>(opts: &Options, plan: &Plan, mut progress: F) -> Result<Report>
where
    F: FnMut(f32),
{
    let file = File::open(opts.input).with_context(|| format!("open {}", opts.input.display()))?;
    // SAFETY: the mapping is read only and the input is not modified while it is mapped.
    let map = unsafe { memmap2::Mmap::map(&file) }.context("memory-map input")?;
    let mut archive = ZipArchive::new(Cursor::new(&map[..])).context("read input zip")?;
    // The zip crate keeps only the last of two same-named entries, so compare counts.
    let declared = declared_entry_count(&map)?;
    ensure!(
        archive.len() == declared,
        "central directory declares {declared} entries but {} are readable (duplicate names?)",
        archive.len()
    );

    let mut pending = opts.entries.clone();
    let mut report = Report::default();
    let mut steps = Vec::with_capacity(archive.len() + pending.len());
    for index in 0..archive.len() {
        let meta = Meta::read(&mut archive, index)?;

        if let Some(path) = pending.remove(&meta.name) {
            let default = meta.method == METHOD_STORED || prefers_stored(&meta.name);
            let stored = plan.stored.get(&meta.name).copied().unwrap_or(default);
            steps.push(Step::New {
                name: meta.name.clone(),
                stamp: meta.stamp(),
                path,
                stored,
            });
            report.replaced += 1;
        } else if plan.remove.contains(&meta.name) {
            report.removed += 1;
        } else if meta.name == "resources.arsc" && meta.method != METHOD_STORED {
            steps.push(Step::Rewrite { index, meta });
            report.rewritten += 1;
        } else {
            steps.push(Step::Copy { meta });
            report.copied += 1;
        }
    }
    for (name, path) in pending {
        let stored = plan.stored.get(&name).copied().unwrap_or(prefers_stored(&name));
        steps.push(Step::New {
            name,
            stamp: Stamp::NEW,
            path,
            stored,
        });
        report.added += 1;
    }

    let output =
        File::create(opts.output).with_context(|| format!("create {}", opts.output.display()))?;
    let mut writer = Writer::new(BufWriter::with_capacity(1 << 20, output));
    let total_steps = steps.len();
    let threads = worker_count();
    const MAX_WINDOW_STEPS: usize = 64;
    let mut start = 0;
    let mut throttle = ProgressThrottle::new();
    let mut emit = |fraction: f32| {
        if throttle.should_report(fraction) {
            progress(fraction);
        }
    };

    while start < steps.len() {
        // A window ends after one deflate job per worker, which bounds the
        // compressed and raw data held at once, or after MAX_WINDOW_STEPS steps.
        let mut end = start;
        let mut jobs = 0;
        while end < steps.len() && jobs < threads && (end - start) < MAX_WINDOW_STEPS {
            jobs += usize::from(steps[end].is_deflated());
            end += 1;
        }
        let prepared = deflate_window(&steps[start..end], threads)?;
        for (step, deflated) in steps[start..end].iter().zip(prepared) {
            match step {
                Step::Copy { meta } => {
                    writer.copy_raw(meta, raw_bytes(&map, meta)?)?;
                }
                Step::Rewrite { index, meta } => {
                    let data = read_entry(&mut archive, *index)?;
                    writer.write_new(&meta.name, meta.stamp(), &data, None)?;
                }
                Step::New {
                    name,
                    stamp,
                    path,
                    stored: true,
                } => {
                    let data = map_file(path)?;
                    writer.write_new(name, *stamp, &data, None)?;
                }
                Step::New { name, stamp, .. } => {
                    let Deflated { data, payload } =
                        deflated.context("deflate job missing from its window")?;
                    // Content that deflate does not make smaller is stored, as apkzlib stores it.
                    let payload = (payload.len() < data.len()).then_some(&payload[..]);
                    writer.write_new(name, *stamp, &data, payload)?;
                }
            }
        }
        start = end;
        if total_steps > 0 {
            emit(start as f32 / total_steps as f32);
        }
    }

    report.output_bytes = writer.finish()?;
    emit(1.0);
    if let Some(signer) = opts.signer {
        report.output_bytes = sign_in_place(opts.output, signer)?;
    }
    Ok(report)
}

/// One output entry, in output order.
enum Step {
    /// An input entry copied as its stored bytes.
    Copy { meta: Meta },
    /// A deflated input `resources.arsc`, rewritten stored.
    Rewrite { index: usize, meta: Meta },
    /// Content from a file, written under `name`.
    New {
        name: String,
        stamp: Stamp,
        path: PathBuf,
        stored: bool,
    },
}

impl Step {
    fn is_deflated(&self) -> bool {
        matches!(self, Step::New { stored: false, .. })
    }
}

/// New content read from disk, with its deflated form.
struct Deflated {
    data: FileBytes,
    payload: Vec<u8>,
}

/// Reads and deflates the deflated `New` steps of a window in parallel. The
/// result is indexed like `steps`, so the writer keeps the output order.
fn deflate_window(steps: &[Step], threads: usize) -> Result<Vec<Option<Deflated>>> {
    let jobs: Vec<(usize, &Path)> = steps
        .iter()
        .enumerate()
        .filter_map(|(i, step)| match step {
            Step::New {
                path,
                stored: false,
                ..
            } => Some((i, path.as_path())),
            _ => None,
        })
        .collect();
    let mut out: Vec<Option<Deflated>> = steps.iter().map(|_| None).collect();
    if jobs.is_empty() {
        return Ok(out);
    }

    let next = AtomicUsize::new(0);
    let results: Vec<Result<Vec<(usize, Deflated)>>> = thread::scope(|scope| {
        let workers: Vec<_> = (0..threads.min(jobs.len()))
            .map(|_| {
                scope.spawn(|| -> Result<Vec<(usize, Deflated)>> {
                    let mut done = Vec::new();
                    loop {
                        let job = next.fetch_add(1, Ordering::Relaxed);
                        let Some(&(step, path)) = jobs.get(job) else {
                            return Ok(done);
                        };
                        let data = map_file(path)?;
                        let payload = deflate(&data)?;
                        done.push((step, Deflated { data, payload }));
                    }
                })
            })
            .collect();
        workers
            .into_iter()
            .map(|worker| worker.join().unwrap_or_else(|_| Err(anyhow!("deflate worker panicked"))))
            .collect()
    });
    for result in results {
        for (step, deflated) in result? {
            out[step] = Some(deflated);
        }
    }
    Ok(out)
}

fn worker_count() -> usize {
    thread::available_parallelism().map_or(1, |n| n.get().min(MAX_WORKERS))
}

fn declared_entry_count(bytes: &[u8]) -> Result<usize> {
    let pos = find_eocd(bytes)?;
    Ok(u16::from_le_bytes([bytes[pos + 10], bytes[pos + 11]]) as usize)
}

/// The bytes of a file on disk, mapped instead of copied to the heap. An empty
/// file cannot be mapped, so it is an empty slice.
enum FileBytes {
    Mapped(memmap2::Mmap),
    Empty,
}

impl Deref for FileBytes {
    type Target = [u8];

    fn deref(&self) -> &[u8] {
        match self {
            FileBytes::Mapped(map) => map,
            FileBytes::Empty => &[],
        }
    }
}

fn map_file(path: &Path) -> Result<FileBytes> {
    let file = File::open(path).with_context(|| format!("open {}", path.display()))?;
    if file.metadata()?.len() == 0 {
        return Ok(FileBytes::Empty);
    }
    // SAFETY: the mapping is read only and the file is not modified while it is mapped.
    let map = unsafe { memmap2::Mmap::map(&file) }
        .with_context(|| format!("memory-map {}", path.display()))?;
    Ok(FileBytes::Mapped(map))
}

/// The stored bytes of an input entry, as a slice of the mapped input.
fn raw_bytes<'a>(map: &'a [u8], meta: &Meta) -> Result<&'a [u8]> {
    let start = usize::try_from(meta.data_start).context("entry data offset")?;
    let len = usize::try_from(meta.compressed).context("entry data length")?;
    map.get(start..)
        .and_then(|rest| rest.get(..len))
        .with_context(|| format!("entry {} data is outside the input", meta.name))
}

fn read_entry<R: Read + Seek>(archive: &mut ZipArchive<R>, index: usize) -> Result<Vec<u8>> {
    let mut data = Vec::new();
    archive.by_index(index)?.read_to_end(&mut data)?;
    Ok(data)
}

fn is_native_lib(name: &str) -> bool {
    name.starts_with("lib/") && name.ends_with(".so")
}

/// Entries that are written stored when they are given as new content.
fn prefers_stored(name: &str) -> bool {
    name == "resources.arsc" || is_native_lib(name)
}

fn alignment_for(name: &str, method: u16) -> usize {
    match method {
        METHOD_STORED if is_native_lib(name) => NATIVE_LIB_ALIGN,
        METHOD_STORED => STORED_ALIGN,
        _ => 1,
    }
}

fn name_flags(name: &str) -> u16 {
    if name.is_ascii() {
        0
    } else {
        UTF8_NAME_FLAG
    }
}

#[derive(Clone, Copy)]
struct Stamp {
    time: u16,
    date: u16,
    attr: u32,
}

impl Stamp {
    /// 1980-01-01 00:00:00, so output does not depend on the clock.
    const NEW: Stamp = Stamp {
        time: 0,
        date: 0x0021,
        attr: UNIX_REGULAR_FILE << 16,
    };
}

struct Meta {
    name: String,
    method: u16,
    crc: u32,
    compressed: u64,
    size: u64,
    time: u16,
    date: u16,
    attr: u32,
    data_start: u64,
}

impl Meta {
    fn read<R: Read + Seek>(archive: &mut ZipArchive<R>, index: usize) -> Result<Self> {
        let entry = archive.by_index_raw(index)?;
        ensure!(!entry.encrypted(), "entry {} is encrypted", entry.name());
        let modified = entry.last_modified().unwrap_or_default();
        let method = match entry.compression() {
            CompressionMethod::Stored => METHOD_STORED,
            CompressionMethod::Deflated => METHOD_DEFLATED,
            other => bail!(
                "entry {} uses unsupported compression {other:?}",
                entry.name()
            ),
        };
        Ok(Meta {
            name: entry.name().to_owned(),
            method,
            crc: entry.crc32(),
            compressed: entry.compressed_size(),
            size: entry.size(),
            time: modified.timepart(),
            date: modified.datepart(),
            attr: entry.unix_mode().map_or(0, |mode| mode << 16),
            data_start: entry.data_start(),
        })
    }

    fn stamp(&self) -> Stamp {
        Stamp {
            time: self.time,
            date: self.date,
            attr: self.attr,
        }
    }
}

struct Header {
    name: String,
    method: u16,
    time: u16,
    date: u16,
    crc: u32,
    compressed: u64,
    size: u64,
    attr: u32,
}

struct Entry {
    header: Header,
    offset: u64,
}

struct Writer<W: Write> {
    inner: W,
    pos: u64,
    entries: Vec<Entry>,
}

impl<W: Write> Writer<W> {
    fn new(inner: W) -> Self {
        Self {
            inner,
            pos: 0,
            entries: Vec::new(),
        }
    }

    fn put(&mut self, bytes: &[u8]) -> io::Result<()> {
        self.inner.write_all(bytes)?;
        self.pos += bytes.len() as u64;
        Ok(())
    }

    /// Writes new content. `deflated` is its compressed form, or `None` to store it.
    fn write_new(
        &mut self,
        name: &str,
        stamp: Stamp,
        data: &[u8],
        deflated: Option<&[u8]>,
    ) -> Result<()> {
        let (method, payload) = match deflated {
            Some(payload) => (METHOD_DEFLATED, payload),
            None => (METHOD_STORED, data),
        };
        let header = Header {
            name: name.to_owned(),
            method,
            time: stamp.time,
            date: stamp.date,
            crc: crc32fast::hash(data),
            compressed: payload.len() as u64,
            size: data.len() as u64,
            attr: stamp.attr,
        };
        self.begin_entry(header)?;
        self.put(payload)?;
        Ok(())
    }

    fn copy_raw(&mut self, meta: &Meta, raw: &[u8]) -> Result<()> {
        let header = Header {
            name: meta.name.clone(),
            method: meta.method,
            time: meta.time,
            date: meta.date,
            crc: meta.crc,
            compressed: meta.compressed,
            size: meta.size,
            attr: meta.attr,
        };
        self.begin_entry(header)?;
        self.put(raw)?;
        Ok(())
    }

    /// Writes a local file header, padded so that the data starts on the alignment
    /// the entry needs, and records the entry for the central directory.
    fn begin_entry(&mut self, header: Header) -> Result<()> {
        let name = header.name.as_bytes();
        check_length(header.compressed, "entry data")?;
        check_length(header.size, "entry data")?;
        let offset = self.pos;
        let align = alignment_for(&header.name, header.method);
        let extra_len = padding_len(self.pos + LOCAL_HEADER_LEN + name.len() as u64, align);

        let mut out = Vec::with_capacity(LOCAL_HEADER_LEN as usize + name.len() + extra_len);
        out.extend_from_slice(&LOCAL_SIG.to_le_bytes());
        out.extend_from_slice(&VERSION_NEEDED.to_le_bytes());
        out.extend_from_slice(&name_flags(&header.name).to_le_bytes());
        out.extend_from_slice(&header.method.to_le_bytes());
        out.extend_from_slice(&header.time.to_le_bytes());
        out.extend_from_slice(&header.date.to_le_bytes());
        out.extend_from_slice(&header.crc.to_le_bytes());
        out.extend_from_slice(&(header.compressed as u32).to_le_bytes());
        out.extend_from_slice(&(header.size as u32).to_le_bytes());
        out.extend_from_slice(&(name.len() as u16).to_le_bytes());
        out.extend_from_slice(&(extra_len as u16).to_le_bytes());
        out.extend_from_slice(name);
        if extra_len > 0 {
            out.extend_from_slice(&PADDING_EXTRA_ID.to_le_bytes());
            out.extend_from_slice(&((extra_len - 4) as u16).to_le_bytes());
            out.resize(out.len() + extra_len - 4, 0);
        }
        self.put(&out)?;
        self.entries.push(Entry { header, offset });
        Ok(())
    }

    fn central_directory(&self) -> Result<Vec<u8>> {
        let mut out = Vec::new();
        for entry in &self.entries {
            let h = &entry.header;
            let name = h.name.as_bytes();
            out.extend_from_slice(&CENTRAL_SIG.to_le_bytes());
            out.extend_from_slice(&VERSION_MADE_BY.to_le_bytes());
            out.extend_from_slice(&VERSION_NEEDED.to_le_bytes());
            out.extend_from_slice(&name_flags(&h.name).to_le_bytes());
            out.extend_from_slice(&h.method.to_le_bytes());
            out.extend_from_slice(&h.time.to_le_bytes());
            out.extend_from_slice(&h.date.to_le_bytes());
            out.extend_from_slice(&h.crc.to_le_bytes());
            out.extend_from_slice(&(h.compressed as u32).to_le_bytes());
            out.extend_from_slice(&(h.size as u32).to_le_bytes());
            out.extend_from_slice(&(name.len() as u16).to_le_bytes());
            out.extend_from_slice(&0u16.to_le_bytes()); // extra length
            out.extend_from_slice(&0u16.to_le_bytes()); // comment length
            out.extend_from_slice(&0u16.to_le_bytes()); // disk number
            out.extend_from_slice(&0u16.to_le_bytes()); // internal attributes
            out.extend_from_slice(&h.attr.to_le_bytes());
            out.extend_from_slice(
                &check_length(entry.offset, "local header offset")?.to_le_bytes(),
            );
            out.extend_from_slice(name);
        }
        Ok(out)
    }

    /// Writes the central directory and EOCD after the entries, which leaves the file
    /// unsigned. Returns its length.
    fn finish(mut self) -> Result<u64> {
        let central = self.central_directory()?;
        let end = eocd(self.entries.len(), central.len() as u64, self.pos)?;
        self.inner.write_all(&central)?;
        self.inner.write_all(&end)?;
        self.inner.flush()?;
        Ok(self.pos + central.len() as u64 + end.len() as u64)
    }
}

impl<W: Write> Write for Writer<W> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.put(buf)?;
        Ok(buf.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

/// Extra-field length that moves `start + extra` onto a multiple of `align`.
/// Zero when no padding is needed; otherwise at least 4 bytes, the size of the
/// extra-field header.
fn padding_len(start: u64, align: usize) -> usize {
    if align <= 1 {
        return 0;
    }
    let rem = (start % align as u64) as usize;
    if rem == 0 {
        return 0;
    }
    let need = align - rem;
    if need >= 4 {
        need
    } else {
        need + align
    }
}

fn eocd(entries: usize, central_len: u64, central_offset: u64) -> Result<Vec<u8>> {
    ensure!(
        entries <= u16::MAX as usize,
        "more than 65535 entries needs ZIP64"
    );
    let mut out = Vec::with_capacity(22);
    out.extend_from_slice(&EOCD_SIG.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes()); // disk number
    out.extend_from_slice(&0u16.to_le_bytes()); // disk with central directory
    out.extend_from_slice(&(entries as u16).to_le_bytes());
    out.extend_from_slice(&(entries as u16).to_le_bytes());
    out.extend_from_slice(&check_length(central_len, "central directory")?.to_le_bytes());
    out.extend_from_slice(&check_length(central_offset, "central directory offset")?.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes()); // comment length
    Ok(out)
}

/// Level 3 is the level the JVM path uses for the same entries.
fn deflate(data: &[u8]) -> io::Result<Vec<u8>> {
    let mut encoder = DeflateEncoder::new(Vec::new(), Compression::new(DEFLATE_LEVEL));
    encoder.write_all(data)?;
    encoder.finish()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn padding_reaches_alignment() {
        for align in [4usize, 16 * 1024] {
            for start in 0u64..(align as u64 * 2) {
                let extra = padding_len(start, align) as u64;
                assert!(extra == 0 || extra >= 4);
                assert_eq!(
                    (start + extra) % align as u64,
                    0,
                    "start {start} align {align}"
                );
            }
        }
        assert_eq!(padding_len(123, 1), 0);
    }

    #[test]
    fn native_lib_rule_needs_stored_so_under_lib_dir() {
        assert!(is_native_lib("lib/arm64-v8a/libfoo.so"));
        assert!(!is_native_lib("assets/libfoo.so"));
        assert_eq!(
            alignment_for("lib/x86/a.so", METHOD_STORED),
            NATIVE_LIB_ALIGN
        );
        assert_eq!(alignment_for("lib/x86/a.so", METHOD_DEFLATED), 1);
        assert_eq!(alignment_for("resources.arsc", METHOD_STORED), STORED_ALIGN);
    }
}
