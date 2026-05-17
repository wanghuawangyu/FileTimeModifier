use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jlong, jboolean};
use libc::{utimensat, AT_FDCWD, timespec};
use std::ffi::CString;

#[no_mangle]
pub extern "system" fn Java_com_example_filemod_TimeModifier_setFileTimes(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    new_mtime_ms: jlong,
    new_atime_ms: jlong,
) -> jboolean {
    let path: String = match env.get_string(&path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let path_c = CString::new(path).unwrap();

    let mtime = timespec {
        tv_sec: (new_mtime_ms / 1000) as _,
        tv_nsec: ((new_mtime_ms % 1000) * 1_000_000) as _,
    };
    let atime = timespec {
        tv_sec: (new_atime_ms / 1000) as _,
        tv_nsec: ((new_atime_ms % 1000) * 1_000_000) as _,
    };

    let ret = unsafe {
        utimensat(
            AT_FDCWD,
            path_c.as_ptr(),
            &[atime, mtime] as *const timespec,
            0,
        )
    };
    (ret == 0) as jboolean
}