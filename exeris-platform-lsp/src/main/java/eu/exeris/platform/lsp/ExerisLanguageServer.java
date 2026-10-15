package eu.exeris.platform.lsp;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeError;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
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
 *
 * <p>The workspace root is the client's to name unless the server was started with a pinned root
 * ({@code --root}). A pinned root is the only directory the session indexes and writes under: an
 * {@code initialize} that names no root gets the pinned one, one that names the same directory is
 * served, and one that names any other is refused with {@code InvalidParams} and
 * {@code retry: false}, so a client cannot steer the write path of {@code exeris/applyMutation}
 * to a tree the operator did not choose. Both are compared as real paths.
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

    private final IntConsumer exitAction;
    private final Path pinnedRoot;

    /** A server whose {@code exit} notification ends the JVM, as a launched server must. */
    public ExerisLanguageServer() {
        this(System::exit);
    }

    /**
     * A server that hands its exit code to {@code exitAction} instead of ending the JVM.
     *
     * @param exitAction receives the code {@code exit} would end the process with
     */
    public ExerisLanguageServer(IntConsumer exitAction) {
        this(exitAction, null);
    }

    /**
     * A server whose workspace root is pinned rather than named by the client.
     *
     * @param exitAction receives the code {@code exit} would end the process with
     * @param pinnedRoot the only root this session serves, as a real path, or {@code null} to take
     *                   the root the client names
     */
    public ExerisLanguageServer(IntConsumer exitAction, Path pinnedRoot) {
        this.exitAction = Objects.requireNonNull(exitAction, "exitAction");
        this.pinnedRoot = pinnedRoot;
        // Both services share one invalidation hook: a save or an out-of-band disk change drops
        // the cached scan so the next read re-parses from disk.
        this.textDocumentService = new ExerisTextDocumentService(this::invalidateIndex);
        this.workspaceService = new ExerisWorkspaceService(this::invalidateIndex);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        Path root;
        try {
            root = sessionRoot(params);
        } catch (ResponseErrorException refused) {
            return CompletableFuture.failedFuture(refused);
        }
        this.index = new WorkspaceIndex(root);
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

    @Override
    public CompletableFuture<MutationPreview> previewMutation(ApplyMutationParams params) {
        // Read-only: computes what applyMutation would write and returns it; touches neither the
        // source nor the index.
        return CompletableFuture.completedFuture(mutationService.preview(requireIndex(), params));
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
     * The root this session serves: the client's, or the pinned one when the client's is absent or
     * names the same directory.
     *
     * @throws ResponseErrorException when a root is pinned and the client names another
     */
    private Path sessionRoot(InitializeParams params) {
        String requested = requestedRootUri(params);
        if (pinnedRoot == null) {
            return requested == null ? null : Path.of(URI.create(requested));
        }
        if (requested == null || pinnedRoot.equals(realPathOf(requested))) {
            return pinnedRoot;
        }
        ResponseError error = new ResponseError(ResponseErrorCode.InvalidParams,
                "workspace root is pinned to " + pinnedRoot + " (--root); initialize named "
                        + requested,
                new InitializeError(false));
        throw new ResponseErrorException(error);
    }

    /** The real path a client's root URI names, or {@code null} when it names none on this disk. */
    private static Path realPathOf(String uri) {
        try {
            return Path.of(URI.create(uri)).toRealPath();
        } catch (IOException | RuntimeException _) {
            // Not a file URI, malformed, or no such directory: none of these is the pinned root.
            return null;
        }
    }

    /**
     * The root URI the client names: rootUri, falling back to the first workspace folder.
     * {@code rootUri} is deprecated in the LSP spec but still sent by most clients, so we
     * honour it for compatibility before falling back to {@code workspaceFolders}.
     */
    @SuppressWarnings("deprecation")
    private static String requestedRootUri(InitializeParams params) {
        String uri = params.getRootUri();
        if (uri == null && params.getWorkspaceFolders() != null
                && !params.getWorkspaceFolders().isEmpty()) {
            uri = params.getWorkspaceFolders().get(0).getUri();
        }
        return uri;
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdownRequested = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // Per the LSP spec: exit 0 if shutdown was requested first, otherwise 1.
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
