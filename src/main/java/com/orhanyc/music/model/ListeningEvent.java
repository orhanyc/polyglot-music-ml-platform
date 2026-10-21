package com.orhanyc.music.model;

import java.time.Instant;
import java.util.Map;

public record ListeningEvent(
        String anonymousId,
        String trackId,
        EventType type,
        Instant timestamp,
        int playSeconds,
        Surface surface,
        Map<String, String> context) {

    public ListeningEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        surface = surface == null ? Surface.HOME : surface;
        context = context == null ? Map.of() : Map.copyOf(context);
        playSeconds = Math.clamp(playSeconds, 0, 3600);
    }

    public enum EventType {
        IMPRESSION,
        PLAY,
        SKIP,
        LIKE,
        ADD
    }

    public enum Surface {
        HOME,
        NEW,
        RADIO,
        MIX
    }
}

