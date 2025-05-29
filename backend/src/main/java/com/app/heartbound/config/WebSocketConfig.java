package com.app.heartbound.config;

import com.app.heartbound.config.security.JWTChannelInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    // Add queue-specific topics
    public static final String QUEUE_TOPIC = "/topic/queue";
    public static final String MATCH_TOPIC = "/topic/matches";

    private final JWTChannelInterceptor jwtChannelInterceptor;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Autowired
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
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*", "http://127.0.0.1:*")
                .withSockJS()
                .setSessionCookieNeeded(false); // Disable session cookies for stateless operation
        
        logger.info("STOMP endpoint '/ws' registered with enhanced security");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor)
                   .taskExecutor()
                   .corePoolSize(4)
                   .maxPoolSize(8)
                   .queueCapacity(100);
        
        logger.debug("JWTChannelInterceptor attached with resource limits");
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure outbound channel with limits
        registration.taskExecutor()
                   .corePoolSize(4)
                   .maxPoolSize(8)
                   .queueCapacity(100);
    }
}
