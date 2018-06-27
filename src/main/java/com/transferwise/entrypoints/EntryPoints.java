package com.transferwise.entrypoints;

import com.transferwise.common.utils.ExceptionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class EntryPoints {
    private ThreadLocal<EntryPointContext> contexts = new ThreadLocal<>();

    private EntryPointContext unknownContext = new EntryPointContext("unknown") {
        @Override
        public EntryPointContext setName(String name) {
            // no-op
            return this;
        }
    };

    private List<EntryPointInterceptor> interceptors = new ArrayList<>();

    public EntryPoints(List<EntryPointInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void addInterceptor(EntryPointInterceptor interceptor){
        interceptors.add(interceptor);
    }

    public <T> T inEntryPointContext(String name, Callable<T> callable) {
        EntryPointContext currentContext = contexts.get();
        try {
            EntryPointContext context = new EntryPointContext(name);
            contexts.set(context);

            return ExceptionUtils.callUnchecked(() -> inEntryPointContext(context, callable, 0));
        } finally {
            contexts.set(currentContext);
        }
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
        return interceptors.get(interceptorIdx).inEntryPointContext(context, unknownContext, () -> {
            return inEntryPointContext(context, callable, interceptorIdx + 1);
        });
    }
}
