package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.context.TwContext;

import java.util.List;
import java.util.function.Supplier;

public class EntryPoints {
    public static final String NAME_UNKNOWN = "unknown";

    private final List<IEntryPointInterceptor> interceptors;

    public EntryPoints(List<IEntryPointInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void addInterceptor(IEntryPointInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public Builder of(String group, String name) {
        return new DefaultBuilder(this, group, name);
    }

    public interface Builder {
        <T> T execute(Supplier<T> supplier);

        void execute(Runnable runnable);
    }

    public static class DefaultBuilder implements Builder {
        private EntryPoints entryPoints;
        private String group;
        private String name;

        public DefaultBuilder(EntryPoints entryPoints, String group, String name) {
            this.entryPoints = entryPoints;
            this.group = group;
            this.name = name;
        }

        @Override
        public <T> T execute(Supplier<T> supplier) {
            TwContext twContext = TwContext.subContext().asEntryPoint(group, name);
            return twContext.execute(() -> inEntryPointContext(supplier, 0));
        }

        @Override
        public void execute(Runnable runnable) {
            execute(() -> {
                runnable.run();
                return null;
            });
        }

        private <T> T inEntryPointContext(Supplier<T> supplier, int interceptorIdx) {
            if (interceptorIdx >= entryPoints.interceptors.size()) {
                return supplier.get();
            }
            return entryPoints.interceptors.get(interceptorIdx)
                                           .inEntryPointContext(
                                               () -> inEntryPointContext(supplier, interceptorIdx + 1));
        }
    }
}
