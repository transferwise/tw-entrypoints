package com.transferwise.common.entrypoints;


import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Goal is to prevent OOM but also to protect Prometheus, if someone starts spamming with too many different entrypoints names.
 */
@Slf4j
public class EntryPointsRegistry implements IEntryPointsRegistry {
    private MeterRegistry meterRegistry;
    private Lock registrationLock = new ReentrantLock();

    public EntryPointsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private static final Map<String, Boolean> registeredNames = new HashMap<>();
    private final int maxDistinctEntryPointsCount = 2000;
    private AtomicInteger registeredNamesCount;

    @PostConstruct
    public void init() {
        registeredNamesCount = meterRegistry.gauge("EntryPoints.RegistrationsCount", new AtomicInteger());
    }

    @Override
    public boolean registerEntryPoint(String name) {
        registrationLock.lock();
        try {
            if (registeredNamesCount.get() >= maxDistinctEntryPointsCount) {
                return false;
            }
            if (registeredNames.containsKey(name)) {
                return true;
            }
            registeredNames.put(name, Boolean.TRUE);
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
