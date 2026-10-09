package com.controlcenter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds browser security headers to every response. The dashboard has state-changing buttons
 * (deploy, roll back, mark status), so no other site may frame it to trick a click
 * (clickjacking). Links to repositories and environment URLs do not leak the dashboard's
 * internal address as a referrer.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        chain.doFilter(request, response);
    }
}
