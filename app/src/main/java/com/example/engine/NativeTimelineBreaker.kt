package com.example.engine

import android.content.Context

/**
 * Build 227 — crash-loop breaker for the native timeline (build 224).
 *
 * Build 226 died on Sarowar's phone with a native crash (SIGSEGV) in the
 * 224 native-timeline path. The lifecycle races behind it are fixed in
 * NativeTimelineEngine / timeline_jni.cpp, but a native crash must NEVER be
 * able to lock him out of his editor in a crash loop: if the app dies while
 * the native timeline surface is up, the next editor start renders the
 * Compose timeline for that session instead — no toggle hunting, no
 * reinstall. His explicit "Native timeline" preference is left untouched;
 * the breaker only overrides the session's initial value once, and the
 * options-sheet toggle (which displays the same state) then shows the
 * effective OFF. Turning it back on from the sheet is a deliberate override
 * and re-arms the breaker normally.
 *
 * Protocol:
 *  - [arm] is called when the native timeline surface is created. It uses
 *    commit(): the flag must be durable BEFORE the risky surface work
 *    continues — apply()'s asynchronous disk write can be lost when a
 *    SIGSEGV kills the process.
 *  - [disarm] is called once the surface has been alive ~10 s (a native
 *    lifecycle crash strikes at creation / first frames, not minutes in)
 *    and again on clean editor exit.
 *  - [initialNativeOn] runs at editor start: if the armed flag is still
 *    set, the previous session died with the surface up — consume (clear)
 *    the flag and start this session with the native timeline OFF.
 */
internal object NativeTimelineBreaker {
    private const val PREFS = "editor"
    private const val KEY_PREF = "native_timeline"
    private const val KEY_ARMED = "native_timeline_armed"

    /** The effective initial value for the editor's native-timeline state. */
    fun initialNativeOn(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefOn = prefs.getBoolean(KEY_PREF, true)
        val diedArmed = prefs.getBoolean(KEY_ARMED, false)
        if (diedArmed) prefs.edit().remove(KEY_ARMED).apply()
        return prefOn && !diedArmed
    }

    /** Mark "the native timeline surface is up" — synchronously durable. */
    fun arm(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ARMED, true).commit()
    }

    /** The surface survived (or the editor closed cleanly): stand down. */
    fun disarm(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ARMED).apply()
    }
}
