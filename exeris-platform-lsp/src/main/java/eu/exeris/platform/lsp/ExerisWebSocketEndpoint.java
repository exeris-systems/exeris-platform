package eu.exeris.platform.lsp;

import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.websocket.jakarta.WebSocketEndpoint;

/**
 * The Studio frontend's transport: the same JSON-RPC surface stdio serves, carried over a
 * WebSocket.
 *
 * <p>Transport only. Not one method, parameter or verdict shape differs between the two — the
 * platform contract is that {@code exeris/*} is defined once and the wire is an implementation
 * detail. Anything that would make a method behave differently here than over stdio belongs in
 * {@link ExerisLanguageServer}, where both transports see it.
 *
 * <p><b>One server per session.</b> Each connection gets its own {@link ExerisLanguageServer},
 * and that is load-bearing rather than incidental. A server instance holds one workspace index
 * built at {@code initialize} and one client proxy, and {@code MutationApplyService} is documented
 * as safe only because LSP4J dispatches one session's messages sequentially on a single reader
 * thread. Sharing one server across sessions would break both: two clients would race the same
 * index and contend on the write path. Per-session instances keep every assumption the stdio path
 * already relies on. What they do NOT isolate is the file system — two sessions editing one
 * workspace is exactly the case ADR-042's baseline and conflict semantics exist for.
 */
public final class ExerisWebSocketEndpoint extends WebSocketEndpoint<LanguageClient> {

    /**
     * Tyrus defaults to an 8 KB text buffer and rejects anything larger as a whole message.
     * That is far below what this protocol actually sends: an {@code exeris/applyMutation}
     * request carries a serialized {@code DomainMetadata} baseline, and {@code domainDescribe}
     * answers with a full projection. 8 MB is chosen to be past any plausible single domain
     * rather than to be tight — the buffer is a ceiling, not an allocation.
     */
    private static final int MAX_TEXT_MESSAGE_BYTES = 8 * 1024 * 1024;

    private Session session;

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        // Before super.onOpen, which installs the message handler that reads with these limits.
        this.session = session;
        session.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BYTES);
        // No idle timeout. A design-time session is idle whenever the developer is thinking, and
        // a server that hangs up on a quiet editor looks like a crash to the user.
        session.setMaxIdleTimeout(0);
        super.onOpen(session, config);
    }

    @Override
    protected void configure(Launcher.Builder<LanguageClient> builder) {
        builder.setLocalService(new ExerisLanguageServer(this::closeSession));
        builder.setRemoteInterface(LanguageClient.class);
    }

    @Override
    protected void connect(Collection<Object> localServices, LanguageClient remoteProxy) {
        localServices.stream()
                .filter(LanguageClientAware.class::isInstance)
                .map(LanguageClientAware.class::cast)
                .forEach(service -> service.connect(remoteProxy));
    }

    /**
     * What {@code exit} means here: end this session, never the process.
     *
     * <p>The stdio server calls {@code System.exit} because there the process is the session. Over
     * a WebSocket the process serves every open Studio tab, so honouring {@code exit} literally
     * would let one client disconnect all the others.
     *
     * @param status the LSP exit code — 0 after a clean {@code shutdown}, 1 without one
     */
    private void closeSession(int status) {
        CloseReason.CloseCode code = status == 0
                ? CloseReason.CloseCodes.NORMAL_CLOSURE
                // `exit` without a preceding `shutdown` is a client protocol error. Saying so on
                // the wire is the only way the client learns it got the sequence wrong; the stdio
                // path says the same thing with exit code 1.
                : CloseReason.CloseCodes.PROTOCOL_ERROR;
        try {
            session.close(new CloseReason(code, "LSP exit"));
        } catch (IOException alreadyGone) {
            // The client hung up first — which is the ordinary way a browser tab closes. Nothing
            // to clean up: the container releases the session either way.
        }
    }
}
