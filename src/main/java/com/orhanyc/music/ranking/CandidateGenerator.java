package com.orhanyc.music.ranking;

import com.orhanyc.music.catalog.CatalogRepository;
import com.orhanyc.music.model.ListenerProfile;
import com.orhanyc.music.model.ListeningEvent;
import com.orhanyc.music.model.Track;
import com.orhanyc.music.signals.FeaturePipeline;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class CandidateGenerator {
    private final CatalogRepository catalog;
    private final FeaturePipeline features;
    private final Random random = new Random(17);

    public CandidateGenerator(CatalogRepository catalog, FeaturePipeline features) {
        this.catalog = catalog;
        this.features = features;
    }

    public List<Candidate> generate(
            ListenerProfile profile,
            List<ListeningEvent> history,
            int limit) {
        Map<String, Candidate> merged = new LinkedHashMap<>();
        merge(merged, fromPreferences(profile, limit));
        merge(merged, fromHistory(history, limit));
        merge(merged, fromPopularity(limit));
        merge(merged, fromExploration(limit));
        return merged.values().stream()
                .filter(candidate -> allowed(profile, candidate.track()))
                .sorted(Comparator.comparingDouble(Candidate::retrievalScore).reversed())
                .limit(limit)
                .toList();
    }

    private List<Candidate> fromPreferences(ListenerProfile profile, int limit) {
        return catalog.findByGenres(profile.genrePreferences().keySet(), limit).stream()
                .map(track -> new Candidate(
                        track,
                        "genre",
                        profile.genrePreferences().getOrDefault(track.genre(), 0.0)))
                .toList();
    }

    private List<Candidate> fromHistory(List<ListeningEvent> history, int limit) {
        Map<String, Double> affinity = features.allTrackAffinities(history);
        List<Track> positive = affinity.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(20)
                .map(Map.Entry::getKey)
                .map(catalog::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
        var artists = positive.stream().map(Track::artist).collect(java.util.stream.Collectors.toSet());
        var genres = positive.stream().map(Track::genre).collect(java.util.stream.Collectors.toSet());
        return catalog.findAll().stream()
                .filter(track -> artists.contains(track.artist()) || genres.contains(track.genre()))
                .limit(limit)
                .map(track -> new Candidate(track, "history", 0.8))
                .toList();
    }

    private List<Candidate> fromPopularity(int limit) {
        return catalog.mostPopular(limit).stream()
                .map(track -> new Candidate(track, "popular", track.popularity()))
                .toList();
    }

    private List<Candidate> fromExploration(int limit) {
        List<Track> tracks = new ArrayList<>(catalog.findAll());
        java.util.Collections.shuffle(tracks, random);
        return tracks.stream()
                .limit(Math.max(limit / 5, 1))
                .map(track -> new Candidate(track, "exploration", 0.25))
                .toList();
    }

    private void merge(Map<String, Candidate> merged, List<Candidate> additions) {
        for (Candidate candidate : additions) {
            merged.merge(
                    candidate.track().id(),
                    candidate,
                    (left, right) -> left.retrievalScore() >= right.retrievalScore() ? left : right);
        }
    }

    private boolean allowed(ListenerProfile profile, Track track) {
        return !profile.blockedArtists().contains(track.artist())
                && (profile.allowExplicit() || !track.explicit())
                && !profile.recentTrackIds().contains(track.id());
    }

    public record Candidate(Track track, String source, double retrievalScore) {
    }
}

