package com.transferwise.common.entrypoints;

import java.util.function.Supplier;

public interface IEntryPointInterceptor {
    <T> T inEntryPointContext(Supplier<T> supplier);
}
