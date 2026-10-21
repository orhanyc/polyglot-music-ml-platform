package com.orhanyc.music.experiment;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExperimentService {
    private final Map<String, Experiment> experiments = new ConcurrentHashMap<>();

    public ExperimentService() {
        register(new Experiment(
                "native-diversity-v1",
                "music-platform-2026",
                List.of(
                        new Variant("control", 50, Map.of("diversityWeight", 0.25)),
                        new Variant("balanced", 25, Map.of("diversityWeight", 0.40)),
                        new Variant("discovery", 25, Map.of("diversityWeight", 0.55)))));
    }

    public void register(Experiment experiment) {
        validate(experiment);
        experiments.put(experiment.name(), experiment);
    }

    public Assignment assign(String experimentName, String anonymousId) {
        Experiment experiment = requireExperiment(experimentName);
        int bucket = bucket(experiment.salt(), anonymousId);
        int cursor = 0;
        for (Variant variant : experiment.variants()) {
            cursor += variant.allocation();
            if (bucket < cursor) {
                return new Assignment(experimentName, variant.name(), variant.parameters());
            }
        }
        throw new IllegalStateException("Experiment allocation did not resolve");
    }

    public List<String> names() {
        return experiments.keySet().stream().sorted().toList();
    }

    private Experiment requireExperiment(String name) {
        Experiment experiment = experiments.get(name);
        if (experiment == null) {
            throw new IllegalArgumentException("Unknown experiment: " + name);
        }
        return experiment;
    }

    private void validate(Experiment experiment) {
        int allocation = experiment.variants().stream().mapToInt(Variant::allocation).sum();
        if (allocation != 100) {
            throw new IllegalArgumentException("Variant allocations must total 100");
        }
        if (experiment.variants().stream().anyMatch(variant -> variant.allocation() <= 0)) {
            throw new IllegalArgumentException("Variant allocations must be positive");
        }
    }

    private int bucket(String salt, String anonymousId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + ":" + anonymousId).getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xff) << 24)
                    | ((hash[1] & 0xff) << 16)
                    | ((hash[2] & 0xff) << 8)
                    | (hash[3] & 0xff);
            return Math.floorMod(value, 100);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    public record Experiment(String name, String salt, List<Variant> variants) {
        public Experiment {
            variants = List.copyOf(variants);
        }
    }

    public record Variant(String name, int allocation, Map<String, Double> parameters) {
        public Variant {
            parameters = Map.copyOf(parameters);
        }
    }

    public record Assignment(String experiment, String variant, Map<String, Double> parameters) {
        public Assignment {
            parameters = Map.copyOf(parameters);
        }
    }
}

