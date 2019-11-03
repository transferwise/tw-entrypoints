package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.ExceptionUtils;

import java.util.List;
import java.util.concurrent.Callable;

public class EntryPoints {
    private final static ThreadLocal<EntryPointContext> contexts = new ThreadLocal<>();

    private final EntryPointContext unknownContext = new EntryPointContext("unknown") {
        @Override
        public EntryPointContext setName(String name) {
            // no-op
            return this;
        }
    };

    private final List<IEntryPointInterceptor> interceptors;

    public EntryPoints(List<IEntryPointInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void addInterceptor(IEntryPointInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public <T> T inEntryPointContext(String name, Callable<T> callable) {
        EntryPointContext currentContext = contexts.get();
        try {
            EntryPointContext context = new EntryPointContext(name);
            contexts.set(context);

            return ExceptionUtils.doUnchecked(() -> inEntryPointContext(context, callable, 0));
        } finally {
            contexts.set(currentContext);
        }
    }

    public void inEntryPointContext(String name, Runnable runnable) {
        inEntryPointContext(name, () -> {
            runnable.run();
            return null;
        });
    }

    public EntryPointContext currentContext() {
        return contexts.get();
    }

    public EntryPointContext currentContextOrUnknown() {
        EntryPointContext ctx = contexts.get();
        return ctx == null ? unknownContext : ctx;
    }

    private <T> T inEntryPointContext(EntryPointContext context, Callable<T> callable, int interceptorIdx) throws Exception {
        if (interceptorIdx >= interceptors.size()) {
            return callable.call();
        }
        return interceptors.get(interceptorIdx)
                           .inEntryPointContext(context, unknownContext, () -> inEntryPointContext(context, callable,
                                                                                                   interceptorIdx + 1));
    }
}
