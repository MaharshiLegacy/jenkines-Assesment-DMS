package com.example.dms.security;

import com.example.dms.config.AppProperties;
import com.example.dms.exception.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Domain restriction. Runs before authentication, so a request arriving through an unapproved
 * domain is rejected before any credentials are even checked.
 *
 *  - every /api/** call must arrive through one of app.security.allowed-hosts
 *  - /api/admin/** must arrive through one of the (stricter) app.security.admin-hosts
 *
 * Fail-closed: an empty list denies everything. Wildcards like "*.dms.local" are supported.
 * Note: the Host header is client-controlled, so in production ALSO enforce this at the
 * reverse proxy / gateway and let it set X-Forwarded-Host (see README).
 */
@Slf4j
public class DomainRestrictionFilter extends OncePerRequestFilter {

    private final AppProperties.Security config;
    private final ApiErrorWriter errors;

    public DomainRestrictionFilter(AppProperties.Security config, ApiErrorWriter errors) {
        this.config = config;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String host = req.getServerName().toLowerCase(Locale.ROOT);
        String uri = req.getRequestURI();
        boolean adminArea = uri.equals("/api/admin") || uri.startsWith("/api/admin/");

        List<String> permitted = adminArea ? config.adminHosts() : config.allowedHosts();
        if (!matches(host, permitted)) {
            log.warn("Blocked request to {} via unapproved host '{}'", uri, host);
            errors.write(req, res, HttpStatus.FORBIDDEN, "Access through this domain is not permitted for this resource");
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean matches(String host, List<String> patterns) {
        for (String p : patterns) {
            if (p.startsWith("*.") ? host.endsWith(p.substring(1)) : host.equals(p)) return true;
        }
        return false;
    }
}
