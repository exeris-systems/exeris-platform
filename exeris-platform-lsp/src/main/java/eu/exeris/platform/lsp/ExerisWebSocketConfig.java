package eu.exeris.platform.lsp;

import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Where {@link ExerisWebSocketEndpoint} is published, in one place.
 *
 * <p>The path is a constant rather than a literal because three things have to agree on it: the
 * container that deploys the endpoint, the integration test that connects to it, and the Studio
 * dev-server proxy that forwards to it.
 */
final class ExerisWebSocketConfig {

    /** The single endpoint path. Studio connects to {@code ws://<host>:<port>/lsp}. */
    static final String PATH = "/lsp";

    private ExerisWebSocketConfig() {
    }

    /**
     * Programmatic rather than {@code @ServerEndpoint}-annotated: the endpoint extends LSP4J's
     * {@code WebSocketEndpoint}, which is a {@code jakarta.websocket.Endpoint} subclass, and the
     * annotation model cannot describe those.
     */
    static ServerEndpointConfig endpointConfig() {
        return ServerEndpointConfig.Builder.create(ExerisWebSocketEndpoint.class, PATH).build();
    }
}
