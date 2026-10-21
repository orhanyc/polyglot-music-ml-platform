#include "music/ranker.hpp"

#include <algorithm>
#include <limits>
#include <stdexcept>
#include <unordered_map>

namespace music {

HybridRanker::HybridRanker(FeatureWeights weights)
    : weights_(weights.normalized()) {
}

std::vector<double> HybridRanker::relevance_scores(const FeatureMatrix& features) const {
    const auto weights = weights_.as_vector();
    if (features.columns() != weights.size()) {
        throw std::invalid_argument("Feature matrix width does not match ranking weights");
    }
    std::vector<double> scores;
    scores.reserve(features.rows());
    for (std::size_t row_index = 0; row_index < features.rows(); ++row_index) {
        const double linear_score = dot(features.row(row_index), weights);
        scores.push_back(sigmoid((linear_score - 0.5) * 4.0));
    }
    return scores;
}

std::vector<RankedItem> HybridRanker::rank(
    const FeatureMatrix& features,
    const std::vector<Candidate>& candidates,
    const RankingOptions& options) const {
    if (features.rows() != candidates.size()) {
        throw std::invalid_argument("Each candidate requires one feature row");
    }
    const auto relevance = relevance_scores(features);
    std::vector<std::size_t> remaining(candidates.size());
    for (std::size_t index = 0; index < remaining.size(); ++index) {
        remaining[index] = index;
    }

    std::vector<Candidate> selected_candidates;
    std::vector<RankedItem> selected;
    selected.reserve(std::min(options.limit, candidates.size()));

    while (!remaining.empty() && selected.size() < options.limit) {
        double best_score = -std::numeric_limits<double>::infinity();
        double best_diversity = 0.0;
        std::size_t best_position = remaining.size();

        for (std::size_t position = 0; position < remaining.size(); ++position) {
            const std::size_t index = remaining[position];
            const Candidate& candidate = candidates[index];
            if (!satisfies_constraints(candidate, selected_candidates, options)) {
                continue;
            }
            const double diversity = diversity_bonus(candidate, selected_candidates);
            const double exploration = candidate.exploration ? 1.0 : 0.0;
            const double relevance_weight = clamp(
                1.0 - options.diversity_weight - options.exploration_weight,
                0.0,
                1.0);
            const double score = relevance_weight * relevance[index]
                + options.diversity_weight * diversity
                + options.exploration_weight * exploration;
            if (score > best_score) {
                best_score = score;
                best_diversity = diversity;
                best_position = position;
            }
        }

        if (best_position == remaining.size()) {
            break;
        }
        const std::size_t winner = remaining[best_position];
        selected.push_back(RankedItem{
            candidates[winner].track_id,
            relevance[winner],
            best_diversity,
            best_score,
        });
        selected_candidates.push_back(candidates[winner]);
        remaining.erase(remaining.begin() + static_cast<std::ptrdiff_t>(best_position));
    }
    return selected;
}

double HybridRanker::diversity_bonus(
    const Candidate& candidate,
    const std::vector<Candidate>& selected) const {
    const auto artist_count = std::count_if(
        selected.begin(),
        selected.end(),
        [&candidate](const Candidate& item) { return item.artist == candidate.artist; });
    const auto genre_count = std::count_if(
        selected.begin(),
        selected.end(),
        [&candidate](const Candidate& item) { return item.genre == candidate.genre; });
    const double artist_novelty = 1.0 / (static_cast<double>(artist_count) + 1.0);
    const double genre_novelty = 1.0 / (static_cast<double>(genre_count) + 1.0);
    return 0.65 * artist_novelty + 0.35 * genre_novelty;
}

bool HybridRanker::satisfies_constraints(
    const Candidate& candidate,
    const std::vector<Candidate>& selected,
    const RankingOptions& options) const {
    const auto artist_count = std::count_if(
        selected.begin(),
        selected.end(),
        [&candidate](const Candidate& item) { return item.artist == candidate.artist; });
    const auto genre_count = std::count_if(
        selected.begin(),
        selected.end(),
        [&candidate](const Candidate& item) { return item.genre == candidate.genre; });
    return static_cast<std::size_t>(artist_count) < options.max_per_artist
        && static_cast<std::size_t>(genre_count) < options.max_per_genre;
}

}
