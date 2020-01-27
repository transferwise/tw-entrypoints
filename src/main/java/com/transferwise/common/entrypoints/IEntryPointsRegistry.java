package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.context.TwContext;

public interface IEntryPointsRegistry {
    boolean registerEntryPoint(String group, String name);

    boolean registerEntryPoint(TwContext context);
}
