package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.ExceptionUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EntryPointServletFilter extends OncePerRequestFilter {
    public static final String GENERIC_NAME = "servletRequest";

    private final EntryPoints entryPoints;

    public EntryPointServletFilter(EntryPoints entryPoints) {
        this.entryPoints = entryPoints;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        entryPoints.of("Web", GENERIC_NAME).execute(() -> {
            ExceptionUtils.doUnchecked(() -> filterChain.doFilter(request, response));
            return null;
        });
    }
}
