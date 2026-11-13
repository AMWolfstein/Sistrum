#[cfg(target_arch = "x86_64")]
#[no_mangle]
pub extern "C" fn sistrum_rust_export(value: i32) -> i32 {
    value + 1
}

#[uniffi::export]
pub fn uniffi_export(value: i32) -> i32 {
    value
}
