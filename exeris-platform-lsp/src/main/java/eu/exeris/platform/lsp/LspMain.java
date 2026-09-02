package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Entry point for the Exeris Platform LSP server.
 *
 * <p>One binary, two transports, one method surface:
 *
 * <pre>
 *   java -jar exeris-platform-lsp-&lt;version&gt;-standalone.jar
 *   java -jar ... --stdio
 *   java -jar ... --websocket [--host 127.0.0.1] [--port 5007]
 * </pre>
 *
 * <p>stdio is the default and the no-argument behaviour, which keeps every existing consumer
 * working — {@code exeris-ai-bridge} launches this jar with no arguments, and IDE plugins speak
 * stdio because that is what their LSP clients spawn. Studio uses the WebSocket, because a browser
 * cannot spawn a process.
 *
 * <p>The two differ only in how bytes arrive. {@code exeris/domains},
 * {@code exeris/domainDescribe}, {@code exeris/actions} and {@code exeris/applyMutation} behave
 * identically on both; forking the surface per transport is exactly what the platform contract
 * forbids.
 */
public final class LspMain {

    private static final System.Logger LOG = System.getLogger(LspMain.class.getName());

    private static final String USAGE = """
            Usage: java -jar exeris-platform-lsp-<version>-standalone.jar [options]

              (no options)            serve LSP over stdio
              --stdio                 serve LSP over stdio
              --websocket             serve LSP over a WebSocket at ws://<host>:<port>/lsp
              --host <address>        interface to bind (WebSocket only, default 127.0.0.1)
              --port <number>         port to bind (WebSocket only, default 5007)
            """;

    private LspMain() {
    }

    public static void main(String[] args) throws Exception {
        LauncherOptions options;
        try {
            options = LauncherOptions.parse(args);
        } catch (IllegalArgumentException badArgs) {
            // stderr, never stdout: on the stdio transport stdout IS the protocol channel, and a
            // stray line there corrupts the very first frame the client reads.
            System.err.println(badArgs.getMessage());
            System.err.println();
            System.err.print(USAGE);
            System.exit(2);
            return;
        }

        switch (options.transport()) {
            case STDIO -> serveStdio();
            case WEBSOCKET -> {
                warnIfExposed(options);
                LspWebSocketServer.run(options.host(), options.port());
            }
        }
    }

    private static void serveStdio() throws Exception {
        ExerisLanguageServer server = new ExerisLanguageServer();
        Launcher<LanguageClient> launcher =
                LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }

    /**
     * Says out loud what a non-loopback bind means, because nothing else will.
     *
     * <p>The socket carries {@code exeris/applyMutation} and LSP has no authentication, so the
     * bind address is the only access control there is. Off loopback, this is a remote write path
     * into the workspace. There are legitimate reasons to do it — a container publishing a port,
     * a remote dev box behind its own network controls — so it is permitted and announced rather
     * than refused.
     */
    private static void warnIfExposed(LauncherOptions options) {
        if (options.bindsBeyondLoopback()) {
            LOG.log(System.Logger.Level.WARNING,
                    () -> "Binding " + options.host() + ": this socket serves exeris/applyMutation, "
                            + "which writes .java files under the workspace root, and it is "
                            + "unauthenticated. Restrict reachability at the network layer.");
        }
    }
}
