package com.orhanyc.music.api;

import com.orhanyc.music.catalog.CatalogRepository;
import com.orhanyc.music.model.ListeningEvent;
import com.orhanyc.music.model.RecommendationModels;
import com.orhanyc.music.privacy.PrivacyService;
import com.orhanyc.music.ranking.NativeRanker;
import com.orhanyc.music.service.RecommendationService;
import com.orhanyc.music.signals.EventRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/v1")
public class RecommendationController {
    private static final Set<String> SUPPORTED_REGIONS = Set.of("us", "eu", "apac");
    private final RecommendationService recommendations;
    private final EventRepository events;
    private final PrivacyService privacy;
    private final CatalogRepository catalog;
    private final NativeRanker nativeRanker;

    public RecommendationController(
            RecommendationService recommendations,
            EventRepository events,
            PrivacyService privacy,
            CatalogRepository catalog,
            NativeRanker nativeRanker) {
        this.recommendations = recommendations;
        this.events = events;
        this.privacy = privacy;
        this.catalog = catalog;
        this.nativeRanker = nativeRanker;
    }

    @PostMapping("/recommendations")
    public CompletableFuture<ResponseEntity<RecommendationModels.Response>> recommend(
            @Valid @RequestBody RecommendationModels.Request request,
            @RequestHeader(name = "X-Region", defaultValue = "us") String region) {
        if (!SUPPORTED_REGIONS.contains(region)) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        }
        return recommendations.recommend(request).thenApply(ResponseEntity::ok);
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, String>> event(@RequestBody ListeningEvent event) {
        events.append(privacy.minimize(event));
        recommendations.invalidate();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "accepted"));
    }

    @PostMapping("/events/batch")
    public ResponseEntity<Map<String, Integer>> eventBatch(@RequestBody List<ListeningEvent> batch) {
        if (batch.size() > 500) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        events.appendAll(batch.stream().map(privacy::minimize).toList());
        recommendations.invalidate();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("accepted", batch.size()));
    }

    @DeleteMapping("/listeners/{anonymousId}")
    public ResponseEntity<Void> deleteListener(@PathVariable String anonymousId) {
        events.delete(privacy.hashIdentifier(anonymousId));
        recommendations.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "healthy",
                "catalogSize", catalog.size(),
                "nativeRanker", nativeRanker.isNativeAvailable());
    }

    @GetMapping("/metrics")
    public Map<String, Long> metrics() {
        return recommendations.metrics();
    }
}

