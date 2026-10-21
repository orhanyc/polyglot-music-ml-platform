package com.orhanyc.music.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orhanyc.music.experiment.ExperimentService;
import com.orhanyc.music.model.RecommendationModels;
import com.orhanyc.music.ranking.CandidateGenerator;
import com.orhanyc.music.ranking.NativeRanker;
import com.orhanyc.music.signals.EventRepository;
import com.orhanyc.music.signals.FeaturePipeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

@Service
public class RecommendationService implements AutoCloseable {
    private final CandidateGenerator generator;
    private final FeaturePipeline features;
    private final EventRepository events;
    private final NativeRanker ranker;
    private final ExperimentService experiments;
    private final Cache<String, RecommendationModels.Response> cache;
    private final ExecutorService executor;
    private final String modelVersion;
    private final int candidateLimit;
    private final LongAdder requests = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder errors = new LongAdder();

    public RecommendationService(
            CandidateGenerator generator,
            FeaturePipeline features,
            EventRepository events,
            NativeRanker ranker,
            ExperimentService experiments,
            @Value("${music.model-version:hybrid-native-v1}") String modelVersion,
            @Value("${music.candidate-limit:200}") int candidateLimit,
            @Value("${music.cache-seconds:120}") int cacheSeconds) {
        this.generator = generator;
        this.features = features;
        this.events = events;
        this.ranker = ranker;
        this.experiments = experiments;
        this.modelVersion = modelVersion;
        this.candidateLimit = candidateLimit;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(cacheSeconds))
                .build();
    }

    public CompletableFuture<RecommendationModels.Response> recommend(
            RecommendationModels.Request request) {
        return CompletableFuture.supplyAsync(() -> compute(request), executor)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        errors.increment();
                    }
                });
    }

    private RecommendationModels.Response compute(RecommendationModels.Request request) {
        long started = System.nanoTime();
        requests.increment();
        String requestId = request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId();
        String key = cacheKey(request);
        RecommendationModels.Response cached = cache.getIfPresent(key);
        if (cached != null) {
            cacheHits.increment();
            return new RecommendationModels.Response(
                    requestId,
                    cached.modelVersion(),
                    cached.recommendations(),
                    elapsedMillis(started),
                    true,
                    cached.experimentVariant());
        }

        var assignment = experiments.assign("native-diversity-v1", request.profile().anonymousId());
        double diversityWeight = assignment.parameters()
                .getOrDefault("diversityWeight", request.diversityWeight());
        var history = events.recent(request.profile().anonymousId(), 500);
        var candidates = generator.generate(request.profile(), history, candidateLimit);
        List<Map<String, Double>> featureRows = candidates.stream()
                .map(candidate -> features.build(request.profile(), candidate.track(), history))
                .toList();
        var ranked = ranker.rank(
                candidates,
                featureRows,
                request.limit(),
                diversityWeight,
                request.explorationWeight());
        var response = new RecommendationModels.Response(
                requestId,
                modelVersion,
                ranked,
                elapsedMillis(started),
                false,
                assignment.variant());
        cache.put(key, response);
        return response;
    }

    public Map<String, Long> metrics() {
        return Map.of(
                "requests", requests.sum(),
                "cacheHits", cacheHits.sum(),
                "errors", errors.sum(),
                "cacheSize", cache.estimatedSize());
    }

    public void invalidate() {
        cache.invalidateAll();
    }

    private String cacheKey(RecommendationModels.Request request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = request.profile().toString()
                    + request.surface()
                    + request.limit()
                    + request.diversityWeight()
                    + request.explorationWeight();
            return java.util.HexFormat.of().formatHex(
                    digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private double elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    @Override
    public void close() {
        executor.close();
    }
}
