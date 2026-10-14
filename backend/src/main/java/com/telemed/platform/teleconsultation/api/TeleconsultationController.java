package com.telemed.platform.teleconsultation.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.telemed.platform.teleconsultation.service.TeleconsultationAccessService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

@Controller
public class TeleconsultationController {

    private static final int MAX_SIGNAL_PAYLOAD_LENGTH = 100_000;
    private static final int MAX_CHAT_LENGTH = 2_000;

    private final SimpMessagingTemplate messagingTemplate;
    private final TeleconsultationAccessService accessService;
    private final Clock clock;

    public TeleconsultationController(
            SimpMessagingTemplate messagingTemplate,
            TeleconsultationAccessService accessService,
            Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.accessService = accessService;
        this.clock = clock;
    }

    @MessageMapping("/teleconsultations/{appointmentId}/join")
    public void join(@DestinationVariable UUID appointmentId, Principal principal) {
        String email = emailFrom(principal);
        accessService.assertCanJoin(appointmentId, email);
        broadcast(appointmentId, principal, TeleconsultationSignalType.JOIN, NullNode.getInstance());
    }

    @MessageMapping("/teleconsultations/{appointmentId}/leave")
    public void leave(@DestinationVariable UUID appointmentId, Principal principal) {
        String email = emailFrom(principal);
        accessService.assertCanJoin(appointmentId, email);
        broadcast(appointmentId, principal, TeleconsultationSignalType.LEAVE, NullNode.getInstance());
    }

    @MessageMapping("/teleconsultations/{appointmentId}/signal")
    public void signal(
            @DestinationVariable UUID appointmentId,
            @Valid TeleconsultationSignalRequest request,
            Principal principal) {
        String email = emailFrom(principal);
        accessService.assertCanJoin(appointmentId, email);
        if (request.type() != TeleconsultationSignalType.OFFER
                && request.type() != TeleconsultationSignalType.ANSWER
                && request.type() != TeleconsultationSignalType.ICE_CANDIDATE
                && request.type() != TeleconsultationSignalType.CHAT) {
            throw new AccessDeniedException("This signal type is not permitted.");
        }
        JsonNode payload = request.payload();
        if (payload == null) {
            throw new AccessDeniedException("The signal payload is invalid.");
        }
        if (payload.toString().length() > MAX_SIGNAL_PAYLOAD_LENGTH) {
            throw new AccessDeniedException("The signal payload is too large.");
        }
        if (request.type() == TeleconsultationSignalType.CHAT
                && (!payload.isTextual()
                        || payload.asText().isBlank()
                        || payload.asText().length() > MAX_CHAT_LENGTH)) {
            throw new AccessDeniedException("The chat message is invalid.");
        }
        broadcast(appointmentId, principal, request.type(), payload);
    }

    private String emailFrom(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("An authenticated participant is required.");
        }
        return token.getToken().getClaimAsString("email");
    }

    private void broadcast(
            UUID appointmentId,
            Principal principal,
            TeleconsultationSignalType type,
            JsonNode payload) {
        String senderId = ((JwtAuthenticationToken) principal).getToken().getSubject();
        messagingTemplate.convertAndSend(
                "/topic/teleconsultations/" + appointmentId,
                new TeleconsultationRoomEvent(appointmentId, senderId, type, payload, clock.instant()));
    }
}
