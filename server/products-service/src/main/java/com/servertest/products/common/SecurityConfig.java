package com.servertest.products.common;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The products service is an OAuth2 resource server: every request must carry a JWT issued by
 * Keycloak for the {@code products-service} audience (checked in application.yml). Tokens are
 * validated locally against Keycloak's public keys, so no call to Keycloak happens per request.
 *
 * <p>Permissions come from the token's {@code scope} claim (exposed as {@code SCOPE_*} authorities):
 * <ul>
 *   <li>{@code products:read}: GET</li>
 *   <li>{@code products:write}: POST, PUT, PATCH, DELETE</li>
 * </ul>
 * The same rules apply to users (web, iOS) and to other services (client credentials).
 */
@Configuration
public class SecurityConfig {

    static final String READ = "SCOPE_products:read";
    static final String WRITE = "SCOPE_products:write";

    @Bean
    SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
        http
            // Bearer tokens only: no cookies, no sessions, so CSRF does not apply.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Let error dispatches render the original status instead of becoming 401/403.
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/hello").permitAll()
                .requestMatchers("/api/products/actuator/health", "/api/products/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").hasAuthority(READ)
                .requestMatchers(HttpMethod.POST, "/api/products", "/api/products/**").hasAuthority(WRITE)
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAuthority(WRITE)
                .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasAuthority(WRITE)
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAuthority(WRITE)
                .anyRequest().denyAll())
            .oauth2ResourceServer(rs -> rs
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(problemEntryPoint(json))
                .accessDeniedHandler(problemAccessDenied(json)));
        return http.build();
    }

    /** 401 as ProblemDetail JSON, keeping the standard {@code WWW-Authenticate: Bearer ...} header. */
    private static AuthenticationEntryPoint problemEntryPoint(ObjectMapper json) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) -> {
            bearer.commence(req, res, ex);
            write(json, res, HttpStatus.UNAUTHORIZED, "A valid bearer token is required");
        };
    }

    /** 403 as ProblemDetail JSON, keeping the standard {@code WWW-Authenticate: Bearer error="insufficient_scope"}. */
    private static AccessDeniedHandler problemAccessDenied(ObjectMapper json) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) -> {
            bearer.handle(req, res, ex);
            write(json, res, HttpStatus.FORBIDDEN, "The token does not grant the scope this request needs");
        };
    }

    private static void write(ObjectMapper json, HttpServletResponse res, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(res.getOutputStream(), problem);
    }
}
