package com.orhanyc.music.signals;

import com.orhanyc.music.model.ListeningEvent;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class EventRepository {
    private static final Duration RETENTION = Duration.ofDays(30);
    private final Map<String, List<ListeningEvent>> events = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void append(ListeningEvent event) {
        lock.writeLock().lock();
        try {
            events.computeIfAbsent(event.anonymousId(), ignored -> new ArrayList<>()).add(event);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void appendAll(List<ListeningEvent> batch) {
        lock.writeLock().lock();
        try {
            for (ListeningEvent event : batch) {
                events.computeIfAbsent(event.anonymousId(), ignored -> new ArrayList<>()).add(event);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<ListeningEvent> recent(String anonymousId, int limit) {
        Instant cutoff = Instant.now().minus(RETENTION);
        lock.writeLock().lock();
        try {
            List<ListeningEvent> retained = events.getOrDefault(anonymousId, List.of()).stream()
                    .filter(event -> event.timestamp().isAfter(cutoff))
                    .toList();
            events.put(anonymousId, new ArrayList<>(retained));
            int start = Math.max(0, retained.size() - limit);
            return List.copyOf(retained.subList(start, retained.size()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean delete(String anonymousId) {
        lock.writeLock().lock();
        try {
            return events.remove(anonymousId) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int count() {
        lock.readLock().lock();
        try {
            return events.values().stream().mapToInt(List::size).sum();
        } finally {
            lock.readLock().unlock();
        }
    }
}

