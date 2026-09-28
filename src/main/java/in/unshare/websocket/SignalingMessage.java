package in.unshare.websocket;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Opaque relay envelope. The backend routes on {@code type} only and never
 * interprets {@code payload} — SDP offers/answers and ICE candidates pass
 * through verbatim between the two peers of a share session.
 */
public record SignalingMessage(SignalingMessageType type, JsonNode payload) {
}
