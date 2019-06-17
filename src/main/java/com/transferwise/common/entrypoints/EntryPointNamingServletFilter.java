package com.transferwise.common.entrypoints;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EntryPointNamingServletFilter extends OncePerRequestFilter {
    private final EntryPoints entryPoints;

    public EntryPointNamingServletFilter(EntryPoints entryPoints) {
        this.entryPoints = entryPoints;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        EntryPointContext context = entryPoints.currentContext();
        if (context != null) {
            Object mapping = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (mapping != null) {
                context.setName(String.valueOf(mapping));
            }
        }
        filterChain.doFilter(request, response);
    }
}
