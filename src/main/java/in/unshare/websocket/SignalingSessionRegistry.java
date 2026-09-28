package in.unshare.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry pairing the two browser sockets of a share session by token.
 * In-memory only: ties signaling to a single backend instance, see architecture notes
 * on scalability. A later step can replace this with a shared pub/sub without touching
 * REST/domain code.
 */
@Component
public class SignalingSessionRegistry {

    private static final class PeerPair {
        private WebSocketSession sender;
        private WebSocketSession receiver;

        synchronized Optional<WebSocketSession> register(PeerRole role, WebSocketSession session) {
            if (role == PeerRole.SENDER) {
                this.sender = session;
            } else {
                this.receiver = session;
            }
            return Optional.ofNullable(role == PeerRole.SENDER ? receiver : sender);
        }

        synchronized Optional<WebSocketSession> unregister(PeerRole role, WebSocketSession session) {
            if (role == PeerRole.SENDER && sender == session) {
                sender = null;
            } else if (role == PeerRole.RECEIVER && receiver == session) {
                receiver = null;
            }
            return Optional.ofNullable(role == PeerRole.SENDER ? receiver : sender);
        }

        synchronized Optional<WebSocketSession> peerOf(PeerRole role) {
            return Optional.ofNullable(role == PeerRole.SENDER ? receiver : sender);
        }

        synchronized boolean isEmpty() {
            return sender == null && receiver == null;
        }
    }

    private final ConcurrentMap<String, PeerPair> pairsByToken = new ConcurrentHashMap<>();

    public Optional<WebSocketSession> register(String token, PeerRole role, WebSocketSession session) {
        PeerPair pair = pairsByToken.computeIfAbsent(token, t -> new PeerPair());
        return pair.register(role, session);
    }

    public Optional<WebSocketSession> unregister(String token, PeerRole role, WebSocketSession session) {
        PeerPair pair = pairsByToken.get(token);
        if (pair == null) {
            return Optional.empty();
        }
        Optional<WebSocketSession> peer = pair.unregister(role, session);
        pairsByToken.computeIfPresent(token, (t, p) -> p.isEmpty() ? null : p);
        return peer;
    }

    public Optional<WebSocketSession> peerOf(String token, PeerRole role) {
        PeerPair pair = pairsByToken.get(token);
        return pair == null ? Optional.empty() : pair.peerOf(role);
    }
}
