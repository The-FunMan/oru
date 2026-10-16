//! JNI entry points of native-packager, loaded by `app.morphe.patcher.apk.NativePackagerLib`.
//!
//! Every function returns 0 and throws `java.io.IOException` on failure, so the
//! Kotlin side can fall back to the JVM path. A panic is thrown the same way.

mod jni_guard;

use jni::objects::{JBooleanArray, JByteArray, JClass, JObject, JObjectArray, JString};
use jni::sys::jlong;
use jni::JNIEnv;
use jni_guard::guard;
use native_packager::{
    package_with_progress, sign_in_place_with_progress, ApkSigner, Options, Plan, ZeroOnDrop,
};
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};

const IO_EXCEPTION: &str = "java/io/IOException";

/// Writes `input` to `output` with the given new entries (`names[i]` is read from `paths[i]`,
/// stored when `stored[i]`) and the `remove` names dropped. The output is unsigned.
/// Returns the output length.
#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_apk_NativePackagerLib_write<'local>(
    env: JNIEnv<'local>,
    class: JClass<'local>,
    input: JString<'local>,
    output: JString<'local>,
    names: JObjectArray<'local>,
    paths: JObjectArray<'local>,
    stored: JBooleanArray<'local>,
    remove: JObjectArray<'local>,
) -> jlong {
    Java_app_morphe_patcher_apk_NativePackagerLib_writeWithProgress(
        env,
        class,
        input,
        output,
        names,
        paths,
        stored,
        remove,
        JObject::null(),
    )
}

/// Like [`Java_app_morphe_patcher_apk_NativePackagerLib_write`], but reports progress to `callback`
/// with `onProgress(float)`.
#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_apk_NativePackagerLib_writeWithProgress<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JString<'local>,
    output: JString<'local>,
    names: JObjectArray<'local>,
    paths: JObjectArray<'local>,
    stored: JBooleanArray<'local>,
    remove: JObjectArray<'local>,
    callback: JObject<'local>,
) -> jlong {
    guard(&mut env, IO_EXCEPTION, |env| {
        let result = (|| -> Result<u64, String> {
            let input = path_of(env, &input)?;
            let output = path_of(env, &output)?;
            let names = strings(env, &names)?;
            let paths = strings(env, &paths)?;
            if names.len() != paths.len() || names.len() != env_len(env, &stored)? {
                return Err("names, paths and stored differ in length".into());
            }
            let stored = booleans(env, &stored)?;
            let remove: BTreeSet<String> = strings(env, &remove)?.into_iter().collect();

            let plan = Plan {
                remove,
                stored: names.iter().cloned().zip(stored).collect(),
            };
            let entries: BTreeMap<String, PathBuf> = names
                .into_iter()
                .zip(paths.into_iter().map(PathBuf::from))
                .collect();
            let mut progress = JavaProgress::new(&mut *env, &callback);
            let report = package_with_progress(
                &Options {
                    input: &input,
                    output: &output,
                    entries,
                    signer: None,
                },
                &plan,
                |fraction| progress.report(fraction),
            )
            .map_err(|error| format!("{error:#}"))?;
            Ok(report.output_bytes)
        })();
        finish(env, result)
    })
}

/// Signs the unsigned APK at `path` in place with the PKCS#8 DER private key and the DER
/// certificate. The key bytes are overwritten before returning. Returns the new length.
#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_apk_NativePackagerLib_signInPlace<'local>(
    env: JNIEnv<'local>,
    class: JClass<'local>,
    path: JString<'local>,
    key: JByteArray<'local>,
    certificate: JByteArray<'local>,
) -> jlong {
    Java_app_morphe_patcher_apk_NativePackagerLib_signInPlaceWithProgress(
        env,
        class,
        path,
        key,
        certificate,
        JObject::null(),
    )
}

/// Like [`Java_app_morphe_patcher_apk_NativePackagerLib_signInPlace`], but reports progress to `callback`
/// with `onProgress(float)`.
#[no_mangle]
pub extern "system" fn Java_app_morphe_patcher_apk_NativePackagerLib_signInPlaceWithProgress<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
    key: JByteArray<'local>,
    certificate: JByteArray<'local>,
    callback: JObject<'local>,
) -> jlong {
    guard(&mut env, IO_EXCEPTION, |env| {
        let result = (|| -> Result<u64, String> {
            let path = path_of(env, &path)?;
            let mut key_bytes = env
                .convert_byte_array(&key)
                .map_err(|error| error.to_string())?;
            let key = ZeroOnDrop::new(&mut key_bytes);
            let certificate = env
                .convert_byte_array(&certificate)
                .map_err(|error| error.to_string())?;
            let signer =
                ApkSigner::from_der(&key, &certificate).map_err(|error| format!("{error:#}"))?;
            let mut progress = JavaProgress::new(&mut *env, &callback);
            sign_in_place_with_progress(&path, &signer, |fraction| progress.report(fraction))
                .map_err(|error| format!("{error:#}"))
        })();
        finish(env, result)
    })
}

/// Reports progress to the Java `onProgress(float)` callback. A null callback reports nothing.
/// A Java exception thrown by the callback is described to the log and cleared, and reports stop
/// after it; the native write or sign keeps running to completion.
struct JavaProgress<'a, 'local> {
    env: &'a mut JNIEnv<'local>,
    callback: &'a JObject<'local>,
    failed: bool,
}

impl<'a, 'local> JavaProgress<'a, 'local> {
    fn new(env: &'a mut JNIEnv<'local>, callback: &'a JObject<'local>) -> Self {
        Self {
            env,
            callback,
            failed: false,
        }
    }

    fn report(&mut self, fraction: f32) {
        if self.failed || self.callback.is_null() {
            return;
        }
        let called = self.env.call_method(
            self.callback,
            "onProgress",
            "(F)V",
            &[jni::objects::JValue::Float(fraction)],
        );
        if called.is_err() {
            self.failed = true;
            if self.env.exception_check().unwrap_or(false) {
                let _ = self.env.exception_describe();
                let _ = self.env.exception_clear();
            }
        }
    }
}

fn finish(env: &mut JNIEnv, result: Result<u64, String>) -> jlong {
    match result {
        Ok(length) => length as jlong,
        Err(message) => {
            let _ = env.throw_new(IO_EXCEPTION, message);
            0
        }
    }
}

fn path_of(env: &mut JNIEnv, value: &JString) -> Result<PathBuf, String> {
    let text: String = env
        .get_string(value)
        .map_err(|error| error.to_string())?
        .into();
    Ok(Path::new(&text).to_path_buf())
}

fn env_len(env: &mut JNIEnv, array: &JBooleanArray) -> Result<usize, String> {
    env.get_array_length(array)
        .map(|n| n as usize)
        .map_err(|error| error.to_string())
}

/// Each element reference is released after its copy. Without that, a list longer than the 512
/// local references a native frame guarantees would overflow the local reference table.
fn strings(env: &mut JNIEnv, array: &JObjectArray) -> Result<Vec<String>, String> {
    let len = env
        .get_array_length(array)
        .map_err(|error| error.to_string())?;
    (0..len)
        .map(|i| {
            let element: JObject = env
                .get_object_array_element(array, i)
                .map_err(|error| error.to_string())?;
            let element = env.auto_local(JString::from(element));
            let text: String = env
                .get_string(&element)
                .map_err(|error| error.to_string())?
                .into();
            Ok(text)
        })
        .collect()
}

fn booleans(env: &mut JNIEnv, array: &JBooleanArray) -> Result<Vec<bool>, String> {
    let len = env_len(env, array)?;
    let mut buf = vec![0u8; len];
    env.get_boolean_array_region(array, 0, &mut buf)
        .map_err(|error| error.to_string())?;
    Ok(buf.into_iter().map(|b| b != 0).collect())
}
