package eu.exeris.platform.lsp;

import com.google.gson.JsonElement;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
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

    private static final System.Logger LOG =
            System.getLogger(ExerisLanguageServer.class.getName());

    /** Registration id for the dynamically-registered Java-source file watcher. */
    private static final String FILE_WATCHER_REGISTRATION_ID = "exeris-java-source-watcher";

    private final TextDocumentService textDocumentService;
    private final WorkspaceService workspaceService;
    private final MutationApplyService mutationService = new MutationApplyService();

    /**
     * What {@code exit} actually does, given the exit code the LSP spec prescribes.
     *
     * <p>Injected rather than hardcoded because the two transports need different answers. On
     * stdio the process <em>is</em> the session, so ending the process is correct. On a WebSocket
     * one process serves many sessions, and {@code System.exit} would let any one client take the
     * server down for every other — so that transport closes its own socket instead.
     */
    private final IntConsumer exitAction;

    private LanguageClient client;

    private volatile WorkspaceIndex index;
    // volatile: shutdown() and exit() can arrive on different LSP4J dispatch threads, so the
    // flag write in shutdown() must be visible to the read in exit() (else a clean shutdown
    // could still exit 1).
    private volatile boolean shutdownRequested;
    // Captured at initialize, read at initialized — both arrive sequentially on the same LSP4J
    // dispatch thread, so no cross-thread visibility concern (unlike shutdownRequested above):
    // whether the client advertised dynamic registration for workspace/didChangeWatchedFiles.
    private boolean clientSupportsFileWatchers;

    /** A server whose {@code exit} ends the JVM — the stdio contract. */
    public ExerisLanguageServer() {
        this(System::exit);
    }

    /**
     * @param exitAction what {@code exit} does with the spec's exit code (0 after a clean
     *                   {@code shutdown}, 1 otherwise)
     */
    public ExerisLanguageServer(IntConsumer exitAction) {
        this.exitAction = Objects.requireNonNull(exitAction, "exitAction");
        // Both services share one invalidation hook: a save or an out-of-band disk change drops
        // the cached scan so the next read re-parses from disk.
        this.textDocumentService = new ExerisTextDocumentService(this::invalidateIndex);
        this.workspaceService = new ExerisWorkspaceService(this::invalidateIndex);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        this.index = new WorkspaceIndex(resolveRoot(params));
        this.clientSupportsFileWatchers = supportsDynamicFileWatchers(params.getCapabilities());

        ServerCapabilities capabilities = new ServerCapabilities();
        // The index reads .java from disk, so only disk-truth signals matter: a save (didSave)
        // and out-of-band changes (workspace/didChangeWatchedFiles). We ask for save
        // notifications but NOT per-keystroke content (change = None) — the in-editor buffer is
        // not on disk and streaming it would only thrash the cache. includeText = false: we
        // re-read the file from disk ourselves rather than trust the streamed copy.
        TextDocumentSyncOptions syncOptions = new TextDocumentSyncOptions();
        syncOptions.setOpenClose(true);
        syncOptions.setChange(TextDocumentSyncKind.None);
        syncOptions.setSave(new SaveOptions(false));
        capabilities.setTextDocumentSync(syncOptions);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public void initialized(InitializedParams params) {
        // Register a recursive watcher for .java sources so out-of-band disk edits (git pull,
        // external tooling, file create/delete) reach didChangeWatchedFiles and invalidate the
        // index. Only clients that advertised dynamic registration get this; others simply won't
        // send watched-file events (didSave still covers in-editor saves).
        if (client == null || !clientSupportsFileWatchers) {
            return;
        }
        FileSystemWatcher watcher = new FileSystemWatcher(Either.forLeft("**/*.java"));
        DidChangeWatchedFilesRegistrationOptions options =
                new DidChangeWatchedFilesRegistrationOptions(List.of(watcher));
        Registration registration = new Registration(
                FILE_WATCHER_REGISTRATION_ID, "workspace/didChangeWatchedFiles", options);
        client.registerCapability(new RegistrationParams(List.of(registration)))
                .exceptionally(failure -> {
                    LOG.log(System.Logger.Level.WARNING,
                            () -> "Failed to register .java file watcher; out-of-band disk edits "
                                    + "won't invalidate the index until the next save", failure);
                    return null;
                });
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

    @Override
    public CompletableFuture<JsonElement> applyMutation(ApplyMutationParams params) {
        // The only writer to on-disk sources. requireIndex() rejects a pre-initialize call the
        // same way the read methods do; the service writes back idempotently and invalidates the
        // index when (and only when) bytes changed.
        return CompletableFuture.completedFuture(
                mutationService.apply(requireIndex(), params, this::invalidateIndex));
    }

    private WorkspaceIndex requireIndex() {
        WorkspaceIndex current = index;
        if (current == null) {
            throw new IllegalStateException(
                    "LSP server not initialized: 'initialize' must precede exeris/* requests");
        }
        return current;
    }

    /** Drops the cached scan if an index exists; the next read re-parses from disk. */
    void invalidateIndex() {
        WorkspaceIndex current = index;
        if (current != null) {
            current.invalidate();
        }
    }

    private static boolean supportsDynamicFileWatchers(ClientCapabilities capabilities) {
        return capabilities != null
                && capabilities.getWorkspace() != null
                && capabilities.getWorkspace().getDidChangeWatchedFiles() != null
                && Boolean.TRUE.equals(capabilities.getWorkspace()
                        .getDidChangeWatchedFiles().getDynamicRegistration());
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
        // Per the LSP spec: exit 0 if shutdown was requested first, otherwise 1. What "exit"
        // means is the transport's to decide — see exitAction.
        exitAction.accept(shutdownRequested ? 0 : 1);
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
