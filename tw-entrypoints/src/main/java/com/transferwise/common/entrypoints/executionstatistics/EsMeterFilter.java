package com.transferwise.common.entrypoints.executionstatistics;

import static com.transferwise.common.entrypoints.executionstatistics.ExecutionStatisticsEntryPointInterceptor.METRIC_PREFIX_ENTRYPOINTS_ES;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

public class EsMeterFilter implements MeterFilter {

  @Override
  public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
    if (id.getName().startsWith(METRIC_PREFIX_ENTRYPOINTS_ES)) {
      if (id.getType() == Type.DISTRIBUTION_SUMMARY) {
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(false)
            .maximumExpectedValue(1d)
            .build()
            .merge(config);
      } else if (id.getType() == Type.TIMER) {
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(false)
            .maximumExpectedValue(1d)
            .minimumExpectedValue(1d)
            .build()
            .merge(config);
      }
    }
    return config;
  }
}
