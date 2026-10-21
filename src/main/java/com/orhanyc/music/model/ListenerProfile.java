package com.orhanyc.music.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ListenerProfile(
        @NotBlank String anonymousId,
        Map<String, Double> genrePreferences,
        List<String> preferredLanguages,
        Set<String> blockedArtists,
        List<String> recentTrackIds,
        @DecimalMin("0.0") @DecimalMax("1.0") double targetEnergy,
        boolean allowExplicit) {

    public ListenerProfile {
        genrePreferences = Map.copyOf(genrePreferences);
        preferredLanguages = List.copyOf(preferredLanguages);
        blockedArtists = Set.copyOf(blockedArtists);
        recentTrackIds = List.copyOf(recentTrackIds);
    }
}

