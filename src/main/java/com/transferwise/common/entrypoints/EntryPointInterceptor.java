package com.transferwise.common.entrypoints;

import java.util.concurrent.Callable;

public interface EntryPointInterceptor {
    <T> T inEntryPointContext(EntryPointContext context, EntryPointContext unknownContext, Callable<T> callable) throws Exception;
}
