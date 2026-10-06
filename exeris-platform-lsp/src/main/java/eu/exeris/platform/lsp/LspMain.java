package eu.exeris.platform.lsp;

import eu.exeris.kernel.spi.websocket.WebSocketConfig;
import eu.exeris.kernel.spi.websocket.WebSocketProvider;
import eu.exeris.kernel.spi.websocket.WebSocketServerEngine;
import eu.exeris.platform.lsp.transport.LspWebSocketConnectionHandler;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Entry point for the Exeris Platform LSP server.
 *
 * <p>Supports stdio (default) and WebSocket ({@code --websocket}, or any WebSocket-only option).
 * Both transports serve the same {@link ExerisLanguageServer} method surface; see
 * {@link LauncherOptions} for the bind and origin rules the WebSocket endpoint enforces.
 */
public final class LspMain {

    private static final System.Logger LOG = System.getLogger(LspMain.class.getName());

    private LspMain() {
    }

    /**
     * The launcher's console contract: a parse error goes to stderr with exit code 2, and the
     * WebSocket mode announces its bound {@code ws://} URL on stdout for whatever started it. On
     * stdio, stdout is the protocol stream and nothing else is written to it.
     */
    @SuppressWarnings("java:S106")
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        LauncherOptions options;
        try {
            options = LauncherOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("exeris-platform-lsp: " + e.getMessage());
            System.exit(2);
            return;
        }
        switch (options.transport()) {
            case STDIO -> runStdio();
            case WEBSOCKET -> runWebSocket(options);
        }
    }

    private static void runStdio() throws InterruptedException, ExecutionException {
        ExerisLanguageServer server = new ExerisLanguageServer();
        Launcher<LanguageClient> launcher =
                LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }

    @SuppressWarnings("java:S106")
    private static void runWebSocket(LauncherOptions options) throws InterruptedException {
        // A non-loopback bind gets this far only with --allow-remote, and the log still says what
        // that exposes.
        if (options.bindsBeyondLoopback()) {
            LOG.log(System.Logger.Level.WARNING,
                    "WebSocket bound to {0}:{1} (--allow-remote); exeris/applyMutation is reachable "
                            + "without authentication",
                    options.host(), String.valueOf(options.port()));
        }

        WebSocketProvider provider = ServiceLoader.load(WebSocketProvider.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No WebSocketProvider registered in META-INF/services/eu.exeris.kernel.spi.websocket.WebSocketProvider"));

        WebSocketConfig config = WebSocketConfig.defaultServer(
                options.host(), options.port(), options.allowedOrigins());
        WebSocketServerEngine engine = provider.createServerEngine(config);
        engine.setHandler(new LspWebSocketConnectionHandler());
        engine.start();

        System.out.printf("Exeris LSP WebSocket listening on ws://%s:%d/lsp%n", options.host(), engine.boundPort());

        Runtime.getRuntime().addShutdownHook(new Thread(engine::close, "lsp-websocket-shutdown"));

        // The engine serves on its own threads; the launcher lives until the JVM shuts down.
        Thread.currentThread().join();
    }
}
