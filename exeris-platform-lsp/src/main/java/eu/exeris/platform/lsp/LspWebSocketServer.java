package eu.exeris.platform.lsp;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * Hosts {@link ExerisWebSocketEndpoint} in an embedded Jetty container.
 *
 * <p>The container is an implementation detail — nothing above this class knows what serves the
 * socket, and the JSON-RPC surface on it is the one {@code LspMain} also serves over stdio. It is
 * Jetty rather than Tyrus for one reason that is not a detail: {@link ServerConnector#setHost}
 * binds the interface it is given. Tyrus's standalone {@code Server} accepts a host and ignores
 * it, always binding {@code 0.0.0.0} — which for a socket carrying {@code exeris/applyMutation}
 * turns a loopback default into a network-reachable write path.
 */
final class LspWebSocketServer {

    private static final System.Logger LOG = System.getLogger(LspWebSocketServer.class.getName());

    private LspWebSocketServer() {
    }

    /**
     * Starts the container and blocks until it stops.
     *
     * @param host interface to bind — honoured, not decorative
     * @param port port to bind
     */
    static void run(String host, int port) throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler("/");
        server.setHandler(context);
        JakartaWebSocketServletContainerInitializer.configure(context,
                (servletContext, container) -> container.addEndpoint(ExerisWebSocketConfig.endpointConfig()));

        // Stops the container through its own lifecycle on Ctrl-C or `docker stop`, rather than
        // having the process shot out from under it with sessions still open.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception failedToStop) {
                LOG.log(System.Logger.Level.WARNING, "WebSocket container did not stop cleanly", failedToStop);
            }
        }, "lsp-websocket-stop"));

        server.start();
        LOG.log(System.Logger.Level.INFO,
                () -> "Exeris LSP listening on ws://" + host + ":" + port + ExerisWebSocketConfig.PATH);
        server.join();
    }
}
