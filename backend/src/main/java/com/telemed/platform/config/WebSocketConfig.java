package com.telemed.platform.config;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.telemed.platform.teleconsultation.service.TeleconsultationAccessService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<String> allowedOrigins;
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final TeleconsultationAccessService accessService;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String configuredOrigins,
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            TeleconsultationAccessService accessService) {
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalStateException("WebSocket must be configured with explicit allowed origins.");
        }
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.accessService = accessService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }
                if (accessor.getCommand() == StompCommand.CONNECT) {
                    authenticate(accessor);
                } else if (accessor.getCommand() == StompCommand.SUBSCRIBE
                        || accessor.getCommand() == StompCommand.MESSAGE) {
                    authorizeDestination(accessor);
                }
                return message;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(15_000);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("An access token is required.");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(7));
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            if (authentication == null) {
                throw new AccessDeniedException("The access token is invalid.");
            }
            accessor.setUser(authentication);
        } catch (JwtException exception) {
            throw new AccessDeniedException("The access token is invalid.", exception);
        }
    }

    private void authorizeDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        String appointmentId = appointmentIdFromDestination(accessor.getCommand(), destination);
        if (appointmentId == null || accessor.getUser() == null) {
            throw new AccessDeniedException("This WebSocket destination is not permitted.");
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String email = accessor.getUser().getName();
            if (accessor.getUser() instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                email = token.getToken().getClaimAsString("email");
            }
            if (!StringUtils.hasText(email)) {
                throw new AccessDeniedException("An authenticated participant is required.");
            }
            accessService.assertCanJoin(UUID.fromString(appointmentId), email);
        }
    }

    private String appointmentIdFromDestination(StompCommand command, String destination) {
        if (!StringUtils.hasText(destination)) {
            return null;
        }
        String prefix = command == StompCommand.SUBSCRIBE
                ? "/topic/teleconsultations/"
                : "/app/teleconsultations/";
        if (!destination.startsWith(prefix)) {
            return null;
        }
        String suffix = destination.substring(prefix.length());
        String[] parts = suffix.split("/");
        if ((command == StompCommand.SUBSCRIBE && parts.length != 1)
                || (command == StompCommand.MESSAGE
                        && (parts.length != 2
                                || !List.of("join", "leave", "signal").contains(parts[1])))) {
            return null;
        }
        try {
            UUID.fromString(parts[0]);
            return parts[0];
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
