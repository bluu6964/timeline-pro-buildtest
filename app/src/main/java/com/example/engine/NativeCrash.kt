package com.example.engine

/** Build 228: installs the native crash logger (cpp/engine/crash_handler.cpp). */
object NativeCrash {
    @JvmStatic external fun install(path: String): Boolean

    /** Safe to call when the library is missing; returns false then. */
    fun installTo(path: String): Boolean = try {
        System.loadLibrary("am_engine")
        install(path)
    } catch (_: Throwable) {
        false
    }
}
