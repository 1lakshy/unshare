package in.unshare.config;

import in.unshare.service.ShareSessionService;
import in.unshare.websocket.SignalingHandshakeInterceptor;
import in.unshare.websocket.SignalingSessionRegistry;
import in.unshare.websocket.SignalingWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingSessionRegistry signalingSessionRegistry;
    private final ShareSessionService shareSessionService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public WebSocketConfig(SignalingSessionRegistry signalingSessionRegistry,
                            ShareSessionService shareSessionService,
                            ObjectMapper objectMapper,
                            AppProperties appProperties) {
        this.signalingSessionRegistry = signalingSessionRegistry;
        this.shareSessionService = shareSessionService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingWebSocketHandler(), "/ws/signaling/*")
                .addInterceptors(new SignalingHandshakeInterceptor(shareSessionService))
                .setAllowedOrigins(appProperties.cors().allowedOrigins().toArray(String[]::new));
    }

    private SignalingWebSocketHandler signalingWebSocketHandler() {
        return new SignalingWebSocketHandler(signalingSessionRegistry, objectMapper);
    }
}
