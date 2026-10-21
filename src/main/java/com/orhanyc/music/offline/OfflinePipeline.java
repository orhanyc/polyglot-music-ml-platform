package com.orhanyc.music.offline;

import com.orhanyc.music.model.ListeningEvent;
import com.orhanyc.music.model.RecommendationModels;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class OfflinePipeline {
    public Split temporalSplit(List<ListeningEvent> events, double holdoutFraction) {
        if (holdoutFraction <= 0 || holdoutFraction >= 1) {
            throw new IllegalArgumentException("Holdout fraction must be between zero and one");
        }
        List<ListeningEvent> ordered = events.stream()
                .sorted(java.util.Comparator.comparing(ListeningEvent::timestamp))
                .toList();
        int split = Math.max((int) (ordered.size() * (1 - holdoutFraction)), 1);
        return new Split(ordered.subList(0, split), ordered.subList(split, ordered.size()));
    }

    public Evaluation evaluate(
            Map<String, List<RecommendationModels.Item>> recommendations,
            Map<String, Set<String>> holdouts,
            int catalogSize) {
        List<Double> precision = new ArrayList<>();
        List<Double> recall = new ArrayList<>();
        List<Double> diversity = new ArrayList<>();
        Set<String> catalogCoverage = new HashSet<>();

        recommendations.forEach((listener, items) -> {
            Set<String> predicted = items.stream()
                    .map(item -> item.track().id())
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> relevant = holdouts.getOrDefault(listener, Set.of());
            Set<String> intersection = new HashSet<>(predicted);
            intersection.retainAll(relevant);
            precision.add(intersection.size() / (double) Math.max(predicted.size(), 1));
            recall.add(intersection.size() / (double) Math.max(relevant.size(), 1));
            long artists = items.stream().map(item -> item.track().artist()).distinct().count();
            diversity.add(artists / (double) Math.max(items.size(), 1));
            catalogCoverage.addAll(predicted);
        });

        return new Evaluation(
                recommendations.size(),
                mean(precision),
                mean(recall),
                mean(diversity),
                catalogCoverage.size() / (double) Math.max(catalogSize, 1));
    }

    public ConfidenceInterval compare(
            List<Double> control,
            List<Double> treatment,
            int samples,
            long seed) {
        if (control.isEmpty() || treatment.isEmpty()) {
            return new ConfidenceInterval(0, 0, 0);
        }
        Random random = new Random(seed);
        List<Double> differences = new ArrayList<>();
        for (int sample = 0; sample < samples; sample++) {
            double controlMean = bootstrapMean(control, random);
            double treatmentMean = bootstrapMean(treatment, random);
            differences.add(treatmentMean - controlMean);
        }
        Collections.sort(differences);
        double observed = mean(treatment) - mean(control);
        double lower = differences.get((int) (samples * 0.025));
        double upper = differences.get(Math.min((int) (samples * 0.975), samples - 1));
        return new ConfidenceInterval(observed, lower, upper);
    }

    public Map<String, List<ListeningEvent>> groupByListener(List<ListeningEvent> events) {
        Map<String, List<ListeningEvent>> grouped = new HashMap<>();
        for (ListeningEvent event : events) {
            grouped.computeIfAbsent(event.anonymousId(), ignored -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    public Map<String, Double> dailyEventCounts(List<ListeningEvent> events) {
        Map<String, Double> counts = new HashMap<>();
        for (ListeningEvent event : events) {
            String day = event.timestamp().toString().substring(0, 10);
            counts.merge(day, 1.0, Double::sum);
        }
        return Map.copyOf(counts);
    }

    private double bootstrapMean(List<Double> values, Random random) {
        double total = 0;
        for (int index = 0; index < values.size(); index++) {
            total += values.get(random.nextInt(values.size()));
        }
        return total / values.size();
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public record Split(List<ListeningEvent> training, List<ListeningEvent> holdout) {
        public Split {
            training = List.copyOf(training);
            holdout = List.copyOf(holdout);
        }
    }

    public record Evaluation(
            int listeners,
            double precisionAtK,
            double recallAtK,
            double artistDiversity,
            double catalogCoverage) {
    }

    public record ConfidenceInterval(double observed, double lower, double upper) {
    }
}
