package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
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
 * Runs the artifact this module ships: the {@code -standalone} shaded jar, launched as
 * {@code java -jar} in a separate process with no source tree, no Maven reactor and no
 * classpath but the jar itself.
 *
 * <p>A shaded launcher is only proven by executing it. The in-process tests in this module
 * cannot see a jar that builds and then fails at startup — they run on the reactor classpath,
 * where every dependency is a separate jar and nothing has been merged.
 *
 * <p>What only this test can see: that the merged jar's manifest actually launches, that
 * shading did not drop a resource the server needs, and — the sharpest edge here — that
 * the SDK's Jackson-3 polymorphic {@code MutationOp} / {@code MutationResult} vocabulary
 * still (de)serializes after {@code jackson-core} and {@code jackson-databind} have been
 * flattened into one archive alongside a multi-release {@code gson}.
 *
 * <p>Bound to failsafe rather than surefire because it needs {@code package} to have
 * produced the jar. The jar is named through the {@code exeris.lsp.jar} system property
 * rather than globbed out of {@code target/} so a leftover from an earlier version cannot
 * be tested by accident.
 */
class LauncherIT {

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

    /** Same domain carrying the field the mutation adds — the reader derives the exact
        {@link FieldMetadata} the writer emits, so the second apply converges instead of
        conflicting. Mirrors {@code ApplyMutationTest}. */
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

    private Process server;

    @AfterEach
    void killLeftovers() {
        if (server != null && server.isAlive()) {
            server.destroyForcibly();
        }
    }

    @Test
    @Timeout(60)
    void theJarAloneBootsAndServesTheReadOnlyTrio(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        server = launch(workspace);

        JsonNode init = call(1, "initialize", initializeParams(workspace));
        assertThat(init.path("result").path("capabilities").isObject()).isTrue();

        JsonNode domains = call(2, "exeris/domains", MAPPER.createObjectNode());
        assertThat(domains.path("result")).hasSize(1);
        assertThat(domains.path("result").get(0).path("qualifiedName").asString())
                .isEqualTo("com.example.shop.Order");

        ObjectNode describeParams = MAPPER.createObjectNode();
        describeParams.put("qualifiedName", "com.example.shop.Order");
        JsonNode described = call(3, "exeris/domainDescribe", describeParams);
        assertThat(described.path("result").path("simpleName").asString()).isEqualTo("Order");

        JsonNode actions = call(4, "exeris/actions", MAPPER.createObjectNode());
        assertThat(actions.path("result").isArray()).isTrue();

        assertThat(shutDownCleanly()).isZero();
    }

    @Test
    @Timeout(60)
    void theJarAppliesTheSameMutationTwiceConvergently(@TempDir Path workspace) throws Exception {
        Path order = sourceIn(workspace);
        Files.writeString(order, ORDER);
        server = launch(workspace);
        call(1, "initialize", initializeParams(workspace));

        FieldMetadata note = new SourceModelReader().read(ORDER_WITH_NOTE)
                .orElseThrow().findField("note").orElseThrow();
        MutationOp op = new MutationOp.AddField("/entities/Order/fields/note", note);

        ObjectNode params = MAPPER.createObjectNode();
        params.put("qualifiedName", "com.example.shop.Order");
        params.set("op", MAPPER.valueToTree(op));
        params.put("baselineJson", trustworthyBaselineFor(ORDER));

        JsonNode first = call(2, "exeris/applyMutation", params);
        assertThat(first.path("result").path("outcome").asString()).isEqualTo("SUCCESS");
        String afterFirst = Files.readString(order);
        assertThat(afterFirst).contains("note");

        JsonNode second = call(3, "exeris/applyMutation", params);
        assertThat(second.path("result").path("outcome").asString()).isEqualTo("SUCCESS");
        assertThat(Files.readString(order)).isEqualTo(afterFirst);

        assertThat(shutDownCleanly()).isZero();
    }

    @Test
    @Timeout(60)
    void theJarBootsWebSocketServerAndServesClients(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        int port = freePort();
        server = launch(workspace, "--websocket", "--port", String.valueOf(port));
        awaitListening(server);

        try (JarWsClient ws = JarWsClient.connect(port, null)) {
            JsonNode initResp = ws.call(1, "initialize", initializeParams(workspace));
            assertThat(initResp.path("result").path("capabilities").isObject()).isTrue();

            JsonNode domainsResp = ws.call(2, "exeris/domains", MAPPER.createObjectNode());
            assertThat(domainsResp.path("result")).hasSize(1);
            assertThat(domainsResp.path("result").get(0).path("qualifiedName").asString())
                    .isEqualTo("com.example.shop.Order");

            ws.call(3, "shutdown", MAPPER.createObjectNode());
            ws.notify("exit");
            assertThat(ws.awaitClose()).isEqualTo(WebSocket.NORMAL_CLOSURE);
        }
        assertThat(server.isAlive()).as("one session's exit does not stop the server").isTrue();
    }

    @Test
    @Timeout(60)
    void theDefaultOriginAllowlistAdmitsTheStudioDevServerOnly(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        int port = freePort();
        server = launch(workspace, "--websocket", "--port", String.valueOf(port));
        awaitListening(server);

        try (JarWsClient studio = JarWsClient.connect(port, "http://localhost:4200")) {
            assertThat(studio.call(1, "initialize", initializeParams(workspace)).has("result")).isTrue();
        }
        for (String foreign : List.of("http://evil.example", "http://localhost", "http://localhost:3000")) {
            assertThat(JarWsClient.tryConnect(port, foreign))
                    .as("handshake from %s is refused", foreign)
                    .isFalse();
        }
    }

    @Test
    @Timeout(60)
    void anExplicitOriginReplacesTheDefault(@TempDir Path workspace) throws Exception {
        Files.writeString(sourceIn(workspace), ORDER);
        int port = freePort();
        server = launch(workspace, "--port", String.valueOf(port),
                "--allowed-origin", "https://studio.example.test");
        awaitListening(server);

        try (JarWsClient listed = JarWsClient.connect(port, "https://studio.example.test")) {
            assertThat(listed.call(1, "initialize", initializeParams(workspace)).has("result")).isTrue();
        }
        assertThat(JarWsClient.tryConnect(port, "http://localhost:4200"))
                .as("the default is replaced, not extended")
                .isFalse();
    }

    @Test
    @Timeout(60)
    void aNonLoopbackBindWithoutAllowRemoteIsRefusedAtStartup(@TempDir Path workspace) throws Exception {
        server = launch(workspace, ProcessBuilder.Redirect.PIPE, "--websocket", "--host", "0.0.0.0",
                "--port", String.valueOf(freePort()));

        assertThat(server.waitFor(30, TimeUnit.SECONDS)).as("the launcher exits instead of binding").isTrue();
        assertThat(server.exitValue()).isEqualTo(2);
        String stderr = new String(server.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(stderr).contains("--allow-remote");
    }

    /** Blocks until the launched jar announces its WebSocket endpoint on stdout. */
    private static void awaitListening(Process process) throws IOException {
        BufferedReader out = process.inputReader(StandardCharsets.UTF_8);
        for (String line = out.readLine(); line != null; line = out.readLine()) {
            if (line.startsWith("Exeris LSP WebSocket listening on ")) {
                return;
            }
        }
        throw new AssertionError("the launcher exited before it was listening");
    }

    private static int freePort() throws IOException {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** A JDK WebSocket client against the launched jar; {@code origin} null sends no Origin. */
    private static final class JarWsClient implements AutoCloseable, WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<Integer> closed = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private WebSocket socket;

        /** Connects to a server that has announced it is listening ({@link #awaitListening}). */
        static JarWsClient connect(int port, String origin) throws Exception {
            return open(port, origin);
        }

        /** One attempt once the server is known to be up: whether the handshake was accepted. */
        static boolean tryConnect(int port, String origin) throws Exception {
            try (JarWsClient _ = open(port, origin)) {
                return true;
            } catch (java.util.concurrent.ExecutionException _) {
                return false;
            }
        }

        private static JarWsClient open(int port, String origin) throws Exception {
            JarWsClient client = new JarWsClient();
            WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
            if (origin != null) {
                builder.header("Origin", origin);
            }
            client.socket = builder.buildAsync(URI.create("ws://127.0.0.1:" + port + "/lsp"), client)
                    .get(5, TimeUnit.SECONDS);
            return client;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.add(statusCode);
            return null;
        }

        JsonNode call(int id, String method, JsonNode params) throws Exception {
            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.set("params", params);
            socket.sendText(MAPPER.writeValueAsString(request), true).get(5, TimeUnit.SECONDS);
            String response = messages.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("response to %s", method).isNotNull();
            return MAPPER.readTree(response);
        }

        void notify(String method) throws Exception {
            ObjectNode notification = MAPPER.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            socket.sendText(MAPPER.writeValueAsString(notification), true).get(5, TimeUnit.SECONDS);
        }

        Integer awaitClose() throws InterruptedException {
            return closed.poll(10, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
            } catch (Exception _) {
                // Already closed by the server: nothing left to close politely.
                socket.abort();
            }
        }
    }

    // --- process + LSP base protocol -------------------------------------------------

    private static Path sourceIn(Path workspace) throws IOException {
        Path pkg = workspace.resolve("com/example/shop");
        Files.createDirectories(pkg);
        return pkg.resolve("Order.java");
    }

    /** Launches the shipped jar with the running JDK — no reliance on whatever {@code java} is
        first on PATH, which on a developer machine is frequently not the one Maven is using. */
    private Process launch(Path workspace, String... args) throws IOException {
        return launch(workspace, ProcessBuilder.Redirect.INHERIT, args);
    }

    private Process launch(Path workspace, ProcessBuilder.Redirect stderr, String... args)
            throws IOException {
        String jar = System.getProperty("exeris.lsp.jar");
        assertThat(jar).as("system property exeris.lsp.jar (set by failsafe)").isNotNull();
        assertThat(Path.of(jar)).as("the shaded jar must exist — run `mvn package` first").exists();

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = new java.util.ArrayList<>(List.of(java.toString(), "-jar", jar));
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspace.toFile());
        // Inherited by default so a stack trace from the server lands in the failsafe report
        // rather than filling a pipe nobody drains — a full stderr buffer would deadlock the
        // server. PIPE only for a launcher expected to exit after one line.
        pb.redirectError(stderr);
        return pb.start();
    }

    @SuppressWarnings("deprecation") // rootUri: deprecated in LSP, still the path clients send
    private ObjectNode initializeParams(Path workspace) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("rootUri", workspace.toUri().toString());
        params.set("capabilities", MAPPER.createObjectNode());
        return params;
    }

    private JsonNode call(int id, String method, JsonNode params) throws IOException {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        write(request);
        return readMessage();
    }

    private void write(JsonNode message) throws IOException {
        byte[] body = MAPPER.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        OutputStream out = server.getOutputStream();
        out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    /** Reads one LSP base-protocol frame: {@code Content-Length} header, blank line, body. */
    private JsonNode readMessage() throws IOException {
        InputStream in = server.getInputStream();
        int length = -1;
        String header;
        while (!(header = readHeaderLine(in)).isEmpty()) {
            if (header.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                length = Integer.parseInt(header.substring(15).trim());
            }
        }
        assertThat(length).as("Content-Length header before the body").isNotNegative();
        byte[] body = in.readNBytes(length);
        assertThat(body.length).as("a truncated frame means the server died mid-answer").isEqualTo(length);
        return MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
    }

    private static String readHeaderLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                line.append((char) c);
            }
        }
        return line.toString();
    }

    /** {@code shutdown} then {@code exit}, per the LSP spec — a server that ignores either is
        one an IDE has to kill, so the exit code is part of the contract. */
    private int shutDownCleanly() throws Exception {
        call(99, "shutdown", MAPPER.createObjectNode());
        ObjectNode exit = MAPPER.createObjectNode();
        exit.put("jsonrpc", "2.0");
        exit.put("method", "exit");
        write(exit);
        assertThat(server.waitFor(30, TimeUnit.SECONDS)).as("server exits on `exit`").isTrue();
        return server.exitValue();
    }

    private static String trustworthyBaselineFor(String source) {
        DomainMetadata model = new SourceModelReader().read(source).orElseThrow();
        ObjectNode node = (ObjectNode) MAPPER.valueToTree(model);
        node.put("schemaVersion", SchemaVersion.CURRENT);
        return MAPPER.writeValueAsString(node);
    }
}
