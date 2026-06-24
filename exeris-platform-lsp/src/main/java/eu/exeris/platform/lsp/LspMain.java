package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Entry point for the Exeris Platform LSP server.
 *
 * <p>Speaks JSON-RPC over stdio — the transport every LSP client (Studio over a
 * WebSocket bridge, IntelliJ in-JVM, VS Code) ultimately consumes. This is also the
 * default {@code EXERIS_LSP_COMMAND} target launched by exeris-ai-bridge via
 * {@code mvn -pl exeris-platform-lsp exec:java}.
 */
public final class LspMain {

    private LspMain() {
    }

    public static void main(String[] args) throws Exception {
        ExerisLanguageServer server = new ExerisLanguageServer();
        Launcher<LanguageClient> launcher =
                LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
