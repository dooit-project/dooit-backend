package com.todolab.config;

import com.todolab.auth.config.AuthJwtProperties;
import com.todolab.auth.config.GuestAccountRateLimitProperties;
import com.todolab.auth.security.ApiAccessDeniedHandler;
import com.todolab.auth.security.ApiAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
        AuthJwtProperties.class,
        DocumentationProperties.class,
        GuestAccountRateLimitProperties.class
})
public class SecurityConfig {

    static final String[] DOCUMENTATION_MATCHERS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/scalar.html"
    };

    static final String[] ACTUATOR_HEALTH_MATCHERS = {
            "/actuator/health",
            "/actuator/health/**"
    };

    static final String[] NON_API_PUBLIC_MATCHERS = ACTUATOR_HEALTH_MATCHERS;

    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;
    private final DocumentationProperties documentationProperties;

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/v1/auth/guest",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain nonApiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicNonApiMatchers()).permitAll()
                        .anyRequest().denyAll()
                )
                .build();
    }

    private String[] publicNonApiMatchers() {
        if (!documentationProperties.publicEnabled()) {
            return NON_API_PUBLIC_MATCHERS;
        }

        String[] matchers = new String[NON_API_PUBLIC_MATCHERS.length + DOCUMENTATION_MATCHERS.length];
        System.arraycopy(NON_API_PUBLIC_MATCHERS, 0, matchers, 0, NON_API_PUBLIC_MATCHERS.length);
        System.arraycopy(
                DOCUMENTATION_MATCHERS,
                0,
                matchers,
                NON_API_PUBLIC_MATCHERS.length,
                DOCUMENTATION_MATCHERS.length
        );
        return matchers;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(AuthJwtProperties authJwtProperties) {
        byte[] secretBytes = authJwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret은 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
    }
}
