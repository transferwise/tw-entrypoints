package com.transferwise.common.entrypoints;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

public class EntryPointsMetricUtils {
    public static final String TAG_ENTRYPOINT_NAME = "entryPointName";

    public static String normalizeNameForMetric(String name) {
        return StringUtils.replaceChars(name, '.', '_');
    }

    public static DistributionSummary summaryWithoutBuckets(MeterRegistry meterRegistry, String name, Iterable<Tag> tags) {
        return DistributionSummary.builder(name).tags(tags)
                                  .publishPercentileHistogram(false)
                                  .maximumExpectedValue(
                                      1L) // TODO: this limits number on buckets and is currently needed because the above histogram disabling does not work due to many services using management.metrics.distribution.percentiles-histogram.all = true.
                                  // remove it when services start using histograms sensibly
                                  .register(meterRegistry);
    }

    public static Timer timerWithoutBuckets(MeterRegistry meterRegistry, String name, Iterable<Tag> tags) {
        return Timer.builder(name).tags(tags)
                    .publishPercentileHistogram(false)
                    .minimumExpectedValue(Duration.ofNanos(1L))
                    .maximumExpectedValue(Duration.ofNanos(
                        1L)) // TODO: last two are limiting number on buckets and are currently needed because the above histogram disabling does not work due to many services using management.metrics.distribution.percentiles-histogram.all = true.
                    // remove those when services start using histograms sensibly
                    .register(meterRegistry);
    }
}
