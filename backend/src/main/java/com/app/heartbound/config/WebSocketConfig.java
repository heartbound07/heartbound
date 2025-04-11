package com.app.heartbound.config;

import com.app.heartbound.config.security.JWTChannelInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final JWTChannelInterceptor jwtChannelInterceptor;

    @Autowired
    public WebSocketConfig(JWTChannelInterceptor jwtChannelInterceptor) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
        logger.debug("Initializing WebSocketConfig with JWTChannelInterceptor");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory broker for demonstration purposes.
        // All topics under "/topic" are handled by the broker.
        config.enableSimpleBroker("/topic");
        // All messages starting with /app are routed to message-handling methods.
        config.setApplicationDestinationPrefixes("/app");
        logger.info("Message Broker configured with application destination prefix '/qapp' and simple broker '/topic'");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the WebSocket endpoint and allow SockJS fallback.
        registry.addEndpoint("/ws")
                .setAllowedOrigins("${cors.allowed-origins}") // Use property instead of hardcoded value
                .withSockJS();
        logger.info("STOMP endpoint '/ws' registered with SockJS fallback and allowed origins: ${cors.allowed-origins}");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
        logger.debug("JWTChannelInterceptor attached to client inbound channel");
    }
}
