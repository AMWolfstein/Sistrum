package com.example

class NativeBridge {
    external fun openFile(path: String, flags: Int): Int
    external fun signatureMismatch(path: String, flags: Int): Int
    external fun missingSymbol(value: String): Int
    external fun protectedFallback(value: String): Int

    private fun fallback(value: String): Int = value.length
}
