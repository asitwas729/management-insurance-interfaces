package com.example.interfacehub.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final boolean openEndpointsForTest;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        @Value("${interfacehub.security.open-endpoints-for-test:false}") boolean openEndpointsForTest
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.openEndpointsForTest = openEndpointsForTest;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedEntryPoint()))
            .authorizeHttpRequests(auth -> {
                if (openEndpointsForTest) {
                    auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/dlq/replay-requests/*/approve").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/dlq/replay-requests/*/reject").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/dlq/replay-requests/*/execute").hasRole("APPROVER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/retries/*/approve").hasAnyRole("APPROVER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/retries/*/reject").hasAnyRole("APPROVER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/retries/*/execute").hasAnyRole("APPROVER", "ADMIN")
                        .anyRequest().permitAll();
                    return;
                }
                auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                .requestMatchers("/", "/index.html", "/app.css", "/app.js").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/metrics/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/audit-logs/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/standards/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/standards/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/standards/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/interfaces/*/configs/*/publish").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/retries/*/approve").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/retries/*/reject").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/retries/*/execute").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/dlq/replay-requests/*/approve").hasRole("APPROVER")
                .requestMatchers(HttpMethod.POST, "/api/v1/dlq/replay-requests/*/reject").hasRole("APPROVER")
                .requestMatchers(HttpMethod.POST, "/api/v1/dlq/replay-requests/*/execute").hasRole("APPROVER")
                .requestMatchers("/api/v1/incidents/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/schedules/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers("/api/v1/**").hasAnyRole("OPERATOR", "APPROVER", "ADMIN")
                .anyRequest().authenticated();
            })
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> response.sendError(401, "Unauthorized");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
