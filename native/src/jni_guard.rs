//! Keeps a Rust panic from unwinding into the JVM, which aborts the process. Every `extern "system"` entry runs its
//! body through [`guard`].

use jni::JNIEnv;
use std::any::Any;
use std::panic::{catch_unwind, AssertUnwindSafe};

/// Runs `body` with the JNI environment. A panic is thrown as a Java `exception` carrying the panic message, and the
/// default value of `T` is returned.
pub fn guard<'l, T: Default>(env: &mut JNIEnv<'l>, exception: &str, body: impl FnOnce(&mut JNIEnv<'l>) -> T) -> T {
    match run_catching(|| body(&mut *env)) {
        Ok(value) => value,
        Err(message) => {
            // A pending exception from the body is the one the JVM should see, so a second throw is skipped.
            if !env.exception_check().unwrap_or(false) {
                let _ = env.throw_new(exception, message);
            }
            T::default()
        }
    }
}

/// Runs `body`, returning the panic message instead of unwinding.
pub fn run_catching<T>(body: impl FnOnce() -> T) -> Result<T, String> {
    catch_unwind(AssertUnwindSafe(body)).map_err(|payload| panic_message(payload.as_ref()))
}

fn panic_message(payload: &(dyn Any + Send)) -> String {
    if let Some(text) = payload.downcast_ref::<&str>() {
        (*text).to_string()
    } else if let Some(text) = payload.downcast_ref::<String>() {
        text.clone()
    } else {
        "native code panicked".to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::run_catching;

    #[test]
    fn a_panic_is_reported_with_its_message_and_the_default_follows() {
        let result: Result<u32, String> = run_catching(|| panic!("boom"));
        assert_eq!(result, Err("boom".to_string()));
        assert_eq!(result.unwrap_or_default(), 0);
    }

    #[test]
    fn a_formatted_panic_keeps_its_text() {
        let result: Result<u32, String> = run_catching(|| panic!("index {} closed", 7));
        assert_eq!(result, Err("index 7 closed".to_string()));
    }

    #[test]
    fn a_normal_return_passes_through() {
        assert_eq!(run_catching(|| 5u32), Ok(5));
    }
}
