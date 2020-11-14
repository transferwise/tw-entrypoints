package com.transferwise.common.entrypoints.tableaccessstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.MS_TO_NS;
import static com.transferwise.common.entrypoints.tableaccessstatistics.TableAccessStatisticsSpyqlListener.METRIC_FIRST_TABLE_ACCESS;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

public class TasMeterFilter implements MeterFilter {

  @Override
  public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
    if (METRIC_FIRST_TABLE_ACCESS.equals(id.getName())) {
      return DistributionStatisticConfig.builder()
          .percentilesHistogram(false)
          .serviceLevelObjectives(1 * MS_TO_NS, 5 * MS_TO_NS, 25 * MS_TO_NS, 125 * MS_TO_NS, 625 * MS_TO_NS, 3125 * MS_TO_NS, 3125 * 5 * MS_TO_NS)
          .build()
          .merge(config);
    }
    return config;
  }
}
