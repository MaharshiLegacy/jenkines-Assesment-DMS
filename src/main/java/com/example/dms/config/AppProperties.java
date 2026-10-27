package com.example.dms.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Security security, Jwt jwt) {

    public record Storage(String dir) {}

    public record Jwt(String secret, String issuer, long expiryMinutes) {}

    public record Security(List<String> allowedHosts, List<String> adminHosts, List<String> allowedOrigins) {
        public Security {
            allowedHosts = normalize(allowedHosts);
            adminHosts = normalize(adminHosts);
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }

        private static List<String> normalize(List<String> in) {
            return in == null ? List.of()
                    : in.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
        }
    }
}
