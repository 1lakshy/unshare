package in.unshare.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Optional;

public class SignalingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingWebSocketHandler.class);

    public static final String TOKEN_ATTRIBUTE = "shareToken";
    public static final String ROLE_ATTRIBUTE = "peerRole";

    private final SignalingSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public SignalingWebSocketHandler(SignalingSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = token(session);
        PeerRole role = role(session);

        Optional<WebSocketSession> peer = registry.register(token, role, session);
        send(session, SignalingMessageType.JOIN, JsonNodeFactory.instance.objectNode().put("role", role.name()));

        peer.ifPresent(peerSession -> {
            send(peerSession, SignalingMessageType.PEER_JOINED, JsonNodeFactory.instance.objectNode().put("role", role.name()));
            send(session, SignalingMessageType.PEER_JOINED, JsonNodeFactory.instance.objectNode().put("role", role.other().name()));
        });

        log.info("Signaling peer connected token={} role={}", token, role);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String token = token(session);
        PeerRole role = role(session);
        SignalingMessage parsed;
        try {
            parsed = objectMapper.readValue(message.getPayload(), SignalingMessage.class);
        } catch (Exception e) {
            log.warn("Discarding malformed signaling message token={} role={}", token, role);
            send(session, SignalingMessageType.ERROR, JsonNodeFactory.instance.objectNode().put("reason", "malformed_message"));
            return;
        }

        if (parsed.type() == SignalingMessageType.LEAVE) {
            session.close(CloseStatus.NORMAL);
            return;
        }

        registry.peerOf(token, role).ifPresentOrElse(
                peer -> forward(peer, parsed),
                () -> send(session, SignalingMessageType.ERROR, JsonNodeFactory.instance.objectNode().put("reason", "peer_not_connected"))
        );
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String token = token(session);
        PeerRole role = role(session);

        registry.unregister(token, role, session).ifPresent(peer ->
                send(peer, SignalingMessageType.PEER_LEFT, JsonNodeFactory.instance.objectNode().put("role", role.name())));

        log.info("Signaling peer disconnected token={} role={} status={}", token, role, status);
    }

    private void forward(WebSocketSession peer, SignalingMessage message) {
        send(peer, message.type(), message.payload());
    }

    private void send(WebSocketSession session, SignalingMessageType type, JsonNode payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new SignalingMessage(type, payload))));
            }
        } catch (IOException e) {
            log.warn("Failed to deliver signaling message to session {}", session.getId(), e);
        }
    }

    private String token(WebSocketSession session) {
        return (String) session.getAttributes().get(TOKEN_ATTRIBUTE);
    }

    private PeerRole role(WebSocketSession session) {
        return (PeerRole) session.getAttributes().get(ROLE_ATTRIBUTE);
    }
}
