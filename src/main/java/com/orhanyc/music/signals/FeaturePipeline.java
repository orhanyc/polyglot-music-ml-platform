package com.orhanyc.music.signals;

import com.orhanyc.music.model.ListenerProfile;
import com.orhanyc.music.model.ListeningEvent;
import com.orhanyc.music.model.Track;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FeaturePipeline {
    private static final Map<ListeningEvent.EventType, Double> EVENT_WEIGHTS =
            new EnumMap<>(ListeningEvent.EventType.class);

    static {
        EVENT_WEIGHTS.put(ListeningEvent.EventType.IMPRESSION, 0.05);
        EVENT_WEIGHTS.put(ListeningEvent.EventType.PLAY, 0.5);
        EVENT_WEIGHTS.put(ListeningEvent.EventType.SKIP, -0.8);
        EVENT_WEIGHTS.put(ListeningEvent.EventType.LIKE, 2.0);
        EVENT_WEIGHTS.put(ListeningEvent.EventType.ADD, 2.5);
    }

    public Map<String, Double> build(
            ListenerProfile profile,
            Track track,
            List<ListeningEvent> history) {
        Map<String, Double> features = new HashMap<>();
        features.put("genre", genreScore(profile, track));
        features.put("energy", clamp(1 - Math.abs(profile.targetEnergy() - track.energy())));
        features.put("popularity", track.popularity());
        features.put("language", profile.preferredLanguages().contains(track.language()) ? 1.0 : 0.2);
        features.put("affinity", scaleSigned(trackAffinity(track.id(), history)));
        features.put("freshness", Math.exp(-Math.max(2026 - track.releaseYear(), 0) / 8.0));
        features.put("completion", completionScore(track.id(), history));
        features.put("quality", qualityScore(track.id(), history));
        return Map.copyOf(features);
    }

    public Map<String, Double> allTrackAffinities(List<ListeningEvent> history) {
        Map<String, Double> values = new HashMap<>();
        for (ListeningEvent event : history) {
            double decay = recencyDecay(event.timestamp());
            double completion = event.type() == ListeningEvent.EventType.PLAY
                    ? Math.min(event.playSeconds() / 180.0, 1.0)
                    : 1.0;
            double weighted = EVENT_WEIGHTS.get(event.type()) * decay * completion;
            values.merge(event.trackId(), weighted, Double::sum);
        }
        double scale = values.values().stream().mapToDouble(Math::abs).max().orElse(1.0);
        if (scale == 0) {
            return values;
        }
        values.replaceAll((ignored, value) -> value / scale);
        return Map.copyOf(values);
    }

    private double genreScore(ListenerProfile profile, Track track) {
        double highest = profile.genrePreferences().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);
        double preference = profile.genrePreferences().getOrDefault(track.genre(), 0.0);
        return clamp(preference / Math.max(highest, 0.001));
    }

    private double trackAffinity(String trackId, List<ListeningEvent> history) {
        return allTrackAffinities(history).getOrDefault(trackId, 0.0);
    }

    private double completionScore(String trackId, List<ListeningEvent> history) {
        List<ListeningEvent> plays = history.stream()
                .filter(event -> event.trackId().equals(trackId))
                .filter(event -> event.type() == ListeningEvent.EventType.PLAY)
                .toList();
        if (plays.isEmpty()) {
            return 0.5;
        }
        return plays.stream()
                .mapToDouble(event -> Math.min(event.playSeconds() / 180.0, 1.0))
                .average()
                .orElse(0.5);
    }

    private double qualityScore(String trackId, List<ListeningEvent> history) {
        long positive = history.stream()
                .filter(event -> event.trackId().equals(trackId))
                .filter(event -> event.type() == ListeningEvent.EventType.LIKE
                        || event.type() == ListeningEvent.EventType.ADD)
                .count();
        long skips = history.stream()
                .filter(event -> event.trackId().equals(trackId))
                .filter(event -> event.type() == ListeningEvent.EventType.SKIP)
                .count();
        return (positive + 1.0) / (positive + skips + 2.0);
    }

    private double recencyDecay(Instant timestamp) {
        double days = Math.max(Duration.between(timestamp, Instant.now()).toHours() / 24.0, 0.0);
        return Math.pow(0.5, days / 14.0);
    }

    private double scaleSigned(double value) {
        return clamp((value + 1) / 2);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(value, 1));
    }
}
