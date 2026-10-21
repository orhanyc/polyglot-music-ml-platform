package com.orhanyc.music.model;

import java.util.List;
import java.util.Objects;

public record Track(
        String id,
        String title,
        String artist,
        String genre,
        String language,
        double energy,
        double popularity,
        int releaseYear,
        boolean explicit,
        List<Double> embedding) {

    public Track {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        Objects.requireNonNull(artist);
        Objects.requireNonNull(genre);
        Objects.requireNonNull(language);
        embedding = List.copyOf(embedding);
        if (id.isBlank() || title.isBlank() || artist.isBlank()) {
            throw new IllegalArgumentException("Track identity fields cannot be blank");
        }
        if (energy < 0 || energy > 1 || popularity < 0 || popularity > 1) {
            throw new IllegalArgumentException("Normalized track features must be in [0, 1]");
        }
    }
}

