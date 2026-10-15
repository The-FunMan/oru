//! JNI surface of [`crate::index::DexIndex`], for `app.morphe.patcher.dex.NativeDexIndexLib`.
//! Queries return class ordinals as int arrays, so the boundary stays coarse.

use crate::index::DexIndex;
use crate::jni_guard::guard;
use jni::objects::{JClass, JIntArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jint, jlong};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::{Arc, LazyLock, PoisonError, RwLock};

const RUNTIME_EXCEPTION: &str = "java/lang/RuntimeException";

/// Open indexes by handle. The JVM holds an id, not a pointer, so a closed or invented handle is absent instead of
/// being dereferenced. A query clones its `Arc`, so a close that races with it cannot free the index under it.
struct Handles<T> {
    next: jlong,
    live: HashMap<jlong, T>,
}

impl<T> Handles<T> {
    fn new() -> Self {
        Self { next: 0, live: HashMap::new() }
    }

    fn insert(&mut self, value: T) -> jlong {
        self.next += 1;
        self.live.insert(self.next, value);
        self.next
    }

    fn get(&self, id: jlong) -> Option<&T> {
        self.live.get(&id)
    }

    fn remove(&mut self, id: jlong) -> Option<T> {
        self.live.remove(&id)
    }
}

static INDEXES: LazyLock<RwLock<Handles<Arc<DexIndex>>>> = LazyLock::new(|| RwLock::new(Handles::new()));

fn register(index: DexIndex) -> jlong {
    INDEXES.write().unwrap_or_else(PoisonError::into_inner).insert(Arc::new(index))
}

fn unregister(handle: jlong) -> Option<Arc<DexIndex>> {
    INDEXES.write().unwrap_or_else(PoisonError::into_inner).remove(handle)
}

fn index(handle: jlong) -> Option<Arc<DexIndex>> {
    INDEXES.read().unwrap_or_else(PoisonError::into_inner).get(handle).cloned()
}

/// Copies `values` into a new int array. The conversion goes through a fixed stack chunk, so a query does not
/// allocate a second buffer the size of its result.
fn ints<'l>(env: &mut JNIEnv<'l>, values: &[u32]) -> JIntArray<'l> {
    const CHUNK: usize = 256;
    let arr = env.new_int_array(values.len() as jint).expect("int array");
    let mut chunk: [jint; CHUNK] = [0; CHUNK];
    for (i, part) in values.chunks(CHUNK).enumerate() {
        for (dst, &src) in chunk.iter_mut().zip(part) {
            *dst = src as jint;
        }
        let _ = env.set_int_array_region(&arr, (i * CHUNK) as jint, &chunk[..part.len()]);
    }
    arr
}

fn empty_ints<'l>(env: &mut JNIEnv<'l>) -> JIntArray<'l> {
    ints(env, &[])
}

/// Reads the path strings. Each element reference is released after its copy, so the live local references stay
/// constant however many paths there are.
fn path_strings(env: &mut JNIEnv, paths: &JObjectArray) -> Option<Vec<String>> {
    let n = env.get_array_length(paths).ok()?;
    (0..n)
        .map(|i| {
            let element = env.get_object_array_element(paths, i).ok()?;
            let element = env.auto_local(JString::from(element));
            let text: String = env.get_string(&element).ok()?.into();
            Some(text)
        })
        .collect()
}

fn open_index(env: &mut JNIEnv, paths: &JObjectArray) -> Option<jlong> {
    let list = path_strings(env, paths)?;
    let index = DexIndex::open(&list).ok()?;
    Some(register(index))
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_open<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    paths: JObjectArray<'l>,
) -> jlong {
    guard(&mut env, RUNTIME_EXCEPTION, |env| open_index(env, &paths).unwrap_or(0))
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_close<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>, handle: jlong) {
    guard(&mut env, RUNTIME_EXCEPTION, |_| drop(unregister(handle)))
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_stats<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
) -> JLongArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| {
        let (c, m, d) = index(handle).map(|i| (i.class_count() as i64, i.methods as i64, i.desynced_methods as i64)).unwrap_or((0, 0, 0));
        let arr = env.new_long_array(3).expect("long array");
        let _ = env.set_long_array_region(&arr, 0, &[c, m, d]);
        arr
    })
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_descriptors<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
) -> JObjectArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| {
        let Some(idx) = index(handle) else {
            let _ = env.throw_new("java/lang/IllegalStateException", "dex index is closed");
            return JObjectArray::default();
        };
        let arr = env.new_object_array(idx.class_count() as jint, "java/lang/String", JObject::null()).expect("string array");
        for o in 0..idx.class_count() {
            let s = env.new_string(idx.descriptor(o as u32)).expect("string");
            let _ = env.set_object_array_element(&arr, o as jint, &s);
            let _ = env.delete_local_ref(s);
        }
        arr
    })
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_withString<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
    string: JString<'l>,
) -> JIntArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| {
        let idx = index(handle);
        let found: &[u32] = match (&idx, env.get_string(&string)) {
            (Some(idx), Ok(s)) => idx.with_string(s.to_bytes()),
            _ => &[],
        };
        ints(env, found)
    })
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_withTypeHash<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
    hash: jint,
) -> JIntArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| match index(handle) {
        Some(idx) => ints(env, idx.with_type_hash(hash)),
        None => empty_ints(env),
    })
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_withLiteralHash<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
    hash: jint,
) -> JIntArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| match index(handle) {
        Some(idx) => ints(env, idx.with_literal_hash(hash)),
        None => empty_ints(env),
    })
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_withMemberHash<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
    hash: jint,
) -> JIntArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| match index(handle) {
        Some(idx) => ints(env, idx.with_member_hash(hash)),
        None => empty_ints(env),
    })
}

#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_dex_NativeDexIndexLib_withAnyString<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    handle: jlong,
) -> JIntArray<'l> {
    guard(&mut env, RUNTIME_EXCEPTION, |env| match index(handle) {
        Some(idx) => ints(env, idx.with_any_string()),
        None => empty_ints(env),
    })
}

#[cfg(test)]
mod tests {
    use super::Handles;

    #[test]
    fn closed_handles_are_absent_and_never_reused() {
        let mut handles = Handles::new();
        let a = handles.insert("a");
        let b = handles.insert("b");
        assert!(a != 0 && b != 0 && a != b);
        assert_eq!(handles.get(a), Some(&"a"));
        assert_eq!(handles.remove(a), Some("a"));
        assert_eq!(handles.remove(a), None, "a second close finds nothing");
        assert_eq!(handles.get(a), None);
        let c = handles.insert("c");
        assert!(c != a && c != b);
        assert_eq!(handles.get(0), None, "0 is the Kotlin null handle and is never issued");
    }
}
