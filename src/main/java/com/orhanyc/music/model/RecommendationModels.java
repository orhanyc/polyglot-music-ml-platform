package com.orhanyc.music.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class RecommendationModels {
    private RecommendationModels() {
    }

    public record Request(
            @Valid @NotNull ListenerProfile profile,
            ListeningEvent.Surface surface,
            @Min(1) @Max(50) int limit,
            @DecimalMin("0.0") @DecimalMax("1.0") double diversityWeight,
            @DecimalMin("0.0") @DecimalMax("0.5") double explorationWeight,
            String requestId) {

        public Request {
            surface = surface == null ? ListeningEvent.Surface.HOME : surface;
            limit = limit == 0 ? 10 : limit;
            requestId = requestId == null ? "" : requestId;
        }
    }

    public record Item(
            Track track,
            int rank,
            double relevance,
            double diversityBonus,
            double finalScore,
            String reason,
            Map<String, Double> features) {

        public Item {
            features = Map.copyOf(features);
        }
    }

    public record Response(
            String requestId,
            String modelVersion,
            List<Item> recommendations,
            double latencyMs,
            boolean cacheHit,
            String experimentVariant) {

        public Response {
            recommendations = List.copyOf(recommendations);
        }
    }
}
