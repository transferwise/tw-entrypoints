package com.transferwise.common.entrypoints.executionstatistics;

import com.transferwise.common.baseutils.clock.ClockHolder;
import com.transferwise.common.entrypoints.EntryPointContext;
import com.transferwise.common.entrypoints.EntryPointsMetricUtils;
import com.transferwise.common.entrypoints.IEntryPointInterceptor;
import com.transferwise.common.entrypoints.IEntryPointsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.TAG_PREFIX_ENTRYPOINTS;

public class ExecutionStatisticsEntryPointInterceptor implements IEntryPointInterceptor {
    private MeterRegistry meterRegistry;
    private IEntryPointsRegistry entryPointsRegistry;

    public ExecutionStatisticsEntryPointInterceptor(MeterRegistry meterRegistry, IEntryPointsRegistry entryPointsRegistry) {
        this.meterRegistry = meterRegistry;
        this.entryPointsRegistry = entryPointsRegistry;
    }

    @Override
    public <T> T inEntryPointContext(EntryPointContext context, EntryPointContext unknownContext, Callable<T> callable) throws Exception {
        long startTimeMs = ClockHolder.getClock().millis();
        try {
            return callable.call();
        } finally {
            if (entryPointsRegistry.registerEntryPoint(context)) {
                String name = EntryPointsMetricUtils.normalizeNameForMetric(context.getName());
                String group = EntryPointsMetricUtils.normalizeNameForMetric(context.getGroup());

                Tags tags = Tags.of(EntryPointsMetricUtils.TAG_ENTRYPOINT_NAME, name,
                                    EntryPointsMetricUtils.TAG_ENTRYPOINT_GROUP, group);
                EntryPointsMetricUtils.timerWithoutBuckets(meterRegistry, TAG_PREFIX_ENTRYPOINTS + "Es.timeTaken", tags)
                                      .record(ClockHolder.getClock().millis() - startTimeMs, TimeUnit.MILLISECONDS);
            }
        }
    }
}
