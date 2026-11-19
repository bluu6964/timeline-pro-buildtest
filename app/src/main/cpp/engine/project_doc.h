#pragma once

#include <cstddef>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

namespace am {

// Step 2A: Native Document, Bounded History Buffer & Atomic Storage.

/**
 * Atomic crash-safe file writer using POSIX open/write/fsync/rename:
 * writes `<targetPath>.tmp`, fsyncs data, rotates existing target to `<targetPath>.bak`,
 * and atomically renames `.tmp` into place.
 */
bool atomicWriteFile(const std::string& targetPath, const uint8_t* data, size_t size);

/** Fast POSIX whole-file reader into a byte vector. */
bool readWholeFile(const std::string& path, std::vector<uint8_t>& out);

/**
 * Thread-safe bounded LIFO history ring buffer storing serialized snapshot blobs
 * off the JVM heap. Evicts oldest entries once size exceeds `capacity`.
 */
class NativeHistoryBuffer {
public:
    explicit NativeHistoryBuffer(int capacity);

    void push(const uint8_t* data, size_t size);
    bool pop(std::vector<uint8_t>& out);
    int size() const;
    int capacity() const { return capacity_; }
    int64_t totalBytes() const;
    void clear();

private:
    int capacity_;
    mutable std::mutex mu_;
    std::deque<std::vector<uint8_t>> entries_;
    int64_t totalBytes_ = 0;
};

/**
 * Exact LaneLayout.placeInChain non-overlapping slot solver:
 * sorts sibling intervals by startMs and advances desiredStart past any overlap.
 */
int64_t solveChainPlacement(
    const int64_t* siblingStarts,
    const int64_t* siblingEnds,
    int count,
    int64_t desiredStartMs,
    int64_t durationMs);

} // namespace am
