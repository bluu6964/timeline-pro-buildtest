// Build 228 - native crash logger. A SIGSEGV kills the process without a Java stack, so the
// CrashReport dialog could only say "native crash, status=11". This installs handlers that, on a
// fatal signal, write the signal, fault address, thread name and a backtrace (library + offset +
// exported symbol) to a text file, then hand the signal on to the previous handler (the system's
// own tombstone is untouched). CrashReport.kt shows the file on the next start.
// Installed through libsigchain (plain sigaction), so ART's own SIGSEGV handling runs first.
#include <signal.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <dlfcn.h>
#include <unwind.h>
#include <stdint.h>
#include <sys/prctl.h>
#include <ucontext.h>

namespace {

char g_path[512];
const int kSigs[] = {SIGSEGV, SIGBUS, SIGABRT, SIGFPE, SIGILL};
const int kNumSigs = 5;
struct sigaction g_old[5];
volatile sig_atomic_t g_busy = 0;
bool g_installed = false;

struct Out {
    char buf[6144];
    int n = 0;
    void put(const char* s) {
        while (s && *s && n < (int)sizeof(buf) - 1) buf[n++] = *s++;
    }
    void hex(uintptr_t v) {
        put("0x");
        char tmp[20];
        int k = 0;
        if (v == 0) tmp[k++] = '0';
        while (v && k < 20) { tmp[k++] = "0123456789abcdef"[v & 15]; v >>= 4; }
        while (k) { if (n < (int)sizeof(buf) - 1) buf[n++] = tmp[--k]; }
    }
    void dec(long v) {
        if (v < 0) { put("-"); v = -v; }
        char tmp[24];
        int k = 0;
        if (v == 0) tmp[k++] = '0';
        while (v && k < 24) { tmp[k++] = (char)('0' + v % 10); v /= 10; }
        while (k) { if (n < (int)sizeof(buf) - 1) buf[n++] = tmp[--k]; }
    }
};

struct Trace { uintptr_t pcs[40]; int n = 0; };

_Unwind_Reason_Code unwindStep(struct _Unwind_Context* ctx, void* arg) {
    Trace* t = (Trace*)arg;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc) {
        if (t->n >= 40) return _URC_END_OF_STACK;
        t->pcs[t->n++] = pc;
    }
    return _URC_NO_REASON;
}

const char* sigName(int s) {
    switch (s) {
        case SIGSEGV: return "SIGSEGV";
        case SIGBUS: return "SIGBUS";
        case SIGABRT: return "SIGABRT";
        case SIGFPE: return "SIGFPE";
        case SIGILL: return "SIGILL";
        default: return "SIG?";
    }
}

void flush(int fd, Out& o) {
    if (fd >= 0 && o.n > 0) {
        ssize_t w = write(fd, o.buf, (size_t)o.n);
        (void)w;
    }
    o.n = 0;
}

// One frame line: pc, library + offset, exported symbol (dladdr).
void frameLine(Out& o, int idx, uintptr_t pc, bool isReturnAddr) {
    o.put("  #");
    o.dec(idx);
    o.put(" pc=");
    o.hex(pc);
    Dl_info di;
    if (pc && dladdr((void*)(isReturnAddr ? pc - 1 : pc), &di)) {
        if (di.dli_fname) {
            const char* base = di.dli_fname;
            for (const char* p = base; *p; ++p) if (*p == '/') base = p + 1;
            o.put(" ");
            o.put(base);
            o.put("+");
            o.hex(pc - (uintptr_t)di.dli_fbase);
        }
        if (di.dli_sname) {
            o.put(" ");
            o.put(di.dli_sname);
            o.put("+");
            o.dec((long)(pc - (uintptr_t)di.dli_saddr));
        }
    }
    o.put("\n");
}

void handler(int sig, siginfo_t* si, void* uctx) {
    if (__sync_bool_compare_and_swap(&g_busy, 0, 1)) {
        int fd = open(g_path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
        Out o;
        o.put("Native crash: ");
        o.put(sigName(sig));
        o.put(" (");
        o.dec(sig);
        o.put(") code=");
        o.dec(si ? si->si_code : 0);
        o.put(" fault_addr=");
        o.hex(si ? (uintptr_t)si->si_addr : 0);
        o.put("\nThread: ");
        char name[32] = {0};
        prctl(PR_GET_NAME, (unsigned long)name, 0, 0, 0);
        o.put(name);
        o.put("\n");
        flush(fd, o);  // the header is on disk before anything risky happens
#if defined(__aarch64__)
        // The REAL crash site comes from the signal's saved registers (the unwinder started
        // inside this handler would only show the handler itself).
        if (uctx) {
            ucontext_t* uc = (ucontext_t*)uctx;
            uintptr_t pc = (uintptr_t)uc->uc_mcontext.pc;
            uintptr_t sp = (uintptr_t)uc->uc_mcontext.sp;
            uintptr_t fp = (uintptr_t)uc->uc_mcontext.regs[29];
            uintptr_t lr = (uintptr_t)uc->uc_mcontext.regs[30];
            o.put("Registers: sp=");
            o.hex(sp);
            o.put(" fp=");
            o.hex(fp);
            o.put(" lr=");
            o.hex(lr);
            o.put("\n");
            for (int i = 0; i < 31; ++i) {
                if (i % 4 == 0) o.put(i ? "\n  " : "  ");
                o.put("x");
                o.dec(i);
                o.put("=");
                o.hex((uintptr_t)uc->uc_mcontext.regs[i]);
                o.put(" ");
            }
            o.put("\nCrash site (frames from the saved registers):\n");
            frameLine(o, 0, pc, false);   // where it stopped (== fault_addr when it jumped to garbage)
            frameLine(o, 1, lr, true);    // who called / jumped
            flush(fd, o);
            // Frame-pointer walk: fp -> {previous fp, return address}. Bounded and checked.
            uintptr_t cur = fp;
            for (int i = 2; i < 24; ++i) {
                if (cur == 0 || (cur & 15) != 0 || cur < sp || cur - sp > (8u << 20)) break;
                uintptr_t* rec = (uintptr_t*)cur;
                uintptr_t next = rec[0], ret = rec[1];
                if (!ret) break;
                frameLine(o, i, ret, true);
                flush(fd, o);
                if (next <= cur) break;
                cur = next;
            }
        }
#else
        (void)uctx;
#endif
        o.put("Handler-side backtrace (usually only the handler itself):\n");
        Trace t;
        _Unwind_Backtrace(unwindStep, &t);
        for (int i = 0; i < t.n; ++i) frameLine(o, i, t.pcs[i], true);
        flush(fd, o);
        if (fd >= 0) close(fd);
    }
    // Hand over to whoever was installed before (the system default -> tombstone) and, for a
    // signal that is not a re-faulting hardware fault, send it again.
    for (int i = 0; i < kNumSigs; ++i)
        if (kSigs[i] == sig) sigaction(sig, &g_old[i], nullptr);
    if (!si || si->si_code <= 0 || sig == SIGABRT) raise(sig);
}

}  // namespace

extern "C" bool am_crash_install(const char* path) {
    if (!path || g_installed) return g_installed;
    size_t len = strlen(path);
    if (len == 0 || len >= sizeof(g_path)) return false;
    memcpy(g_path, path, len + 1);
    // Alternate stack for this thread, so a stack overflow can still be reported.
    static char altStack[32768];
    stack_t ss;
    ss.ss_sp = altStack;
    ss.ss_size = sizeof(altStack);
    ss.ss_flags = 0;
    sigaltstack(&ss, nullptr);
    for (int i = 0; i < kNumSigs; ++i) {
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_sigaction = handler;
        sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
        sigemptyset(&sa.sa_mask);
        sigaction(kSigs[i], &sa, &g_old[i]);
    }
    g_installed = true;
    return true;
}

#ifdef __ANDROID__
#include <jni.h>
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_engine_NativeCrash_install(
    JNIEnv* env, jclass, jstring path) {
    if (!path) return JNI_FALSE;
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) return JNI_FALSE;
    bool ok = am_crash_install(p);
    env->ReleaseStringUTFChars(path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}
#endif
