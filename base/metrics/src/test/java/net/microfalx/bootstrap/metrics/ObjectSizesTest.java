package net.microfalx.bootstrap.metrics;

import net.microfalx.jvm.ObjectSize;
import net.microfalx.jvm.ObjectSizeEstimator;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectSizesTest {

    @Test
    void timeWindowStatisticalSummary() {
        MutableStatisticalSummary summary = (MutableStatisticalSummary) TrendStatisticalSummary.create();
        for (int i = 0; i < 100; i++) {
            summary.add(i);
        }
        ObjectSize size = ObjectSizeEstimator.get().getDeepSize(summary);
        assertThat(size.getSizeOf()).isGreaterThanOrEqualTo(1200);
        assertThat(size.getCountOf()).isGreaterThanOrEqualTo(30);
        assertThat(size.getArraySizeOf()).isGreaterThanOrEqualTo(80);
        assertThat(size.getArrayCountOf()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void arrayOfTimeWindowStatisticalSummary() {
        List<MutableStatisticalSummary> summaries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MutableStatisticalSummary summary = (MutableStatisticalSummary) TrendStatisticalSummary.create();
            for (int j = 0; j < 100; j++) {
                summary.add(i);
            }
            summaries.add(summary);
        }
        ObjectSize size = ObjectSizeEstimator.get().getDeepSize(summaries);
        assertThat(size.getSizeOf()).isGreaterThanOrEqualTo(12000L);
        assertThat(size.getCountOf()).isGreaterThanOrEqualTo(300);
        assertThat(size.getArraySizeOf()).isGreaterThanOrEqualTo(800);
        assertThat(size.getArrayCountOf()).isGreaterThanOrEqualTo(100);
    }
}
