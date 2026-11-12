package com.yunshu.zhixue.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain portalChain(HttpSecurity http, JwtService jwtService, TokenService tokenService,
                                            RestAuthHandlers handlers) throws Exception {
        return chain(http, "/api/portal/**", "/api/portal/v1/auth/**",
                new JwtAuthenticationFilter(jwtService, tokenService, JwtService.AUDIENCE_PORTAL), handlers,
                "/api/portal/v1/subjects", "/api/portal/v1/subjects/*/stages",
                "/api/portal/v1/questions", "/api/portal/v1/questions/*",
                "/api/portal/v1/home/platform-stats", "/api/portal/v1/home/hot-stages");
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminChain(HttpSecurity http, JwtService jwtService, TokenService tokenService,
                                           RestAuthHandlers handlers) throws Exception {
        return chain(http, "/api/admin/**", "/api/admin/v1/auth/**",
                new JwtAuthenticationFilter(jwtService, tokenService, JwtService.AUDIENCE_ADMIN), handlers);
    }

    @Bean
    @Order(3)
    public SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/files/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    private SecurityFilterChain chain(HttpSecurity http, String matcher, String anonymous, JwtAuthenticationFilter filter,
                                      RestAuthHandlers handlers, String... publicGets) throws Exception {
        return http
                .securityMatcher(matcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll();
                    if (publicGets.length > 0) {
                        auth.requestMatchers(HttpMethod.GET, publicGets).permitAll();
                    }
                    auth.requestMatchers(anonymous).permitAll().anyRequest().authenticated();
                })
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
