package com.transferwise.common.entrypoints.transactionstatistics;

import static com.transferwise.common.entrypoints.EntryPointsMetrics.MS_TO_NS;
import static com.transferwise.common.entrypoints.transactionstatistics.TransactionsStatisticsSpyqlListener.METRIC_TRANSACTION_COMPLETION;
import static com.transferwise.common.entrypoints.transactionstatistics.TransactionsStatisticsSpyqlListener.METRIC_TRANSACTION_FINALIZATION;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

public class TsMeterFilter implements MeterFilter {

  @Override
  public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
    String name = id.getName();
    if (!METRIC_TRANSACTION_COMPLETION.equals(name) && !(METRIC_TRANSACTION_FINALIZATION.equals(name))) {
      return config;
    }
    return DistributionStatisticConfig.builder()
        .percentilesHistogram(false)
        .serviceLevelObjectives(1 * MS_TO_NS, 5 * MS_TO_NS, 25 * MS_TO_NS, 125 * MS_TO_NS, 625 * MS_TO_NS, 3125 * MS_TO_NS, 15625 * MS_TO_NS,
            78125 * MS_TO_NS)
        .build()
        .merge(config);
  }
}
