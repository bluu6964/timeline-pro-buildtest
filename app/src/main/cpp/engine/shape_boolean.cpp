#include "shape_boolean.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <unordered_map>

namespace am {
namespace shape_bool {

namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr double kTwoPi = 2.0 * kPi;

inline double cross(const Pt& u, const Pt& v) { return u.x * v.y - u.y * v.x; }
inline Pt sub(const Pt& a, const Pt& b) { return {a.x - b.x, a.y - b.y}; }
inline double dist(const Pt& a, const Pt& b) { return std::hypot(a.x - b.x, a.y - b.y); }
inline bool ptLess(const Pt& p, const Pt& q) {
    return p.x < q.x || (p.x == q.x && p.y < q.y);
}

inline bool evalOp(BoolOp op, bool inA, bool inB) {
    switch (op) {
        case kUnion: return inA || inB;
        case kSubtract: return inA && !inB;
        case kIntersect: return inA && inB;
        case kXor: return inA != inB;
    }
    return false;
}

struct PtKey {
    uint64_t xb;
    uint64_t yb;
    bool operator==(const PtKey& o) const { return xb == o.xb && yb == o.yb; }
};

struct PtKeyHash {
    size_t operator()(const PtKey& k) const {
        return static_cast<size_t>(k.xb * 11400714819323198485ull ^ (k.yb >> 1));
    }
};

PtKey makePtKey(const Pt& p) {
    const double cx = (p.x == 0.0) ? 0.0 : p.x;
    const double cy = (p.y == 0.0) ? 0.0 : p.y;
    uint64_t xb = 0, yb = 0;
    std::memcpy(&xb, &cx, sizeof(uint64_t));
    std::memcpy(&yb, &cy, sizeof(uint64_t));
    return {xb, yb};
}

class VertexPool {
public:
    int intern(const Pt& p) {
        const Pt canon{(p.x == 0.0) ? 0.0 : p.x, (p.y == 0.0) ? 0.0 : p.y};
        const PtKey key = makePtKey(canon);
        auto it = map_.find(key);
        if (it != map_.end()) return it->second;
        const int id = static_cast<int>(verts_.size());
        verts_.push_back(canon);
        map_.emplace(key, id);
        return id;
    }

    const Pt& operator[](int id) const { return verts_[static_cast<size_t>(id)]; }
    int size() const { return static_cast<int>(verts_.size()); }

private:
    std::vector<Pt> verts_;
    std::unordered_map<PtKey, int, PtKeyHash> map_;
};

struct Edge {
    int a = 0;
    int b = 0;
    std::vector<std::pair<double, int>> cuts;

    Edge(int va, int vb) : a(va), b(vb) {
        cuts.reserve(4);
        cuts.emplace_back(0.0, va);
        cuts.emplace_back(1.0, vb);
    }
};

struct Sub {
    int a = 0;
    int b = 0;
};

double extentOf(const Region& a, const Region& b) {
    double minX = std::numeric_limits<double>::infinity();
    double minY = std::numeric_limits<double>::infinity();
    double maxX = -std::numeric_limits<double>::infinity();
    double maxY = -std::numeric_limits<double>::infinity();
    auto scan = [&](const Region& reg) {
        for (const auto& ring : reg) {
            for (const auto& p : ring) {
                minX = std::min(minX, p.x);
                minY = std::min(minY, p.y);
                maxX = std::max(maxX, p.x);
                maxY = std::max(maxY, p.y);
            }
        }
    };
    scan(a);
    scan(b);
    if (minX > maxX) return 1.0;
    return std::max(1.0, std::hypot(maxX - minX, maxY - minY));
}

Region cleanRegion(const Region& reg, double tol) {
    Region out;
    out.reserve(reg.size());
    for (const auto& ring : reg) {
        Ring pts;
        pts.reserve(ring.size());
        for (const auto& p : ring) {
            if (pts.empty() || dist(pts.back(), p) > tol) pts.push_back(p);
        }
        while (pts.size() > 1 && dist(pts.front(), pts.back()) <= tol) pts.pop_back();
        if (pts.size() >= 3) out.push_back(std::move(pts));
    }
    return out;
}

bool cleanRing(Ring& pts, double tol, double minArea) {
    if (pts.size() < 3) return false;
    bool changed = true;
    while (changed && pts.size() > 3) {
        changed = false;
        size_t i = 0;
        while (i < pts.size() && pts.size() > 3) {
            const Pt& prev = pts[(i + pts.size() - 1) % pts.size()];
            const Pt& cur = pts[i];
            const Pt& next = pts[(i + 1) % pts.size()];
            const Pt u = sub(cur, prev);
            const Pt v = sub(next, cur);
            const double lu = std::hypot(u.x, u.y);
            const double lv = std::hypot(v.x, v.y);
            const bool straight = lu > 0.0 && lv > 0.0 &&
                std::fabs(cross(u, v)) <= 1e-9 * lu * lv && (u.x * v.x + u.y * v.y) > 0.0;
            if (straight || dist(cur, next) <= tol) {
                pts.erase(pts.begin() + static_cast<ptrdiff_t>(i));
                changed = true;
            } else {
                ++i;
            }
        }
    }
    if (pts.size() < 3) return false;
    if (std::fabs(signedArea(pts)) <= minArea) return false;
    return true;
}

Region outputOrEmpty(const Region& reg, double minArea) {
    Region out;
    out.reserve(reg.size());
    for (Ring r : reg) {
        if (cleanRing(r, 0.0, minArea)) out.push_back(std::move(r));
    }
    return out;
}

void splitPair(Edge& e1, Edge& e2, VertexPool& pool, double tol) {
    const Pt p1a = pool[e1.a];
    const Pt p1b = pool[e1.b];
    const Pt p2a = pool[e2.a];
    const Pt p2b = pool[e2.b];
    const Pt r = sub(p1b, p1a);
    const Pt s = sub(p2b, p2a);
    const double lr = std::hypot(r.x, r.y);
    const double ls = std::hypot(s.x, s.y);
    if (lr <= 0.0 || ls <= 0.0) return;

    const double denom = cross(r, s);
    const Pt qp = sub(p2a, p1a);
    if (std::fabs(denom) > 1e-12 * lr * ls) {
        const double t = cross(qp, s) / denom;
        const double u = cross(qp, r) / denom;
        const double tt = tol / lr;
        const double uu = tol / ls;
        if (t < -tt || t > 1.0 + tt || u < -uu || u > 1.0 + uu) return;
        const double tc = std::clamp(t, 0.0, 1.0);
        const double uc = std::clamp(u, 0.0, 1.0);
        int vid = -1;
        if (tc == 0.0) vid = e1.a;
        else if (tc == 1.0) vid = e1.b;
        else if (uc == 0.0) vid = e2.a;
        else if (uc == 1.0) vid = e2.b;
        else vid = pool.intern({p1a.x + r.x * tc, p1a.y + r.y * tc});
        e1.cuts.emplace_back(tc, vid);
        e2.cuts.emplace_back(uc, vid);
        return;
    }

    if (std::fabs(cross(qp, r)) > tol * lr) return;
    const double lr2 = lr * lr;
    const double tc = (qp.x * r.x + qp.y * r.y) / lr2;
    const double td = tc + (s.x * r.x + s.y * r.y) / lr2;
    const double lo = std::max(0.0, std::min(tc, td));
    const double hi = std::min(1.0, std::max(tc, td));
    if (hi - lo <= tol / lr) return;

    auto atE1 = [&](double t) -> int {
        if (t == 0.0) return e1.a;
        if (t == 1.0) return e1.b;
        if (t == tc) return e2.a;
        if (t == td) return e2.b;
        return pool.intern({p1a.x + r.x * t, p1a.y + r.y * t});
    };
    e1.cuts.emplace_back(lo, atE1(lo));
    e1.cuts.emplace_back(hi, atE1(hi));

    const double ls2 = ls * ls;
    const double u1 = ((p1a.x - p2a.x) * s.x + (p1a.y - p2a.y) * s.y) / ls2;
    const double u2 = ((p1b.x - p2a.x) * s.x + (p1b.y - p2a.y) * s.y) / ls2;
    const double ulo = std::max(0.0, std::min(u1, u2));
    const double uhi = std::min(1.0, std::max(u1, u2));
    auto atE2 = [&](double u) -> int {
        if (u == 0.0) return e2.a;
        if (u == 1.0) return e2.b;
        if (u == u1) return e1.a;
        if (u == u2) return e1.b;
        return pool.intern({p2a.x + s.x * u, p2a.y + s.y * u});
    };
    if (uhi - ulo > tol / ls) {
        e2.cuts.emplace_back(ulo, atE2(ulo));
        e2.cuts.emplace_back(uhi, atE2(uhi));
    }
}

int chooseNext(int v, const Sub& incoming, const VertexPool& pool,
               const std::vector<std::vector<int>>& outgoing,
               const std::vector<Sub>& kept, const std::vector<uint8_t>& used) {
    const auto& cands = outgoing[static_cast<size_t>(v)];
    if (cands.empty()) return -1;
    const Pt din = sub(pool[incoming.b], pool[incoming.a]);
    const double rev = std::atan2(-din.y, -din.x);
    int best = -1;
    double bestScore = std::numeric_limits<double>::infinity();
    double bestLen = -1.0;
    for (int ci : cands) {
        if (used[static_cast<size_t>(ci)]) continue;
        const Sub& c = kept[static_cast<size_t>(ci)];
        const Pt d = sub(pool[c.b], pool[c.a]);
        const double ang = std::atan2(d.y, d.x);
        double score = std::fmod(rev - ang, kTwoPi);
        if (score <= 0.0) score += kTwoPi;
        const double len = std::hypot(d.x, d.y);
        if (score < bestScore - 1e-12 ||
            (std::fabs(score - bestScore) <= 1e-12 && len > bestLen)) {
            best = ci;
            bestScore = score;
            bestLen = len;
        }
    }
    return best;
}

}  // namespace

double signedArea(const Ring& ring) {
    const size_t n = ring.size();
    if (n < 2) return 0.0;
    double s = 0.0;
    for (size_t i = 0; i < n; ++i) {
        const Pt& a = ring[i];
        const Pt& b = ring[(i + 1) % n];
        s += a.x * b.y - b.x * a.y;
    }
    return s * 0.5;
}

int windingNumber(const Pt& p, const Region& region) {
    int wn = 0;
    for (const auto& ring : region) {
        const size_t n = ring.size();
        if (n < 2) continue;
        for (size_t i = 0; i < n; ++i) {
            const Pt& a = ring[i];
            const Pt& b = ring[(i + 1) % n];
            if (a.y <= p.y) {
                if (b.y > p.y && cross(sub(b, a), sub(p, a)) > 0.0) ++wn;
            } else {
                if (b.y <= p.y && cross(sub(b, a), sub(p, a)) < 0.0) --wn;
            }
        }
    }
    return wn;
}

Region combine(const Region& a, const Region& b, BoolOp op) {
    const double extent = extentOf(a, b);
    const double tol = 1e-9 * std::max(1.0, extent);
    const double minArea = (1e-6 * extent) * (1e-6 * extent);
    const Region ra = cleanRegion(a, tol);
    const Region rb = cleanRegion(b, tol);
    if (ra.empty() || rb.empty()) {
        switch (op) {
            case kUnion: return ra.empty() ? outputOrEmpty(rb, minArea) : outputOrEmpty(ra, minArea);
            case kIntersect: return {};
            case kSubtract: return rb.empty() ? outputOrEmpty(ra, minArea) : Region{};
            case kXor: return ra.empty() ? outputOrEmpty(rb, minArea) : outputOrEmpty(ra, minArea);
        }
    }

    VertexPool pool;
    std::vector<Edge> edges;
    auto addRegionEdges = [&](const Region& reg) {
        for (const auto& ring : reg) {
            std::vector<int> ids;
            ids.reserve(ring.size());
            for (const auto& p : ring) ids.push_back(pool.intern(p));
            const size_t n = ids.size();
            for (size_t i = 0; i < n; ++i) {
                const int p = ids[i];
                const int q = ids[(i + 1) % n];
                if (p != q) edges.emplace_back(p, q);
            }
        }
    };
    addRegionEdges(ra);
    addRegionEdges(rb);

    const size_t ec = edges.size();
    for (size_t i = 0; i < ec; ++i) {
        for (size_t j = i + 1; j < ec; ++j) {
            splitPair(edges[i], edges[j], pool, tol);
        }
    }

    std::unordered_map<uint64_t, bool> seenSubs;
    std::vector<Sub> subs;
    subs.reserve(ec * 2);
    for (auto& e : edges) {
        std::stable_sort(e.cuts.begin(), e.cuts.end(),
                         [](const std::pair<double, int>& x, const std::pair<double, int>& y) {
                             return x.first < y.first;
                         });
        std::vector<int> uniq;
        uniq.reserve(e.cuts.size());
        for (const auto& c : e.cuts) {
            if (uniq.empty() || dist(pool[uniq.back()], pool[c.second]) > 0.0) {
                uniq.push_back(c.second);
            }
        }
        for (size_t s = 0; s + 1 < uniq.size(); ++s) {
            const int p = uniq[s];
            const int q = uniq[s + 1];
            if (p == q) continue;
            if (dist(pool[p], pool[q]) <= tol) continue;
            const bool pFirst = ptLess(pool[p], pool[q]);
            const uint32_t k0 = static_cast<uint32_t>(pFirst ? p : q);
            const uint32_t k1 = static_cast<uint32_t>(pFirst ? q : p);
            const uint64_t key = (static_cast<uint64_t>(k0) << 32) | static_cast<uint64_t>(k1);
            if (seenSubs.emplace(key, true).second) {
                subs.push_back({p, q});
            }
        }
    }

    std::vector<Sub> kept;
    kept.reserve(subs.size());
    const double deltaBase = 0.001 * std::max(1.0, extent / 1000.0);
    for (const Sub& s : subs) {
        const Pt pa = pool[s.a];
        const Pt pb = pool[s.b];
        const Pt d = sub(pb, pa);
        const double len = std::hypot(d.x, d.y);
        if (len <= tol) continue;
        const double delta = std::min(len * 0.25, deltaBase);
        const double nx = -d.y / len;
        const double ny = d.x / len;
        const Pt mid{(pa.x + pb.x) * 0.5, (pa.y + pb.y) * 0.5};
        const Pt leftPt{mid.x + nx * delta, mid.y + ny * delta};
        const Pt rightPt{mid.x - nx * delta, mid.y - ny * delta};
        const bool inL = evalOp(op, windingNumber(leftPt, ra) != 0, windingNumber(leftPt, rb) != 0);
        const bool inR = evalOp(op, windingNumber(rightPt, ra) != 0, windingNumber(rightPt, rb) != 0);
        if (inL == inR) continue;
        kept.push_back(inL ? s : Sub{s.b, s.a});
    }

    std::vector<std::vector<int>> outgoing(static_cast<size_t>(pool.size()));
    for (int i = 0; i < static_cast<int>(kept.size()); ++i) {
        outgoing[static_cast<size_t>(kept[static_cast<size_t>(i)].a)].push_back(i);
    }

    std::vector<uint8_t> used(kept.size(), 0);
    Region out;
    for (int fi = 0; fi < static_cast<int>(kept.size()); ++fi) {
        if (used[static_cast<size_t>(fi)]) continue;
        Ring ring;
        int curIdx = fi;
        const int startV = kept[static_cast<size_t>(fi)].a;
        bool closed = false;
        int guard = static_cast<int>(kept.size()) + 1;
        while (guard-- > 0) {
            used[static_cast<size_t>(curIdx)] = 1;
            const Sub& cur = kept[static_cast<size_t>(curIdx)];
            ring.push_back(pool[cur.a]);
            const int v = cur.b;
            if (v == startV && ring.size() >= 3) {
                closed = true;
                break;
            }
            const int next = chooseNext(v, cur, pool, outgoing, kept, used);
            if (next < 0) break;
            curIdx = next;
        }
        if (closed && cleanRing(ring, tol, minArea)) {
            out.push_back(std::move(ring));
        }
    }
    return out;
}

Region flattenPathRings(const int32_t* ops, const int32_t* offsets, const int32_t* counts,
                        int segCount, const float* pts, int ptsLen, int stepsPerCurve) {
    Region rings;
    if (!ops || !offsets || !counts || segCount <= 0) return rings;
    const int steps = std::max(1, stepsPerCurve);
    Ring ring;
    Pt cur{0.0, 0.0};
    Pt start = cur;

    auto flush = [&]() {
        while (ring.size() > 1 &&
               ring.back().x == ring.front().x && ring.back().y == ring.front().y) {
            ring.pop_back();
        }
        if (ring.size() >= 2) rings.push_back(std::move(ring));
        ring.clear();
    };
    auto quad = [](const Pt& p0, const Pt& p1, const Pt& p2, double t) -> Pt {
        const double u = 1.0 - t;
        return {u * u * p0.x + 2.0 * u * t * p1.x + t * t * p2.x,
                u * u * p0.y + 2.0 * u * t * p1.y + t * t * p2.y};
    };
    auto cubic = [](const Pt& p0, const Pt& p1, const Pt& p2, const Pt& p3, double t) -> Pt {
        const double u = 1.0 - t;
        const double u2 = u * u, t2 = t * t;
        return {u2 * u * p0.x + 3.0 * u2 * t * p1.x + 3.0 * u * t2 * p2.x + t2 * t * p3.x,
                u2 * u * p0.y + 3.0 * u2 * t * p1.y + 3.0 * u * t2 * p2.y + t2 * t * p3.y};
    };

    for (int s = 0; s < segCount; ++s) {
        const int op = ops[s];
        const int off = offsets[s];
        const int cnt = counts[s];
        if (off < 0 || cnt < 0 || off + cnt > ptsLen) continue;
        const float* q = pts ? (pts + off) : nullptr;
        switch (op) {
            case 0:  // MOVE
                if (q && cnt >= 2) {
                    flush();
                    cur = {static_cast<double>(q[0]), static_cast<double>(q[1])};
                    start = cur;
                    ring.push_back(cur);
                }
                break;
            case 1:  // LINE
                if (q && cnt >= 2) {
                    cur = {static_cast<double>(q[0]), static_cast<double>(q[1])};
                    ring.push_back(cur);
                }
                break;
            case 2:  // QUAD
                if (q && cnt >= 4) {
                    const Pt c{static_cast<double>(q[0]), static_cast<double>(q[1])};
                    const Pt e{static_cast<double>(q[2]), static_cast<double>(q[3])};
                    for (int i = 1; i <= steps; ++i) {
                        ring.push_back(quad(cur, c, e, static_cast<double>(i) / steps));
                    }
                    cur = e;
                }
                break;
            case 3:  // CUBIC
                if (q && cnt >= 6) {
                    const Pt c1{static_cast<double>(q[0]), static_cast<double>(q[1])};
                    const Pt c2{static_cast<double>(q[2]), static_cast<double>(q[3])};
                    const Pt e{static_cast<double>(q[4]), static_cast<double>(q[5])};
                    for (int i = 1; i <= steps; ++i) {
                        ring.push_back(cubic(cur, c1, c2, e, static_cast<double>(i) / steps));
                    }
                    cur = e;
                }
                break;
            case 4:  // CLOSE
                ring.push_back(start);
                cur = start;
                break;
            default:
                break;
        }
    }
    flush();
    return rings;
}

}  // namespace shape_bool
}  // namespace am
