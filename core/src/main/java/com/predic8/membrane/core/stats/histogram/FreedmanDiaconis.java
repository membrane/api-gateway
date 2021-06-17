package com.predic8.membrane.core.stats.histogram;

import java.util.*;
import java.util.stream.Collectors;

public class FreedmanDiaconis {

    private final List<Integer> metrics;

    public FreedmanDiaconis(List<Integer> metrics) {
        this.metrics = metrics;
    }

    public String getXmlNotation() {
        int count = binCount();
        double width = binWidth();

        List<Double> values = new ArrayList<>();

        for (int i = 1; i < count; i++) {
            values.add(width * i);
        }

        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public int binCount() {
        int max = Collections.max(metrics);
        int min = Collections.min(metrics);
        return (int) Math.ceil((max - min) / binWidth());
    }

    public double binWidth() {
        return 2 * interquartileRange() * Math.pow(metrics.size(), -1.0/3.0);
    }

    private int interquartileRange() {
        List<Integer> sorted = metrics.stream()
                .sorted(Comparator.comparingInt(o -> o))
                .collect(Collectors.toList());

        int q1 = sorted.get((int) Math.floor(sorted.size() / 4.0));
        int q3 = sorted.get((int) Math.floor(sorted.size() * 3.0 / 4.0));

        return q3 - q1;
    }
}
