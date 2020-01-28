package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.common.baseutils.context.TwContext;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EntryPointServletFilter extends OncePerRequestFilter {
    public static final String GENERIC_NAME = "servletRequest";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        TwContext.newSubContext().asEntryPoint("Web", GENERIC_NAME).execute(() -> {
            ExceptionUtils.doUnchecked(() -> filterChain.doFilter(request, response));
            return null;
        });
    }
}
