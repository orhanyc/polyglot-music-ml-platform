#pragma once

#include "music/feature_matrix.hpp"

#include <cstddef>
#include <string>
#include <vector>

namespace music {

struct Candidate {
    std::string track_id;
    std::string artist;
    std::string genre;
    bool exploration{false};
};

struct RankedItem {
    std::string track_id;
    double relevance{0.0};
    double diversity{0.0};
    double score{0.0};
};

struct RankingOptions {
    std::size_t limit{10};
    double diversity_weight{0.35};
    double exploration_weight{0.10};
    std::size_t max_per_artist{1};
    std::size_t max_per_genre{3};
};

class HybridRanker {
public:
    explicit HybridRanker(FeatureWeights weights = {});

    [[nodiscard]] std::vector<RankedItem> rank(
        const FeatureMatrix& features,
        const std::vector<Candidate>& candidates,
        const RankingOptions& options) const;

    [[nodiscard]] std::vector<double> relevance_scores(const FeatureMatrix& features) const;

private:
    [[nodiscard]] double diversity_bonus(
        const Candidate& candidate,
        const std::vector<Candidate>& selected) const;

    [[nodiscard]] bool satisfies_constraints(
        const Candidate& candidate,
        const std::vector<Candidate>& selected,
        const RankingOptions& options) const;

    FeatureWeights weights_;
};

}

