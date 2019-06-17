package com.transferwise.common.entrypoints;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EntryPointServletFilter extends OncePerRequestFilter {
    private final EntryPoints entryPoints;

    public EntryPointServletFilter(EntryPoints entryPoints) {
        this.entryPoints = entryPoints;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        // We could get too many entry points here, so for initial version, we will not use for example request.getRequestUri().toString() as name.
        // Currently Grails filter will later overwrite the name, as soon as we get controller and action information.
        // If we start seeing stats for "servletRequest", we may rethink it.
        entryPoints.inEntryPointContext("servletRequest", () -> {
            filterChain.doFilter(request, response);
            return null;
        });
    }
}
