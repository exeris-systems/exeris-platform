package eu.exeris.platform.lsp;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Base LSP lifecycle for the Exeris Platform server.
 *
 * <p>It advertises minimal capabilities and answers {@code initialize} / {@code shutdown} /
 * {@code exit}, and implements the read-only {@code exeris/*} extensions
 * ({@code exeris/domains}, {@code exeris/domainDescribe}, {@code exeris/actions}) by querying
 * the {@link WorkspaceIndex} built over the workspace root at {@code initialize} time. Those
 * methods sit under the {@code exeris/} namespace per the platform LSP method-surface contract.
 */
public final class ExerisLanguageServer
        implements LanguageServer, LanguageClientAware, ExerisProtocolExtensions {

    private final TextDocumentService textDocumentService = new ExerisTextDocumentService();
    private final WorkspaceService workspaceService = new ExerisWorkspaceService();

    @SuppressWarnings("unused") // wired now; used once notifications/diagnostics land.
    private LanguageClient client;

    private volatile WorkspaceIndex index;
    private boolean shutdownRequested;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        this.index = new WorkspaceIndex(resolveRoot(params));
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<List<DomainSummary>> domains() {
        return CompletableFuture.completedFuture(
                requireIndex().domains().stream()
                        .map(ProtocolProjections::toSummary)
                        .toList());
    }

    @Override
    public CompletableFuture<DomainDescription> domainDescribe(DomainDescribeParams params) {
        // Unknown qualifiedName yields a null result (JSON null) — the caller treats it as "not found".
        return CompletableFuture.completedFuture(
                requireIndex().findByQualifiedName(params.qualifiedName())
                        .map(ProtocolProjections::toDescription)
                        .orElse(null));
    }

    @Override
    public CompletableFuture<List<ActionSummary>> actions() {
        return CompletableFuture.completedFuture(
                requireIndex().domains().stream()
                        .flatMap(d -> ProtocolProjections.toActionSummaries(d).stream())
                        .toList());
    }

    private WorkspaceIndex requireIndex() {
        WorkspaceIndex current = index;
        if (current == null) {
            throw new IllegalStateException(
                    "LSP server not initialized: 'initialize' must precede exeris/* requests");
        }
        return current;
    }

    /**
     * Resolves the workspace root from rootUri, falling back to the first workspace folder.
     * {@code rootUri} is deprecated in the LSP spec but still sent by most clients, so we
     * honour it for compatibility before falling back to {@code workspaceFolders}.
     */
    @SuppressWarnings("deprecation")
    private static Path resolveRoot(InitializeParams params) {
        String uri = params.getRootUri();
        if (uri == null && params.getWorkspaceFolders() != null
                && !params.getWorkspaceFolders().isEmpty()) {
            uri = params.getWorkspaceFolders().get(0).getUri();
        }
        return uri == null ? null : Path.of(URI.create(uri));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdownRequested = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // Per the LSP spec: exit 0 if shutdown was requested first, otherwise 1.
        System.exit(shutdownRequested ? 0 : 1);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }
}
