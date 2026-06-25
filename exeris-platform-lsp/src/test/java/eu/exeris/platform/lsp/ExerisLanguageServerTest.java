package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.exeris.platform.lsp.ExerisProtocolExtensions.ActionSummary;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.DomainDescribeParams;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.DomainDescription;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.DomainSummary;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.FieldDescription;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives {@link ExerisLanguageServer} over a real in-memory JSON-RPC round-trip — the same
 * wire path {@code LspMain} uses on stdio — covering both the base lifecycle and the read-only
 * {@code exeris/*} extensions. {@code exit()} is intentionally not exercised: it calls
 * {@code System.exit} and would kill the test JVM; the stdio exit-code behaviour is covered by
 * the manual smoke run.
 */
class ExerisLanguageServerTest {

    /** Combined remote view so the client proxy can call both LSP and exeris/* methods. */
    interface ServerApi extends LanguageServer, ExerisProtocolExtensions {
    }

    private final List<AutoCloseable> closeables = new ArrayList<>();
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        for (AutoCloseable c : closeables) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void initializeAdvertisesSyncCapabilityAndShutdownCompletesNull() throws Exception {
        ServerApi remote = connect(new ExerisLanguageServer());

        InitializeResult init = remote.initialize(new InitializeParams()).get(5, TimeUnit.SECONDS);
        // Save notifications on, but no per-keystroke content (change = None): the index reads
        // disk and only needs to know when a file hit it.
        TextDocumentSyncOptions sync = init.getCapabilities().getTextDocumentSync().getRight();
        assertThat(sync.getOpenClose()).isTrue();
        assertThat(sync.getChange()).isEqualTo(TextDocumentSyncKind.None);
        assertThat(sync.getSave()).isNotNull();

        assertThat(remote.shutdown().get(5, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void exerisMethodsReturnDomainsActionsAndDescriptionOverTheWire(@TempDir Path workspace)
            throws Exception {
        Files.writeString(workspace.resolve("Order.java"), """
                package com.example.shop;

                import eu.exeris.sdk.annotations.Action;
                import eu.exeris.sdk.annotations.ActionParam;
                import eu.exeris.sdk.annotations.ExerisDomain;
                import eu.exeris.sdk.annotations.Field;

                @ExerisDomain(name = "Order", restApi = true, graphqlApi = true)
                public class Order {

                    @Field(required = true)
                    private String code;

                    private double total;

                    @Action(httpMethod = "POST")
                    public void submit(@ActionParam(required = true) String reason) {
                    }
                }
                """);
        // A non-parsing .java file must be skipped, not fail the whole index.
        Files.writeString(workspace.resolve("Broken.java"), "this is not valid java {{{");

        ServerApi remote = connect(new ExerisLanguageServer());
        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        remote.initialize(params).get(5, TimeUnit.SECONDS);

        List<DomainSummary> domains = remote.domains().get(5, TimeUnit.SECONDS);
        assertThat(domains).singleElement().satisfies(d -> {
            assertThat(d.qualifiedName()).isEqualTo("com.example.shop.Order");
            assertThat(d.simpleName()).isEqualTo("Order");
            assertThat(d.packageName()).isEqualTo("com.example.shop");
            assertThat(d.sourcePath()).endsWith("Order.java");
        });

        DomainDescription description = remote
                .domainDescribe(new DomainDescribeParams("com.example.shop.Order"))
                .get(5, TimeUnit.SECONDS);
        assertThat(description.fields()).extracting(FieldDescription::name)
                .contains("code", "total");
        assertThat(description.fields())
                .filteredOn(f -> f.name().equals("code"))
                .singleElement()
                .satisfies(f -> assertThat(f.required()).isTrue());
        assertThat(description.actions()).extracting("name").contains("submit");
        assertThat(description.artefacts()).contains("rest", "graphql");

        List<ActionSummary> actions = remote.actions().get(5, TimeUnit.SECONDS);
        assertThat(actions).singleElement().satisfies(a -> {
            assertThat(a.owningDomain()).isEqualTo("com.example.shop.Order");
            assertThat(a.name()).isEqualTo("submit");
        });
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void exerisMethodOnUnknownDomainReturnsNull(@TempDir Path workspace) throws Exception {
        ServerApi remote = connect(new ExerisLanguageServer());
        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        remote.initialize(params).get(5, TimeUnit.SECONDS);

        DomainDescription missing = remote
                .domainDescribe(new DomainDescribeParams("does.not.Exist"))
                .get(5, TimeUnit.SECONDS);
        assertThat(missing).isNull();
    }

    @Test
    @Timeout(10)
    void exerisMethodBeforeInitializeFailsWithJsonRpcError() throws Exception {
        ServerApi remote = connect(new ExerisLanguageServer());

        // requireIndex() throws IllegalStateException synchronously; LSP4J turns it into a
        // JSON-RPC error, surfaced to the client as a ResponseErrorException.
        assertThatThrownBy(() -> remote.domains().get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseErrorException.class);
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void didSaveInvalidatesIndexSoNextReadSeesTheNewSource(@TempDir Path workspace) throws Exception {
        ExerisLanguageServer server = new ExerisLanguageServer();
        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        server.initialize(params).get(5, TimeUnit.SECONDS);
        assertThat(server.domains().get(5, TimeUnit.SECONDS)).isEmpty();

        Path account = workspace.resolve("Account.java");
        Files.writeString(account, ACCOUNT_SOURCE);
        // The cached (empty) scan is still served until a save invalidates it.
        assertThat(server.domains().get(5, TimeUnit.SECONDS)).isEmpty();

        server.getTextDocumentService().didSave(new DidSaveTextDocumentParams(
                new TextDocumentIdentifier(account.toUri().toString())));

        assertThat(server.domains().get(5, TimeUnit.SECONDS))
                .singleElement()
                .satisfies(d -> assertThat(d.qualifiedName()).isEqualTo("com.example.bank.Account"));
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void didSaveOfNonJavaFileLeavesIndexCached(@TempDir Path workspace) throws Exception {
        ExerisLanguageServer server = new ExerisLanguageServer();
        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        server.initialize(params).get(5, TimeUnit.SECONDS);
        // Populate (and cache) the empty scan.
        assertThat(server.domains().get(5, TimeUnit.SECONDS)).isEmpty();

        // A domain lands on disk, but the save we report is a non-.java file.
        Files.writeString(workspace.resolve("Account.java"), ACCOUNT_SOURCE);
        server.getTextDocumentService().didSave(new DidSaveTextDocumentParams(
                new TextDocumentIdentifier(workspace.resolve("pom.xml").toUri().toString())));

        // The non-Java save must not invalidate: the stale (empty) cache is still served.
        assertThat(server.domains().get(5, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void didChangeWatchedFilesInvalidatesIndexSoNextReadSeesTheNewSource(@TempDir Path workspace)
            throws Exception {
        ExerisLanguageServer server = new ExerisLanguageServer();
        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        server.initialize(params).get(5, TimeUnit.SECONDS);
        assertThat(server.domains().get(5, TimeUnit.SECONDS)).isEmpty();

        Path account = workspace.resolve("Account.java");
        Files.writeString(account, ACCOUNT_SOURCE);

        server.getWorkspaceService().didChangeWatchedFiles(new DidChangeWatchedFilesParams(
                List.of(new FileEvent(account.toUri().toString(), FileChangeType.Created))));

        assertThat(server.domains().get(5, TimeUnit.SECONDS))
                .singleElement()
                .satisfies(d -> assertThat(d.qualifiedName()).isEqualTo("com.example.bank.Account"));
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void initializedRegistersJavaWatcherWhenClientSupportsDynamicRegistration(@TempDir Path workspace)
            throws Exception {
        RecordingClient recordingClient = new RecordingClient();
        ExerisLanguageServer server = new ExerisLanguageServer();
        server.connect(recordingClient);

        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        params.setCapabilities(clientCapabilitiesWithDynamicWatchers(true));
        server.initialize(params).get(5, TimeUnit.SECONDS);

        server.initialized(new InitializedParams());

        assertThat(recordingClient.registrations)
                .singleElement()
                .satisfies(rp -> assertThat(rp.getRegistrations())
                        .singleElement()
                        .satisfies(r -> assertThat(r.getMethod())
                                .isEqualTo("workspace/didChangeWatchedFiles")));
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void initializedRegistersNothingWhenClientLacksDynamicRegistration(@TempDir Path workspace)
            throws Exception {
        RecordingClient recordingClient = new RecordingClient();
        ExerisLanguageServer server = new ExerisLanguageServer();
        server.connect(recordingClient);

        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        params.setCapabilities(clientCapabilitiesWithDynamicWatchers(false));
        server.initialize(params).get(5, TimeUnit.SECONDS);

        server.initialized(new InitializedParams());

        assertThat(recordingClient.registrations).isEmpty();
    }

    private static ClientCapabilities clientCapabilitiesWithDynamicWatchers(boolean dynamic) {
        DidChangeWatchedFilesCapabilities watched = new DidChangeWatchedFilesCapabilities();
        watched.setDynamicRegistration(dynamic);
        WorkspaceClientCapabilities workspace = new WorkspaceClientCapabilities();
        workspace.setDidChangeWatchedFiles(watched);
        ClientCapabilities capabilities = new ClientCapabilities();
        capabilities.setWorkspace(workspace);
        return capabilities;
    }

    private static final String ACCOUNT_SOURCE = """
            package com.example.bank;

            import eu.exeris.sdk.annotations.ExerisDomain;

            @ExerisDomain(name = "Account")
            public class Account {
            }
            """;

    /** Wires an in-memory client proxy to {@code server} and returns the remote view. */
    private ServerApi connect(ExerisLanguageServer server) throws IOException {
        PipedInputStream serverIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream(serverIn);
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream serverOut = new PipedOutputStream(clientIn);

        // Daemon threads so a lingering blocked reader never holds the test JVM open.
        executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "lsp-test");
            thread.setDaemon(true);
            return thread;
        });

        Launcher<LanguageClient> serverLauncher = new Launcher.Builder<LanguageClient>()
                .setLocalService(server)
                .setRemoteInterface(LanguageClient.class)
                .setInput(serverIn)
                .setOutput(serverOut)
                .setExecutorService(executor)
                .create();
        server.connect(serverLauncher.getRemoteProxy());

        Launcher<ServerApi> clientLauncher = new Launcher.Builder<ServerApi>()
                .setLocalService(new NoopClient())
                .setRemoteInterface(ServerApi.class)
                .setInput(clientIn)
                .setOutput(clientOut)
                .setExecutorService(executor)
                .create();

        serverLauncher.startListening();
        clientLauncher.startListening();

        closeables.add(clientOut);
        closeables.add(serverOut);
        closeables.add(clientIn);
        closeables.add(serverIn);
        return clientLauncher.getRemoteProxy();
    }

    /** Minimal client; the server never calls back on the paths under test. */
    private static final class NoopClient implements LanguageClient {
        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }

    /** Client that records dynamic capability registrations the server pushes during initialized. */
    private static final class RecordingClient implements LanguageClient {
        private final List<RegistrationParams> registrations = new ArrayList<>();

        @Override
        public CompletableFuture<Void> registerCapability(RegistrationParams params) {
            registrations.add(params);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }
}
