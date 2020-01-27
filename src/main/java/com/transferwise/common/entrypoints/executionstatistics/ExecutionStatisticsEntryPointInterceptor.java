package com.transferwise.common.entrypoints.executionstatistics;

import com.transferwise.common.baseutils.clock.ClockHolder;
import com.transferwise.common.baseutils.context.TwContext;
import com.transferwise.common.entrypoints.EntryPointsMetricUtils;
import com.transferwise.common.entrypoints.IEntryPointInterceptor;
import com.transferwise.common.entrypoints.IEntryPointsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.METRIC_PREFIX_ENTRYPOINTS;

public class ExecutionStatisticsEntryPointInterceptor implements IEntryPointInterceptor {
    private MeterRegistry meterRegistry;
    private IEntryPointsRegistry entryPointsRegistry;

    public ExecutionStatisticsEntryPointInterceptor(MeterRegistry meterRegistry, IEntryPointsRegistry entryPointsRegistry) {
        this.meterRegistry = meterRegistry;
        this.entryPointsRegistry = entryPointsRegistry;
    }

    @Override
    public <T> T inEntryPointContext(Supplier<T> supplier) {
        long startTimeMs = ClockHolder.getClock().millis();
        try {
            return supplier.get();
        } finally {
            TwContext twContext = TwContext.current();
            if (twContext != null && entryPointsRegistry.registerEntryPoint(twContext)) {
                String name = EntryPointsMetricUtils.normalizeNameForMetric(twContext.getName());
                String group = EntryPointsMetricUtils.normalizeNameForMetric(twContext.getGroup());

                Tags tags = Tags.of(EntryPointsMetricUtils.TAG_ENTRYPOINT_NAME, name,
                                    EntryPointsMetricUtils.TAG_ENTRYPOINT_GROUP, group);
                EntryPointsMetricUtils
                    .timerWithoutBuckets(meterRegistry, METRIC_PREFIX_ENTRYPOINTS + "Es.timeTaken", tags)
                    .record(ClockHolder.getClock().millis() - startTimeMs, TimeUnit.MILLISECONDS);
            }
        }
    }
}
