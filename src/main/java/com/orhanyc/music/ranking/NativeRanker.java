package com.orhanyc.music.ranking;

import com.orhanyc.music.model.RecommendationModels;
import com.orhanyc.music.model.Track;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NativeRanker {
    private static final String LIBRARY_NAME = "music_ranker";
    private final AtomicBoolean nativeAvailable = new AtomicBoolean(false);

    public NativeRanker() {
        try {
            System.loadLibrary(LIBRARY_NAME);
            nativeAvailable.set(true);
        } catch (UnsatisfiedLinkError ignored) {
            nativeAvailable.set(false);
        }
    }

    public List<RecommendationModels.Item> rank(
            List<CandidateGenerator.Candidate> candidates,
            List<Map<String, Double>> features,
            int limit,
            double diversityWeight,
            double explorationWeight) {
        if (nativeAvailable.get()) {
            try {
                return rankNative(candidates, features, limit, diversityWeight, explorationWeight);
            } catch (RuntimeException error) {
                nativeAvailable.set(false);
            }
        }
        return rankJava(candidates, features, limit, diversityWeight, explorationWeight);
    }

    public boolean isNativeAvailable() {
        return nativeAvailable.get();
    }

    private List<RecommendationModels.Item> rankNative(
            List<CandidateGenerator.Candidate> candidates,
            List<Map<String, Double>> features,
            int limit,
            double diversityWeight,
            double explorationWeight) {
        int rows = candidates.size();
        int columns = FeatureOrder.NAMES.size();
        double[] matrix = new double[rows * columns];
        String[] ids = new String[rows];
        String[] artists = new String[rows];
        String[] genres = new String[rows];
        boolean[] exploration = new boolean[rows];

        for (int row = 0; row < rows; row++) {
            var candidate = candidates.get(row);
            ids[row] = candidate.track().id();
            artists[row] = candidate.track().artist();
            genres[row] = candidate.track().genre();
            exploration[row] = candidate.source().equals("exploration");
            for (int column = 0; column < columns; column++) {
                matrix[row * columns + column] = features.get(row)
                        .getOrDefault(FeatureOrder.NAMES.get(column), 0.0);
            }
        }

        NativeResult result = nativeRank(
                matrix,
                rows,
                columns,
                ids,
                artists,
                genres,
                exploration,
                limit,
                diversityWeight,
                explorationWeight);
        return toItems(result, candidates, features);
    }

    private List<RecommendationModels.Item> rankJava(
            List<CandidateGenerator.Candidate> candidates,
            List<Map<String, Double>> features,
            int limit,
            double diversityWeight,
            double explorationWeight) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble((Integer index) -> linearScore(features.get(index))).reversed());
        List<RecommendationModels.Item> items = new ArrayList<>();
        for (int rank = 0; rank < Math.min(limit, order.size()); rank++) {
            int index = order.get(rank);
            Track track = candidates.get(index).track();
            double relevance = linearScore(features.get(index));
            items.add(new RecommendationModels.Item(
                    track,
                    rank + 1,
                    relevance,
                    0.0,
                    relevance,
                    "Java fallback ranking",
                    features.get(index)));
        }
        return items;
    }

    private List<RecommendationModels.Item> toItems(
            NativeResult result,
            List<CandidateGenerator.Candidate> candidates,
            List<Map<String, Double>> features) {
        Map<String, Integer> indexes = new java.util.HashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            indexes.put(candidates.get(index).track().id(), index);
        }
        List<RecommendationModels.Item> items = new ArrayList<>();
        for (int rank = 0; rank < result.trackIds().length; rank++) {
            Integer index = indexes.get(result.trackIds()[rank]);
            if (index == null) {
                continue;
            }
            Track track = candidates.get(index).track();
            items.add(new RecommendationModels.Item(
                    track,
                    rank + 1,
                    result.relevance()[rank],
                    result.diversity()[rank],
                    result.scores()[rank],
                    "Native relevance and diversity ranking",
                    features.get(index)));
        }
        return items;
    }

    private double linearScore(Map<String, Double> features) {
        return features.getOrDefault("genre", 0.0) * 0.25
                + features.getOrDefault("energy", 0.0) * 0.15
                + features.getOrDefault("popularity", 0.0) * 0.10
                + features.getOrDefault("language", 0.0) * 0.05
                + features.getOrDefault("affinity", 0.0) * 0.25
                + features.getOrDefault("freshness", 0.0) * 0.08
                + features.getOrDefault("completion", 0.0) * 0.07
                + features.getOrDefault("quality", 0.0) * 0.05;
    }

    private native NativeResult nativeRank(
            double[] matrix,
            int rows,
            int columns,
            String[] ids,
            String[] artists,
            String[] genres,
            boolean[] exploration,
            int limit,
            double diversityWeight,
            double explorationWeight);

    public record NativeResult(
            String[] trackIds,
            double[] relevance,
            double[] diversity,
            double[] scores) {
    }

    private static final class FeatureOrder {
        private static final List<String> NAMES = List.of(
                "genre",
                "energy",
                "popularity",
                "language",
                "affinity",
                "freshness",
                "completion",
                "quality");
    }
}
