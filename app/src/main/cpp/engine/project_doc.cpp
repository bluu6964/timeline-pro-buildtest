#include "project_doc.h"

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdio>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>

namespace am {
namespace {

bool writeAll(int fd, const uint8_t* data, size_t size) {
    size_t written = 0;
    while (written < size) {
        ssize_t n = ::write(fd, data + written, size - written);
        if (n < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        written += static_cast<size_t>(n);
    }
    return true;
}

void fsyncParentDir(const std::string& targetPath) {
    size_t slash = targetPath.find_last_of('/');
    if (slash == std::string::npos) return;
    std::string dirPath = (slash == 0) ? "/" : targetPath.substr(0, slash);
    int dfd = ::open(dirPath.c_str(), O_RDONLY | O_DIRECTORY);
    if (dfd >= 0) {
        (void)::fsync(dfd);
        ::close(dfd);
    }
}

} // namespace

bool atomicWriteFile(const std::string& targetPath, const uint8_t* data, size_t size) {
    if (targetPath.empty()) return false;
    std::string tmpPath = targetPath + ".tmp";
    std::string bakPath = targetPath + ".bak";

    int fd = ::open(tmpPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return false;

    bool ok = (size == 0 || data == nullptr) ? true : writeAll(fd, data, size);
    if (ok) {
        (void)::fsync(fd);
    }
    if (::close(fd) != 0) {
        ok = false;
    }
    if (!ok) {
        (void)::unlink(tmpPath.c_str());
        return false;
    }

    if (::access(targetPath.c_str(), F_OK) == 0) {
        (void)::unlink(bakPath.c_str());
        (void)::rename(targetPath.c_str(), bakPath.c_str());
    }

    if (::rename(tmpPath.c_str(), targetPath.c_str()) != 0) {
        // Fallback: copy tmp directly to target if rename failed across mount boundaries.
        int outFd = ::open(targetPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (outFd < 0) {
            (void)::unlink(tmpPath.c_str());
            return false;
        }
        bool copyOk = (size == 0 || data == nullptr) ? true : writeAll(outFd, data, size);
        if (copyOk) (void)::fsync(outFd);
        if (::close(outFd) != 0) copyOk = false;
        (void)::unlink(tmpPath.c_str());
        if (!copyOk) return false;
    }

    fsyncParentDir(targetPath);
    return true;
}

bool readWholeFile(const std::string& path, std::vector<uint8_t>& out) {
    out.clear();
    if (path.empty()) return false;
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;

    struct stat st {};
    if (::fstat(fd, &st) == 0 && st.st_size > 0) {
        out.reserve(static_cast<size_t>(st.st_size));
    }

    uint8_t buf[16384];
    for (;;) {
        ssize_t n = ::read(fd, buf, sizeof(buf));
        if (n < 0) {
            if (errno == EINTR) continue;
            ::close(fd);
            out.clear();
            return false;
        }
        if (n == 0) break;
        out.insert(out.end(), buf, buf + n);
    }
    ::close(fd);
    return true;
}

NativeHistoryBuffer::NativeHistoryBuffer(int capacity)
    : capacity_(capacity > 0 ? capacity : 1) {}

void NativeHistoryBuffer::push(const uint8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(mu_);
    std::vector<uint8_t> blob;
    if (data != nullptr && size > 0) {
        blob.assign(data, data + size);
    }
    totalBytes_ += static_cast<int64_t>(blob.size());
    entries_.push_back(std::move(blob));
    while (static_cast<int>(entries_.size()) > capacity_) {
        totalBytes_ -= static_cast<int64_t>(entries_.front().size());
        entries_.pop_front();
    }
}

bool NativeHistoryBuffer::pop(std::vector<uint8_t>& out) {
    std::lock_guard<std::mutex> lock(mu_);
    if (entries_.empty()) return false;
    out = std::move(entries_.back());
    totalBytes_ -= static_cast<int64_t>(out.size());
    entries_.pop_back();
    return true;
}

int NativeHistoryBuffer::size() const {
    std::lock_guard<std::mutex> lock(mu_);
    return static_cast<int>(entries_.size());
}

int64_t NativeHistoryBuffer::totalBytes() const {
    std::lock_guard<std::mutex> lock(mu_);
    return totalBytes_;
}

void NativeHistoryBuffer::clear() {
    std::lock_guard<std::mutex> lock(mu_);
    entries_.clear();
    totalBytes_ = 0;
}

int64_t solveChainPlacement(
    const int64_t* siblingStarts,
    const int64_t* siblingEnds,
    int count,
    int64_t desiredStartMs,
    int64_t durationMs) {
    int64_t start = std::max<int64_t>(0, desiredStartMs);
    if (count <= 0 || siblingStarts == nullptr || siblingEnds == nullptr) {
        return start;
    }
    std::vector<std::pair<int64_t, int64_t>> siblings;
    siblings.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        siblings.emplace_back(siblingStarts[i], siblingEnds[i]);
    }
    std::stable_sort(siblings.begin(), siblings.end(),
        [](const auto& a, const auto& b) { return a.first < b.first; });

    bool moved = true;
    int guard = 0;
    while (moved && guard++ < 500) {
        moved = false;
        for (const auto& s : siblings) {
            if (start < s.second && s.first < start + durationMs) {
                start = s.second;
                moved = true;
            }
        }
    }
    return start;
}

} // namespace am
