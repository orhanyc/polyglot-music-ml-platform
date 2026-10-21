package com.orhanyc.music.catalog;

import com.orhanyc.music.model.Track;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class CatalogRepository {
    private final Map<String, Track> tracks = new ConcurrentHashMap<>();

    public CatalogRepository() {
        seedCatalog();
    }

    public Optional<Track> findById(String id) {
        return Optional.ofNullable(tracks.get(id));
    }

    public List<Track> findAll() {
        return List.copyOf(tracks.values());
    }

    public List<Track> findByGenres(Set<String> genres, int limit) {
        return tracks.values().stream()
                .filter(track -> genres.contains(track.genre()))
                .sorted(Comparator.comparingDouble(Track::popularity).reversed())
                .limit(limit)
                .toList();
    }

    public List<Track> mostPopular(int limit) {
        return tracks.values().stream()
                .sorted(Comparator.comparingDouble(Track::popularity).reversed())
                .limit(limit)
                .toList();
    }

    public Map<String, List<Track>> groupByArtist() {
        return tracks.values().stream().collect(Collectors.groupingBy(Track::artist));
    }

    public Map<String, List<Track>> groupByGenre() {
        return tracks.values().stream().collect(Collectors.groupingBy(Track::genre));
    }

    public void upsert(Track track) {
        tracks.put(track.id(), track);
    }

    public void replaceAll(List<Track> replacement) {
        tracks.clear();
        replacement.forEach(this::upsert);
    }

    public int size() {
        return tracks.size();
    }

    private void seedCatalog() {
        List<Track> seeds = new ArrayList<>();
        seeds.add(track("t1", "Midnight Lines", "Nova Vale", "electronic", 0.78, 0.72));
        seeds.add(track("t2", "Paper Satellites", "Nova Vale", "electronic", 0.62, 0.81));
        seeds.add(track("t3", "Quiet Current", "Mira Coast", "ambient", 0.22, 0.48));
        seeds.add(track("t4", "Second Avenue", "The Lanterns", "indie", 0.57, 0.66));
        seeds.add(track("t5", "Glass Garden", "Aster Bloom", "pop", 0.69, 0.88));
        seeds.add(track("t6", "Long Way Home", "Cedar Field", "folk", 0.38, 0.53));
        seeds.add(track("t7", "Signal Fires", "The Lanterns", "rock", 0.86, 0.77));
        seeds.add(track("t8", "Blue Geometry", "June Atlas", "jazz", 0.44, 0.51));
        seeds.add(track("t9", "Daybreak Run", "Kite Theory", "pop", 0.92, 0.84));
        seeds.add(track("t10", "Soft Machines", "Mira Coast", "ambient", 0.31, 0.59));
        seeds.add(track("t11", "Golden Hour", "Cedar Field", "folk", 0.47, 0.64));
        seeds.add(track("t12", "Northbound", "Kite Theory", "rock", 0.82, 0.74));
        seeds.forEach(this::upsert);
    }

    private Track track(
            String id,
            String title,
            String artist,
            String genre,
            double energy,
            double popularity) {
        return new Track(
                id,
                title,
                artist,
                genre,
                "en",
                energy,
                popularity,
                2026,
                false,
                List.of(energy, popularity, 1 - energy));
    }
}

