package com.app.heartbound.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Configuration
@EnableMethodSecurity // Enable method-level security for @PreAuthorize annotations
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JWTTokenProvider jwtTokenProvider;
    
    @Autowired
    private RateLimitingFilter rateLimitingFilter;
    
    @Autowired
    private SecurityHeadersFilter securityHeadersFilter;
    
    @Autowired
    private CorsValidationFilter corsValidationFilter;
    
    @Autowired
    private CorsConfigurationProvider corsConfigurationProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Use the enhanced CORS configuration from CorsConfigurationProvider
        CorsConfiguration configuration = corsConfigurationProvider.createCorsConfiguration();
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this CORS configuration to all endpoints
        source.registerCorsConfiguration("/**", configuration);
        
        logger.info("CORS configuration source created with {} allowed origins", 
                   configuration.getAllowedOrigins() != null ? configuration.getAllowedOrigins().size() : 0);
        
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Use the global CORS configuration defined above
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Permit swagger and OpenAPI endpoints
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/swagger-ui/**", "/api/v3/api-docs/**").permitAll()
                // Permit the root, error, and WebSocket handshake endpoints
                .requestMatchers("/", "/error", "/ws/**").permitAll()
                // Permit static resources
                .requestMatchers("/images/**", "/css/**", "/js/**").permitAll()
                // Permit endpoints for OAuth & authentication controllers
                .requestMatchers("/auth/**", "/oauth2/**", "/api/auth/**", "/auth/discord/authorize", "/oauth2/callback/discord").permitAll()
                // Permit user profile endpoints - updated paths
                .requestMatchers(HttpMethod.GET, "/users/*/profile", "/users/profiles").permitAll()
                .requestMatchers(HttpMethod.PUT, "/users/*/profile").authenticated()
                // Add explicit permission for the credits endpoint
                .requestMatchers(HttpMethod.PATCH, "/users/*/credits").hasRole("ADMIN")
                
                // Role-based security for admin endpoints
                .requestMatchers("/admin/**", "/shop/admin/**").hasRole("ADMIN")
                // Moderator endpoints available to admins and moderators
                .requestMatchers("/moderation/**").hasAnyRole("ADMIN", "MODERATOR")
                // Monarch (premium) features
                .requestMatchers("/premium/**").hasAnyRole("ADMIN", "MONARCH")
                
                // Shop endpoint permissions
                .requestMatchers(HttpMethod.GET, "/api/shop/items/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/shop/purchase/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/shop/inventory").authenticated()
                
                // Inventory endpoints
                .requestMatchers("/inventory/**").authenticated()

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            // Add security headers filter first
            .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
            // Add CORS validation filter after security headers
            .addFilterBefore(corsValidationFilter, UsernamePasswordAuthenticationFilter.class)
            // Add rate limiting filter before JWT authentication filter
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            // Add JWT authentication filter
            .addFilterBefore(new JWTAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
