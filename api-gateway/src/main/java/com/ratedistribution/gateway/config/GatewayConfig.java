package com.ratedistribution.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GatewayConfig is a configuration class for setting up routes and route filters in the Spring Cloud Gateway.
 * It also defines role-based access control using the {@link AuthGatewayFilterFactory}.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Configuration
public class GatewayConfig {
    @Value("${route.auth}")
    private String authUri;

    @Value("${route.user}")
    private String userUri;

    @Value("${route.rest}")
    private String restUri;

    @Value("${circuit-breaker-name}")
    private String circuitBreakerName;

    private static final String ADMIN = "ADMIN";
    private static final String OPERATOR = "OPERATOR";
    private static final String ANALYST = "ANALYST";
    private final Map<String, List<String>> endpointRoleMapping = new HashMap<>();

    /**
     * Constructs a GatewayConfig object and sets up the role mappings for various endpoints.
     * Role mappings define which user roles are allowed to access specific endpoints.
     */
    public GatewayConfig() {
        this.endpointRoleMapping.put("/api/rates", List.of(ADMIN, OPERATOR));
        this.endpointRoleMapping.put("/users", List.of(ADMIN));
        this.endpointRoleMapping.put("/users/me", List.of(ANALYST, OPERATOR, ADMIN));
        this.endpointRoleMapping.put("/users/update/me", List.of(ANALYST, OPERATOR, ADMIN));
        this.endpointRoleMapping.put("/auth/refresh", List.of(ANALYST, OPERATOR, ADMIN));
        this.endpointRoleMapping.put("/auth/logout", List.of(ANALYST, OPERATOR, ADMIN));
        this.endpointRoleMapping.put("/auth/change-password", List.of(ANALYST, OPERATOR, ADMIN));
    }

    /**
     * Defines the routes for the application, setting up path matching, role-based authorization filters,
     * and circuit breakers with fallback URIs for different services (auth, user management, patient, report).
     *
     * @param builder                  the RouteLocatorBuilder for defining routes and their filters.
     * @param authGatewayFilterFactory the custom filter factory for JWT validation and role-based authorization.
     * @return the constructed RouteLocator containing all routes and their configurations.
     */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, AuthGatewayFilterFactory authGatewayFilterFactory) {
        return builder.routes()
                .route("auth-service", r -> r.path("/auth/**")
                        .filters(f -> f
                                .filter(authGatewayFilterFactory.apply(new AuthGatewayFilterFactory.Config().setRoleMapping(endpointRoleMapping)))
                                .circuitBreaker(c -> c.setName(this.circuitBreakerName).setFallbackUri("forward:/fallback/auth"))
                        )
                        .uri(this.authUri))

                .route("user-management-service", r -> r.path("/users/**")
                        .filters(f -> f
                                .filter(authGatewayFilterFactory.apply(new AuthGatewayFilterFactory.Config().setRoleMapping(endpointRoleMapping)))
                                .circuitBreaker(c -> c.setName(this.circuitBreakerName).setFallbackUri("forward:/fallback/user"))
                        )
                        .uri(this.userUri))

                .route("rest-data-provider", r -> r.path("/api/rates/**")
                        .filters(f -> f
                                .filter(authGatewayFilterFactory.apply(new AuthGatewayFilterFactory.Config().setRoleMapping(endpointRoleMapping)))
                                .circuitBreaker(c -> c.setName(this.circuitBreakerName).setFallbackUri("forward:/fallback/rest"))
                        )
                        .uri(this.restUri))

                .build();
    }
}
