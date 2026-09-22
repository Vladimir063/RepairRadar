package ru.repairradar.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public class ReservoirSampler<T> {

    private final int capacity;
    private final RandomGenerator random;
    private final List<T> selected = new ArrayList<>();
    private long seen;

    public ReservoirSampler(int capacity, RandomGenerator random) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.random = random;
    }

    public void accept(T value) {
        seen++;
        if (selected.size() < capacity) {
            selected.add(value);
        } else {
            long index = random.nextLong(seen);
            if (index < capacity) {
                selected.set((int) index, value);
            }
        }
    }

    public List<T> values() {
        return List.copyOf(selected);
    }
}
