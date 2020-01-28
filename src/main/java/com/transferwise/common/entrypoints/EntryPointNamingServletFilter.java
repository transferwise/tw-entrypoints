package com.transferwise.common.entrypoints;

import com.transferwise.common.baseutils.context.TwContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EntryPointNamingServletFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        TwContext context = TwContext.current();
        if (context == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean nameSet = false;

        Object mapping = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (mapping != null) {
            context.replaceValueDeep(TwContext.NAME_KEY, EntryPointServletFilter.GENERIC_NAME, String.valueOf(mapping));
            nameSet = true;
        }

        filterChain.doFilter(request, response);

        if (!nameSet) {
            mapping = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (mapping != null) {
                context.replaceValueDeep(TwContext.NAME_KEY, EntryPointServletFilter.GENERIC_NAME,
                                         String.valueOf(mapping));
            }
        }
    }
}
