package in.unshare.websocket;

import in.unshare.exception.ShareSessionExpiredException;
import in.unshare.exception.ShareSessionNotFoundException;
import in.unshare.service.ShareSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Validates that a share session exists and is joinable before a signaling
 * socket is accepted, and stashes the token/role as session attributes.
 */
public class SignalingHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SignalingHandshakeInterceptor.class);

    private final ShareSessionService shareSessionService;

    public SignalingHandshakeInterceptor(ShareSessionService shareSessionService) {
        this.shareSessionService = shareSessionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String token = path.substring(path.lastIndexOf('/') + 1);
        String roleParam = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("role");

        if (token.isBlank() || roleParam == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
            return false;
        }

        PeerRole role;
        try {
            role = PeerRole.valueOf(roleParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
            return false;
        }

        try {
            shareSessionService.getByToken(token);
        } catch (ShareSessionNotFoundException e) {
            response.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
            return false;
        } catch (ShareSessionExpiredException e) {
            response.setStatusCode(org.springframework.http.HttpStatus.GONE);
            return false;
        }

        attributes.put(SignalingWebSocketHandler.TOKEN_ATTRIBUTE, token);
        attributes.put(SignalingWebSocketHandler.ROLE_ATTRIBUTE, role);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.warn("Signaling handshake failed", exception);
        }
    }
}
