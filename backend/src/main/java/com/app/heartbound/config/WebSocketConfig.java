package com.app.heartbound.config;

import com.app.heartbound.config.security.JWTChannelInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    // Well defined namespace for party events
    public static final String PARTY_TOPIC = "/topic/party";
    public static final String PARTY_APP_DESTINATION = "/app/party";
    // Additional mapping for party updates to ensure consistency across controllers
    public static final String PARTY_UPDATE = "/party/update";
    public static final String MATCH_TOPIC = "/topic/matches";

    private final JWTChannelInterceptor jwtChannelInterceptor;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    
    @Value("${cors.origin.patterns:}")
    private String allowedOriginPatterns;

    public WebSocketConfig(JWTChannelInterceptor jwtChannelInterceptor) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
        logger.debug("Initializing WebSocketConfig with JWTChannelInterceptor");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for both general topics and user-specific destinations
        config.enableSimpleBroker("/topic", "/user");
        
        config.setApplicationDestinationPrefixes("/app");
        
        // Set user destination prefix for personal messages
        config.setUserDestinationPrefix("/user");
        
        logger.info("Message Broker configured with security enhancements");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/ws");
        
        // Configure allowed origins
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            String[] origins = allowedOrigins.split(",");
            endpoint.setAllowedOrigins(origins);
            logger.info("STOMP endpoint '/ws' registered with explicit origins: {}", allowedOrigins);
        }
        
        // Configure allowed origin patterns for dynamic URLs (like Vercel preview URLs)
        if (allowedOriginPatterns != null && !allowedOriginPatterns.trim().isEmpty()) {
            String[] patterns = allowedOriginPatterns.split(",");
            endpoint.setAllowedOriginPatterns(patterns);
            logger.info("STOMP endpoint '/ws' registered with origin patterns: {}", allowedOriginPatterns);
        }
        
        // If neither origins nor patterns are configured, allow all (for development only)
        if ((allowedOrigins == null || allowedOrigins.trim().isEmpty()) && 
            (allowedOriginPatterns == null || allowedOriginPatterns.trim().isEmpty())) {
            logger.warn("No CORS origins or patterns configured for WebSocket - allowing all origins (DEVELOPMENT ONLY)");
            endpoint.setAllowedOrigins("*");
        }
        
        endpoint.withSockJS()
                .setSessionCookieNeeded(false)
                // Configure SockJS options for better reliability
                .setClientLibraryUrl("https://cdn.jsdelivr.net/sockjs/1.6.1/sockjs.min.js")
                .setDisconnectDelay(30000)
                .setHeartbeatTime(25000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor)
                   .taskExecutor()
                   .corePoolSize(16)
                   .maxPoolSize(32)
                   .queueCapacity(500)
                   .keepAliveSeconds(120);
        
        logger.info("JWTChannelInterceptor attached with enhanced auth-optimized resource limits");
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                   .corePoolSize(16)
                   .maxPoolSize(32)
                   .queueCapacity(1000)
                   .keepAliveSeconds(120);
        
        logger.info("Outbound channel configured with enhanced message-optimized resource limits");
    }
}
