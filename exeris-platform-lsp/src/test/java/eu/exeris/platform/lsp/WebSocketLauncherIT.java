package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Runs the shipped jar in its second mode: {@code --websocket}, the transport Studio uses.
 *
 * <p>{@link LauncherIT} proves the jar boots and serves over stdio. This proves the same jar
 * serves the same method surface over a socket — and covers the three things that are true only
 * here:
 *
 * <ul>
 *   <li><b>The bind address is honoured.</b> The default is loopback because the socket carries
 *       {@code exeris/applyMutation}, an unauthenticated write path into the workspace. The
 *       previous container (Tyrus) accepted a host and bound {@code 0.0.0.0} regardless; nothing
 *       but starting it and looking would have caught that.</li>
 *   <li><b>{@code exit} closes the session, not the process.</b> One process serves every Studio
 *       tab, so a literal {@code System.exit} would let any client disconnect all the others.</li>
 *   <li><b>Sessions are independent.</b> A second connection after the first has exited gets a
 *       working server with its own index.</li>
 * </ul>
 *
 * <p>The client is {@code java.net.http.WebSocket} from the JDK — no test dependency, and
 * deliberately not the LSP4J client: a test that drove the wire with the same library that writes
 * it could not see a framing bug.
 */
class WebSocketLauncherIT {

    private static final String ORDER = """
            package com.example.shop;

            import eu.exeris.sdk.annotations.ExerisDomain;
            import eu.exeris.sdk.annotations.Field;

            @ExerisDomain(name = "Order")
            public class Order {

                @Field(required = true)
                private String code;
            }
            """;

    private static final String ORDER_WITH_NOTE = """
            package com.example.shop;

            import eu.exeris.sdk.annotations.ExerisDomain;
            import eu.exeris.sdk.annotations.Field;

            @ExerisDomain(name = "Order")
            public class Order {

                @Field(required = true)
                private String code;

                private String note;
            }
            """;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    /** RFC 6455 1002. Spelled out because {@code java.net.http.WebSocket} exposes a constant for
        NORMAL_CLOSURE and for nothing else. */
    private static final int PROTOCOL_ERROR_CLOSE_CODE = 1002;

    private Process server;

    @AfterEach
    void killLeftovers() {
        if (server != null && server.isAlive()) {
            server.destroyForcibly();
        }
    }

    @Test
    @Timeout(120)
    void theSameJarServesTheSameSurfaceOverAWebSocket(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        int port = freePort();
        server = launch(port);
        awaitListening(port);

        try (LspSocket socket = LspSocket.connect(port)) {
            JsonNode init = socket.call(1, "initialize", initializeParams(workspace));
            assertThat(init.path("result").path("capabilities").isObject()).isTrue();

            JsonNode domains = socket.call(2, "exeris/domains", MAPPER.createObjectNode());
            assertThat(domains.path("result")).hasSize(1);
            assertThat(domains.path("result").get(0).path("qualifiedName").asString())
                    .isEqualTo("com.example.shop.Order");

            ObjectNode describeParams = MAPPER.createObjectNode();
            describeParams.put("qualifiedName", "com.example.shop.Order");
            assertThat(socket.call(3, "exeris/domainDescribe", describeParams)
                    .path("result").path("simpleName").asString()).isEqualTo("Order");

            assertThat(socket.call(4, "exeris/actions", MAPPER.createObjectNode())
                    .path("result").isArray()).isTrue();
        }
    }

    @Test
    @Timeout(120)
    void mutationsRoundTripAndConvergeOverTheSocket(@TempDir Path workspace) throws Exception {
        Path order = sourceIn(workspace);
        Files.writeString(order, ORDER);
        int port = freePort();
        server = launch(port);
        awaitListening(port);

        try (LspSocket socket = LspSocket.connect(port)) {
            socket.call(1, "initialize", initializeParams(workspace));

            FieldMetadata note = new SourceModelReader().read(ORDER_WITH_NOTE)
                    .orElseThrow().findField("note").orElseThrow();
            ObjectNode params = MAPPER.createObjectNode();
            params.put("qualifiedName", "com.example.shop.Order");
            params.set("op", MAPPER.valueToTree(new MutationOp.AddField("/entities/Order/fields/note", note)));
            params.put("baselineJson", trustworthyBaselineFor(ORDER));

            assertThat(socket.call(2, "exeris/applyMutation", params)
                    .path("result").path("outcome").asString()).isEqualTo("SUCCESS");
            String afterFirst = Files.readString(order);
            assertThat(afterFirst).contains("note");

            assertThat(socket.call(3, "exeris/applyMutation", params)
                    .path("result").path("outcome").asString()).isEqualTo("SUCCESS");
            assertThat(Files.readString(order))
                    .as("the same op applied twice converges, transport notwithstanding")
                    .isEqualTo(afterFirst);
        }
    }

    @Test
    @Timeout(120)
    void exitEndsTheSessionAndLeavesTheServerServing(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        int port = freePort();
        server = launch(port);
        awaitListening(port);

        try (LspSocket first = LspSocket.connect(port)) {
            first.call(1, "initialize", initializeParams(workspace));
            first.call(2, "shutdown", MAPPER.createObjectNode());
            first.notify("exit");

            assertThat(first.awaitClose())
                    .as("a clean shutdown then exit closes the session normally")
                    .isEqualTo(WebSocket.NORMAL_CLOSURE);
        }

        assertThat(server.isAlive())
                .as("exit must close the session, never the process — one process serves every tab")
                .isTrue();

        // The proof that sessions are independent: a fresh connection gets a working server.
        try (LspSocket second = LspSocket.connect(port)) {
            second.call(1, "initialize", initializeParams(workspace));
            assertThat(second.call(2, "exeris/domains", MAPPER.createObjectNode()).path("result"))
                    .as("the server still answers after another client exited")
                    .hasSize(1);
        }
    }

    @Test
    @Timeout(120)
    void exitWithoutShutdownIsReportedAsAProtocolError(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        int port = freePort();
        server = launch(port);
        awaitListening(port);

        try (LspSocket socket = LspSocket.connect(port)) {
            socket.call(1, "initialize", initializeParams(workspace));
            // No shutdown. The LSP spec makes this the client's error, and the stdio server says
            // so with exit code 1 — the socket has to say it too, or a misbehaving client has no
            // way to learn it got the sequence wrong.
            socket.notify("exit");

            assertThat(socket.awaitClose())
                    .as("exit without a preceding shutdown closes with a protocol error")
                    .isEqualTo(PROTOCOL_ERROR_CLOSE_CODE);
        }

        assertThat(server.isAlive())
                .as("even a protocol error ends the session only")
                .isTrue();
    }

    // --- process + socket plumbing ---------------------------------------------------

    private static Path sourceIn(Path workspace) throws IOException {
        Path pkg = workspace.resolve("com/example/shop");
        Files.createDirectories(pkg);
        return pkg.resolve("Order.java");
    }

    /** An ephemeral port, released immediately. The launcher reports the port it is given, so the
        test has to pick one rather than ask the server which it chose. */
    private static int freePort() throws IOException {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private Process launch(int port) throws IOException {
        String jar = System.getProperty("exeris.lsp.jar");
        assertThat(jar).as("system property exeris.lsp.jar (set by failsafe)").isNotNull();
        assertThat(Path.of(jar)).as("the shaded jar must exist — run `mvn package` first").exists();

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder pb = new ProcessBuilder(List.of(
                java.toString(), "-jar", jar, "--websocket", "--port", String.valueOf(port)));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }

    /** Polls the port rather than the log: the container is ready when it accepts a connection,
        and a log line can print before or after that. */
    private void awaitListening(int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            assertThat(server.isAlive()).as("server process died before it listened").isTrue();
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", port), 250);
                return;
            } catch (IOException notYet) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("server never listened on port " + port);
    }

    @SuppressWarnings("deprecation") // rootUri: deprecated in LSP, still the path clients send
    private ObjectNode initializeParams(Path workspace) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("rootUri", workspace.toUri().toString());
        params.set("capabilities", MAPPER.createObjectNode());
        return params;
    }

    private static String trustworthyBaselineFor(String source) {
        DomainMetadata model = new SourceModelReader().read(source).orElseThrow();
        ObjectNode node = (ObjectNode) MAPPER.valueToTree(model);
        node.put("schemaVersion", SchemaVersion.CURRENT);
        return MAPPER.writeValueAsString(node);
    }

    /** Minimal JSON-RPC-over-WebSocket client on the JDK's own WebSocket. */
    private static final class LspSocket implements AutoCloseable, WebSocket.Listener {

        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final BlockingQueue<Integer> closed = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        static LspSocket connect(int port) throws Exception {
            LspSocket client = new LspSocket();
            client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/lsp"), client)
                    .get(30, TimeUnit.SECONDS);
            return client;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            // A message can arrive in fragments; only a complete one is a JSON-RPC frame.
            partial.append(data);
            if (last) {
                inbox.add(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.add(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            inbox.add("{\"transportError\":\"" + error + "\"}");
        }

        JsonNode call(int id, String method, JsonNode params) throws Exception {
            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.set("params", params);
            send(request);
            String response = inbox.poll(30, TimeUnit.SECONDS);
            assertThat(response).as("no response to %s within 30s", method).isNotNull();
            return MAPPER.readTree(response);
        }

        void notify(String method) throws Exception {
            ObjectNode notification = MAPPER.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            send(notification);
        }

        int awaitClose() throws Exception {
            Integer status = closed.poll(30, TimeUnit.SECONDS);
            assertThat(status).as("server did not close the session within 30s").isNotNull();
            return status;
        }

        private void send(JsonNode message) throws Exception {
            socket.sendText(MAPPER.writeValueAsString(message), true).get(30, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "test over").get(10, TimeUnit.SECONDS);
            } catch (Exception alreadyGone) {
                socket.abort();
            }
        }
    }
}
