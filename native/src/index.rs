//! Per-class instruction indexes of a set of dex files: the string literals, the hashes of referenced types and the
//! wide literals each class uses, inverted so a lookup costs the size of its answer.
//!
//! This is a drop-in for what the JVM patcher computes class by class with dexlib2 objects. It reads the dex bytes
//! directly and works for any dex file, so it needs no knowledge of the app being patched.

use memmap2::Mmap;
use rayon::prelude::*;
use std::collections::HashMap;
use std::fs::File;
use std::path::Path;

/// What one class uses, with strings as (dex number, string index) keys.
#[derive(Default)]
struct ClassScan {
    descriptor: String,
    strings: Vec<u64>,
    type_hashes: Vec<i32>,
    member_hashes: Vec<i32>,
    literals: Vec<i64>,
    literal_hashes: Vec<i32>,
}

pub struct DexIndex {
    _maps: Vec<Mmap>,
    classes: Vec<String>,
    strings: HashMap<Box<[u8]>, Vec<u32>>,
    type_hashes: HashMap<i32, Vec<u32>>,
    member_hashes: HashMap<i32, Vec<u32>>,
    literal_hashes: HashMap<i32, Vec<u32>>,
    with_strings: Vec<u32>,
    /// Methods whose instruction stream did not end exactly where its code item says. Always 0 for a correct decoder.
    pub desynced_methods: usize,
    pub methods: usize,
}

struct Dex<'a> {
    data: &'a [u8],
    string_ids_off: usize,
    type_ids_off: usize,
    field_ids_off: usize,
    method_ids_off: usize,
    type_hash: Vec<i32>,
    method_name_hash: Vec<i32>,
    field_name_hash: Vec<i32>,
    n_fields: usize,
    n_methods: usize,
}

fn u16_at(d: &[u8], o: usize) -> Option<u16> {
    d.get(o..o + 2).map(|b| u16::from_le_bytes([b[0], b[1]]))
}

fn u32_at(d: &[u8], o: usize) -> Option<u32> {
    d.get(o..o + 4).map(|b| u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
}

fn uleb(d: &[u8], o: &mut usize) -> Option<u32> {
    let mut result = 0u32;
    let mut shift = 0;
    loop {
        let b = *d.get(*o)?;
        *o += 1;
        result |= ((b & 0x7f) as u32) << shift;
        if b & 0x80 == 0 {
            return Some(result);
        }
        shift += 7;
        if shift > 35 {
            return None;
        }
    }
}

/// Java's `String.hashCode()` of a descriptor stored as modified UTF-8.
fn java_hash(mutf8: &[u8]) -> i32 {
    let mut h: i32 = 0;
    let mut i = 0;
    while i < mutf8.len() {
        let b = mutf8[i];
        let unit: u32 = if b < 0x80 {
            i += 1;
            b as u32
        } else if b & 0xe0 == 0xc0 && i + 1 < mutf8.len() {
            let u = ((b as u32 & 0x1f) << 6) | (mutf8[i + 1] as u32 & 0x3f);
            i += 2;
            u
        } else if i + 2 < mutf8.len() {
            let u = ((b as u32 & 0x0f) << 12) | ((mutf8[i + 1] as u32 & 0x3f) << 6) | (mutf8[i + 2] as u32 & 0x3f);
            i += 3;
            u
        } else {
            i += 1;
            b as u32
        };
        h = h.wrapping_mul(31).wrapping_add(unit as i32);
    }
    h
}

impl<'a> Dex<'a> {
    fn parse(data: &'a [u8]) -> Option<(Self, usize, usize)> {
        if data.len() < 112 || &data[0..4] != b"dex\n" {
            return None;
        }
        let string_ids_size = u32_at(data, 56)? as usize;
        let string_ids_off = u32_at(data, 60)? as usize;
        let type_ids_size = u32_at(data, 64)? as usize;
        let type_ids_off = u32_at(data, 68)? as usize;
        let n_fields = u32_at(data, 80)? as usize;
        let field_ids_off = u32_at(data, 84)? as usize;
        let n_methods = u32_at(data, 88)? as usize;
        let method_ids_off = u32_at(data, 92)? as usize;
        let class_defs_size = u32_at(data, 96)? as usize;
        let class_defs_off = u32_at(data, 100)? as usize;
        let mut dex = Dex {
            data,
            string_ids_off,
            type_ids_off,
            field_ids_off,
            method_ids_off,
            type_hash: Vec::new(),
            method_name_hash: Vec::new(),
            field_name_hash: Vec::new(),
            n_fields,
            n_methods,
        };
        let _ = string_ids_size;
        dex.type_hash = (0..type_ids_size)
            .map(|t| {
                let s = u32_at(data, type_ids_off + t * 4).unwrap_or(0) as usize;
                dex.string_bytes(s).map(java_hash).unwrap_or(0)
            })
            .collect();
        dex.method_name_hash = (0..n_methods)
            .map(|m| {
                let name = u32_at(data, method_ids_off + m * 8 + 4).unwrap_or(0) as usize;
                dex.string_bytes(name).map(java_hash).unwrap_or(0)
            })
            .collect();
        dex.field_name_hash = (0..n_fields)
            .map(|f| {
                let name = u32_at(data, field_ids_off + f * 8 + 4).unwrap_or(0) as usize;
                dex.string_bytes(name).map(java_hash).unwrap_or(0)
            })
            .collect();
        Some((dex, class_defs_size, class_defs_off))
    }

    fn string_bytes(&self, idx: usize) -> Option<&'a [u8]> {
        let off = u32_at(self.data, self.string_ids_off + idx * 4)? as usize;
        let mut p = off;
        uleb(self.data, &mut p)?;
        let end = self.data.get(p..)?.iter().position(|&b| b == 0)? + p;
        self.data.get(p..end)
    }

    fn type_descriptor(&self, type_idx: usize) -> Option<&'a [u8]> {
        let s = u32_at(self.data, self.type_ids_off + type_idx * 4)? as usize;
        self.string_bytes(s)
    }

    fn method_class_hash(&self, idx: usize) -> Option<i32> {
        if idx >= self.n_methods {
            return None;
        }
        let class_idx = u16_at(self.data, self.method_ids_off + idx * 8)? as usize;
        self.type_hash.get(class_idx).copied()
    }

    fn field_class_hash(&self, idx: usize) -> Option<i32> {
        if idx >= self.n_fields {
            return None;
        }
        let class_idx = u16_at(self.data, self.field_ids_off + idx * 8)? as usize;
        self.type_hash.get(class_idx).copied()
    }

    fn scan_class(&self, dex_no: u64, class_def_off: usize, desynced: &mut usize, methods: &mut usize) -> Option<ClassScan> {
        let class_idx = u32_at(self.data, class_def_off)? as usize;
        let mut scan = ClassScan {
            descriptor: String::from_utf8_lossy(self.type_descriptor(class_idx)?).into_owned(),
            ..Default::default()
        };
        let class_data_off = u32_at(self.data, class_def_off + 24)? as usize;
        if class_data_off != 0 {
            let mut p = class_data_off;
            let static_fields = uleb(self.data, &mut p)?;
            let instance_fields = uleb(self.data, &mut p)?;
            let direct = uleb(self.data, &mut p)?;
            let virt = uleb(self.data, &mut p)?;
            for _ in 0..(static_fields + instance_fields) {
                uleb(self.data, &mut p)?;
                uleb(self.data, &mut p)?;
            }
            for _ in 0..(direct + virt) {
                uleb(self.data, &mut p)?; // method index delta
                uleb(self.data, &mut p)?; // access flags
                let code_off = uleb(self.data, &mut p)? as usize;
                if code_off != 0 {
                    *methods += 1;
                    if !self.scan_code(dex_no, code_off, &mut scan) {
                        *desynced += 1;
                    }
                }
            }
        }
        scan.strings.sort_unstable();
        scan.strings.dedup();
        scan.type_hashes.sort_unstable();
        scan.type_hashes.dedup();
        scan.member_hashes.sort_unstable();
        scan.member_hashes.dedup();
        // java.lang.Long.hashCode: the value xor its upper half
        scan.literal_hashes = scan.literals.iter().map(|&v| (v ^ ((v as u64) >> 32) as i64) as i32).collect();
        scan.literal_hashes.sort_unstable();
        scan.literal_hashes.dedup();
        scan.literals = Vec::new();
        Some(scan)
    }

    /// Walks one code item. Returns false when the stream does not end exactly at its declared size.
    fn scan_code(&self, dex_no: u64, code_off: usize, scan: &mut ClassScan) -> bool {
        let size = match u32_at(self.data, code_off + 12) {
            Some(s) => s as usize,
            None => return false,
        };
        let start = code_off + 16;
        let raw = match self.data.get(start..start + size * 2) {
            Some(r) => r,
            None => return false,
        };
        let unit = |i: usize| -> u16 { u16::from_le_bytes([raw[i * 2], raw[i * 2 + 1]]) };
        let mut i = 0usize;
        while i < size {
            let u = unit(i);
            let op = (u & 0xff) as u8;
            // each arm yields the instruction length in code units
            let len = match op {
                0x00 => match u {
                    0x0100 if i + 1 < size => 4 + unit(i + 1) as usize * 2,
                    0x0200 if i + 1 < size => 2 + unit(i + 1) as usize * 4,
                    0x0300 if i + 3 < size => 4 + (((unit(i + 2) as usize) | ((unit(i + 3) as usize) << 16)) * unit(i + 1) as usize + 1) / 2,
                    _ => 1,
                },
                0x12 => {
                    scan.literals.push(((u as i16) >> 12) as i64);
                    1
                }
                0x13 | 0x16 if i + 1 < size => {
                    scan.literals.push(unit(i + 1) as i16 as i64);
                    2
                }
                0x14 | 0x17 if i + 2 < size => {
                    scan.literals.push((unit(i + 1) as u32 | (unit(i + 2) as u32) << 16) as i32 as i64);
                    3
                }
                0x15 if i + 1 < size => {
                    scan.literals.push(((unit(i + 1) as i16 as i32) << 16) as i64);
                    2
                }
                0x19 if i + 1 < size => {
                    scan.literals.push((unit(i + 1) as i16 as i64) << 48);
                    2
                }
                0x18 if i + 4 < size => {
                    let v = unit(i + 1) as u64 | (unit(i + 2) as u64) << 16 | (unit(i + 3) as u64) << 32 | (unit(i + 4) as u64) << 48;
                    scan.literals.push(v as i64);
                    5
                }
                0x1a if i + 1 < size => {
                    scan.strings.push(dex_no << 32 | unit(i + 1) as u64);
                    2
                }
                0x1b if i + 2 < size => {
                    scan.strings.push(dex_no << 32 | (unit(i + 1) as u32 | (unit(i + 2) as u32) << 16) as u64);
                    3
                }
                0x1c | 0x1f | 0x20 | 0x22 | 0x23 if i + 1 < size => {
                    if let Some(h) = self.type_hash.get(unit(i + 1) as usize) {
                        scan.type_hashes.push(*h);
                    }
                    2
                }
                0x24 | 0x25 if i + 2 < size => {
                    if let Some(h) = self.type_hash.get(unit(i + 1) as usize) {
                        scan.type_hashes.push(*h);
                    }
                    3
                }
                0x52..=0x6d if i + 1 < size => {
                    if let Some(h) = self.field_class_hash(unit(i + 1) as usize) {
                        scan.type_hashes.push(h);
                    }
                    if let Some(h) = self.field_name_hash.get(unit(i + 1) as usize) {
                        scan.member_hashes.push(*h);
                    }
                    2
                }
                0x6e..=0x72 | 0x74..=0x78 if i + 2 < size => {
                    if let Some(h) = self.method_class_hash(unit(i + 1) as usize) {
                        scan.type_hashes.push(h);
                    }
                    if let Some(h) = self.method_name_hash.get(unit(i + 1) as usize) {
                        scan.member_hashes.push(*h);
                    }
                    3
                }
                0xfa | 0xfb if i + 3 < size => {
                    if let Some(h) = self.method_class_hash(unit(i + 1) as usize) {
                        scan.type_hashes.push(h);
                    }
                    if let Some(h) = self.method_name_hash.get(unit(i + 1) as usize) {
                        scan.member_hashes.push(*h);
                    }
                    4
                }
                0xd0..=0xd7 if i + 1 < size => {
                    scan.literals.push(unit(i + 1) as i16 as i64);
                    2
                }
                0xd8..=0xe2 if i + 1 < size => {
                    scan.literals.push(((unit(i + 1) >> 8) as u8 as i8) as i64);
                    2
                }
                0x02 | 0x05 | 0x08 | 0x29 | 0x2d..=0x3d | 0x44..=0x51 | 0x90..=0xaf | 0xfe | 0xff => 2,
                0x03 | 0x06 | 0x09 | 0x26 | 0x2a | 0x2b | 0x2c | 0xfc | 0xfd => 3,
                _ => 1,
            };
            i += len;
        }
        i == size
    }
}

impl DexIndex {
    /// Indexes the given dex files, in order. Class ordinals follow that order and each file's class_defs order.
    pub fn open(paths: &[impl AsRef<Path>]) -> std::io::Result<Self> {
        let mut maps = Vec::with_capacity(paths.len());
        for p in paths {
            let file = File::open(p.as_ref())?;
            maps.push(unsafe { Mmap::map(&file)? });
        }
        let mut scans: Vec<ClassScan> = Vec::new();
        let (mut desynced, mut methods) = (0usize, 0usize);
        let mut dexes: Vec<Dex> = Vec::new();
        let mut counts = Vec::new();
        for m in &maps {
            if let Some((dex, n, off)) = Dex::parse(&m[..]) {
                counts.push((n, off));
                dexes.push(dex);
            } else {
                counts.push((0, 0));
                dexes.push(Dex {
                    data: &m[..0],
                    string_ids_off: 0,
                    type_ids_off: 0,
                    field_ids_off: 0,
                    method_ids_off: 0,
                    type_hash: vec![],
                    method_name_hash: vec![],
                    field_name_hash: vec![],
                    n_fields: 0,
                    n_methods: 0,
                });
            }
        }
        for (no, dex) in dexes.iter().enumerate() {
            let (n, off) = counts[no];
            let parts: Vec<(Option<ClassScan>, usize, usize)> = (0..n)
                .into_par_iter()
                .map(|c| {
                    let (mut d, mut mth) = (0, 0);
                    let s = dex.scan_class(no as u64, off + c * 32, &mut d, &mut mth);
                    (s, d, mth)
                })
                .collect();
            for (s, d, mth) in parts {
                desynced += d;
                methods += mth;
                if let Some(s) = s {
                    scans.push(s);
                }
            }
        }
        let mut classes = Vec::with_capacity(scans.len());
        let mut by_key: HashMap<u64, Vec<u32>> = HashMap::new();
        let mut type_hashes: HashMap<i32, Vec<u32>> = HashMap::new();
        let mut member_hashes: HashMap<i32, Vec<u32>> = HashMap::new();
        let mut literal_hashes: HashMap<i32, Vec<u32>> = HashMap::new();
        let mut with_strings = Vec::new();
        for (ord, s) in scans.into_iter().enumerate() {
            let ord = ord as u32;
            if !s.strings.is_empty() {
                with_strings.push(ord);
            }
            for k in s.strings {
                by_key.entry(k).or_default().push(ord);
            }
            for h in s.type_hashes {
                type_hashes.entry(h).or_default().push(ord);
            }
            for h in s.member_hashes {
                member_hashes.entry(h).or_default().push(ord);
            }
            for h in s.literal_hashes {
                literal_hashes.entry(h).or_default().push(ord);
            }
            classes.push(s.descriptor);
        }
        let mut strings: HashMap<Box<[u8]>, Vec<u32>> = HashMap::with_capacity(by_key.len());
        for (k, mut v) in by_key {
            let (no, idx) = ((k >> 32) as usize, (k & 0xffff_ffff) as usize);
            if let Some(b) = dexes.get(no).and_then(|d| d.string_bytes(idx)) {
                let e = strings.entry(b.to_vec().into_boxed_slice()).or_default();
                e.append(&mut v);
            }
        }
        for v in strings.values_mut() {
            v.sort_unstable();
            v.dedup();
        }
        drop(dexes);
        Ok(DexIndex { _maps: maps, classes, strings, type_hashes, member_hashes, literal_hashes, with_strings, desynced_methods: desynced, methods })
    }

    pub fn class_count(&self) -> usize {
        self.classes.len()
    }

    pub fn descriptor(&self, ordinal: u32) -> &str {
        &self.classes[ordinal as usize]
    }

    /// Ordinals of the classes with a `const-string` of exactly these modified UTF-8 bytes.
    pub fn with_string(&self, mutf8: &[u8]) -> &[u32] {
        self.strings.get(mutf8).map(|v| v.as_slice()).unwrap_or(&[])
    }

    pub fn with_type_hash(&self, hash: i32) -> &[u32] {
        self.type_hashes.get(&hash).map(|v| v.as_slice()).unwrap_or(&[])
    }

    pub fn with_member_hash(&self, hash: i32) -> &[u32] {
        self.member_hashes.get(&hash).map(|v| v.as_slice()).unwrap_or(&[])
    }

    /// Classes with a wide literal whose `Long.hashCode()` is `hash`.
    pub fn with_literal_hash(&self, hash: i32) -> &[u32] {
        self.literal_hashes.get(&hash).map(|v| v.as_slice()).unwrap_or(&[])
    }

    /// Classes with at least one `const-string`.
    pub fn with_any_string(&self) -> &[u32] {
        &self.with_strings
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_hash_matches_the_jvm() {
        // "Ljava/lang/String;".hashCode() computed by Java
        // expected values computed independently of this code
        assert_eq!(java_hash(b"Ljava/lang/String;"), 1379658506);
        assert_eq!(java_hash(b"Landroid/view/View;"), -1078235368);
        assert_eq!(java_hash(b""), 0);
        assert_eq!(java_hash(b"a"), 97);
        assert_eq!(java_hash(b"ab"), 97 * 31 + 98);
        // a two-byte sequence is one UTF-16 unit
        assert_eq!(java_hash(&[0xc3, 0xa9]), 0xe9);
    }

    /// Runs on real dex files when ORU_TEST_DEX lists them separated by ';'.
    #[test]
    fn real_dex_streams_end_exactly() {
        let Ok(list) = std::env::var("ORU_TEST_DEX") else { return };
        let paths: Vec<_> = list.split(';').collect();
        let index = DexIndex::open(&paths).unwrap();
        println!("classes {} methods {} desynced {}", index.class_count(), index.methods, index.desynced_methods);
        assert!(index.class_count() > 0);
        assert_eq!(index.desynced_methods, 0, "the decoder lost its place in some methods");
    }
}
