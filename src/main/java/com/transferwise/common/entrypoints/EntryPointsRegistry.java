package com.transferwise.common.entrypoints;


import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.transferwise.common.entrypoints.EntryPointsMetricUtils.TAG_PREFIX_ENTRYPOINTS;

/**
 * Goal is to prevent OOM but also to protect Prometheus, if someone starts spamming with too many different entrypoints names.
 */
@Slf4j
public class EntryPointsRegistry implements IEntryPointsRegistry {
    @Value("${tw-entrypoints.max-distinct-entry-points:2000}")
    private int maxDistinctEntryPointsCount;

    private MeterRegistry meterRegistry;
    private Lock registrationLock = new ReentrantLock();

    public EntryPointsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private static final Map<Pair<String, String>, Boolean> registeredNames = new HashMap<>();
    private AtomicInteger registeredNamesCount;

    @PostConstruct
    public void init() {
        registeredNamesCount = meterRegistry.gauge(TAG_PREFIX_ENTRYPOINTS + "RegistrationsCount", new AtomicInteger());
    }

    @Override
    public boolean registerEntryPoint(EntryPointContext context) {
        return registerEntryPoint(context.getGroup(), context.getName());
    }

    @Override
    public boolean registerEntryPoint(String group, String name) {
        Pair<String, String> key = Pair.of(group, name);

        registrationLock.lock();
        try {
            if (registeredNamesCount.get() >= maxDistinctEntryPointsCount) {
                return false;
            }
            if (registeredNames.containsKey(key)) {
                return true;
            }
            registeredNames.put(key, Boolean.TRUE);
            registeredNamesCount.incrementAndGet();

            if (registeredNamesCount.get() == maxDistinctEntryPointsCount) {
                log.error("Too many Entry Points detected, check for parameterized urls in following entries.");
                registeredNames.forEach((k, v) -> log.info("Registered Entry Point: `" + k + "`"));
            }

            return true;
        } finally {
            registrationLock.unlock();
        }
    }
}
