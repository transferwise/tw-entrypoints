package com.transferwise.common.entrypoints;

public interface IEntryPointsRegistry {
    boolean registerEntryPoint(String group, String name);

    boolean registerEntryPoint(EntryPointContext context);
}
