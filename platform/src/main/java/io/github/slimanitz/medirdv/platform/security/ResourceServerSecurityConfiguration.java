package io.github.slimanitz.medirdv.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Every service is an OAuth2 resource server: it validates the Keycloak JWT relayed by the
 * gateway (defence in depth, the gateway is not the only gate). Actuator runs on a separate
 * management port that is not exposed outside the cluster / compose network; on it only health
 * and the Prometheus scrape endpoint are anonymous.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@Import(AccessDeniedProblemAdvice.class)
public class ResourceServerSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${management.server.port:-1}") int managementPort)
            throws Exception {
        RequestMatcher health = request -> request.getRequestURI().startsWith("/actuator/health");
        RequestMatcher prometheusOnManagementPort = request -> onPort(request, managementPort)
                && request.getRequestURI().equals("/actuator/prometheus");
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(health, prometheusOnManagementPort).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRolesConverter())))
                .build();
    }

    private static boolean onPort(HttpServletRequest request, int port) {
        return port > 0 && request.getLocalPort() == port;
    }
}
