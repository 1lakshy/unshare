package in.unshare.websocket;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SignalingSessionRegistryTest {

    private final SignalingSessionRegistry registry = new SignalingSessionRegistry();

    @Test
    void senderRegistersFirstWithNoPeerYet() {
        WebSocketSession sender = Mockito.mock(WebSocketSession.class);

        Optional<WebSocketSession> peer = registry.register("tok", PeerRole.SENDER, sender);

        assertThat(peer).isEmpty();
    }

    @Test
    void secondPeerToRegisterSeesTheFirst() {
        WebSocketSession sender = Mockito.mock(WebSocketSession.class);
        WebSocketSession receiver = Mockito.mock(WebSocketSession.class);

        registry.register("tok", PeerRole.SENDER, sender);
        Optional<WebSocketSession> peerOfReceiver = registry.register("tok", PeerRole.RECEIVER, receiver);
        Optional<WebSocketSession> peerOfSender = registry.peerOf("tok", PeerRole.SENDER);

        assertThat(peerOfReceiver).contains(sender);
        assertThat(peerOfSender).contains(receiver);
    }

    @Test
    void unregisterRemovesOnlyMatchingSessionAndReportsRemainingPeer() {
        WebSocketSession sender = Mockito.mock(WebSocketSession.class);
        WebSocketSession receiver = Mockito.mock(WebSocketSession.class);
        registry.register("tok", PeerRole.SENDER, sender);
        registry.register("tok", PeerRole.RECEIVER, receiver);

        Optional<WebSocketSession> remainingPeer = registry.unregister("tok", PeerRole.SENDER, sender);

        assertThat(remainingPeer).contains(receiver);
        assertThat(registry.peerOf("tok", PeerRole.RECEIVER)).isEmpty();
    }

    @Test
    void unknownTokenYieldsNoPeer() {
        assertThat(registry.peerOf("does-not-exist", PeerRole.SENDER)).isEmpty();
    }
}
