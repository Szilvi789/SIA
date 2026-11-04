package com.personalai.platform.privacy;

import com.personalai.platform.configuration.RuntimeDataSettings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects stale browser sessions before reading a task body after the backend mode changes. */
@Component
public class RuntimeModeFilter extends OncePerRequestFilter {
    private final String mode;
    public RuntimeModeFilter(RuntimeDataSettings settings) { this.mode = settings.mode().name().toLowerCase(Locale.ROOT); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = request.getHeader("X-SAI-Mode");
        String path = request.getRequestURI();
        boolean discovery = path.equals("/api/runtime-config") || path.equals("/api/health");
        if (path.startsWith("/api/") && !discovery && expected != null && !mode.equals(expected)) {
            response.setStatus(409); response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Workspace mode changed\",\"status\":409,\"detail\":\"The backend mode changed. Export unsaved work and reload before using this environment.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
