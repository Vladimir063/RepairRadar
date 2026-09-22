package ru.repairradar.gar;

import org.junit.jupiter.api.Test;
import ru.repairradar.utility.ReservoirSampler;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservoirSamplerTest {

    @Test
    void replacesAnEarlyElement() {
        var random = mock(RandomGenerator.class);
        when(random.nextLong(3)).thenReturn(0L);
        var sampler = new ReservoirSampler<Integer>(2, random);
        sampler.accept(10);
        sampler.accept(20);
        sampler.accept(30);
        assertThat(sampler.values()).containsExactly(30, 20);
    }

    @Test
    void reproducibleAndBounded() {
        var first = new ReservoirSampler<Integer>(100, new Random(42));
        var second = new ReservoirSampler<Integer>(100, new Random(42));
        for (int i = 0; i < 10000; i++) {
            first.accept(i);
            second.accept(i);
        }
        assertThat(first.values()).hasSize(100).doesNotHaveDuplicates().isEqualTo(second.values());
        assertThat(first.values()).anyMatch(i -> i >= 100);
    }
}