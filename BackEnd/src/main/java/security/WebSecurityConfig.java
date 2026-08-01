package com.haris.SpringEcom.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
// RBAC: This annotation UNLOCKS @PreAuthorize on our controllers.
// Without it, Spring ignores all @PreAuthorize annotations silently!
@EnableMethodSecurity
public class WebSecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(Customizer.withDefaults()) // Tells Spring Security to use the CorsConfigurationSource bean below
                .csrf(csrfConfig -> csrfConfig.disable())
                .sessionManagement(sessionConfig ->
                        sessionConfig.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints — no token needed
                        .requestMatchers("/auth/**").permitAll()
                        // Public product browsing — no token needed (customers browse before login)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/product/**").permitAll()
                        // Everything else (place order, add/update/delete product) requires login
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oAuth2 -> oAuth2
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 error: {}", exception.getMessage());
                    handlerExceptionResolver.resolveException(request, response, null, exception);
                })
                .successHandler(oAuth2SuccessHandler)
        );
//        formLogin():|
        return httpSecurity.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Specifically allow your Vite frontend origin
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        // Allow all standard HTTP methods, plus OPTIONS for preflight requests
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allow standard headers that your frontend will send
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Required if you are sending cookies or using OAuth2/Sessions
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this configuration to all API routes
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

