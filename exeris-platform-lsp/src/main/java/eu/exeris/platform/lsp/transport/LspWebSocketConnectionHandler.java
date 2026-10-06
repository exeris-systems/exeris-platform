package eu.exeris.platform.lsp.transport;

import eu.exeris.kernel.spi.websocket.WebSocketCloseCode;
import eu.exeris.kernel.spi.websocket.WebSocketExchange;
import eu.exeris.kernel.spi.websocket.WebSocketHandler;
import eu.exeris.platform.lsp.ExerisLanguageServer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.lsp4j.launch.LSPLauncher;

/**
 * Kernel 0.12 Loom WebSocket connection handler hosting the Exeris Language Server.
 *
 * <p>Honours the ADR-084 requirements:
 * <ul>
 *   <li>One {@link ExerisLanguageServer} allocated per session.</li>
 *   <li>Blocking virtual thread loop on {@link WebSocketExchange#receive()}, handing messages to
 *       LSP4J through a bounded handoff that parks the loop while LSP4J is behind (backpressure,
 *       never an unbounded on-heap queue).</li>
 *   <li>Clean exit on session close; maps exit status to {@link WebSocketCloseCode#NORMAL_CLOSURE}
 *       or {@link WebSocketCloseCode#PROTOCOL_ERROR}.</li>
 * </ul>
 */
public final class LspWebSocketConnectionHandler implements WebSocketHandler {

    private static final System.Logger LOG =
            System.getLogger(LspWebSocketConnectionHandler.class.getName());

    @Override
    public void handle(WebSocketExchange exchange) {
        // Allocate a dedicated ExerisLanguageServer instance per session (ADR-084 Requirement 3)
        ExerisLanguageServer server = new ExerisLanguageServer(exitStatus -> {
            try {
                if (exitStatus == 0) {
                    exchange.close(WebSocketCloseCode.NORMAL_CLOSURE, "clean exit");
                } else {
                    exchange.close(WebSocketCloseCode.PROTOCOL_ERROR, "exit without shutdown");
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.DEBUG, "Failed to close exchange on server exit", e);
            }
        });

        WebSocketJsonRpcFraming.MessageInputStream in = WebSocketJsonRpcFraming.createInputStream();
        WebSocketJsonRpcFraming.FramingOutputStream out = WebSocketJsonRpcFraming.createOutputStream(exchange);

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        Future<?> listening = null;
        try {
            var launcher = LSPLauncher.createServerLauncher(server, in, out, virtualExecutor, null);
            server.connect(launcher.getRemoteProxy());
            Future<?> listener = launcher.startListening();
            listening = listener;
            // Once LSP4J stops reading, nothing will drain the handoff: close it so a receive loop
            // parked on a full handoff is released rather than stalled for the session's lifetime.
            virtualExecutor.execute(() -> {
                try {
                    listener.get();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                } catch (Exception listenerEnded) {
                    // Cancellation and listener failure end the session the same way.
                    LOG.log(System.Logger.Level.DEBUG, "LSP4J listener ended", listenerEnded);
                } finally {
                    in.close();
                }
            });

            // Block on receive() until the peer disconnects, the session closes, or LSP4J stops.
            String message;
            while ((message = exchange.receive()) != null) {
                if (!in.feedMessage(message)) {
                    break;
                }
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.DEBUG, "WebSocket loop terminated", e);
        } finally {
            in.close();
            if (listening != null) {
                listening.cancel(true);
            }
            virtualExecutor.shutdownNow();
        }
    }
}
