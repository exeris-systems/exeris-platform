package eu.exeris.platform.lsp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.exeris.kernel.spi.websocket.WebSocketConfig;
import eu.exeris.kernel.spi.websocket.WebSocketProvider;
import eu.exeris.kernel.spi.websocket.WebSocketServerEngine;
import eu.exeris.platform.lsp.ExerisLanguageServer;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Transport parity: the same raw JSON-RPC requests, sent over the stdio base protocol and over
 * WebSocket, must produce identical JSON results. One server class, one method surface; only the
 * framing differs. WebSocket clients here send no {@code Origin}, which the kernel does not subject
 * to the allowlist (browser-origin enforcement is covered by {@code LauncherIT}).
 */
public class TransportParityIT {

    private static final String ORDER_SOURCE = """
            package com.example.shop;

            import eu.exeris.sdk.annotations.Action;
            import eu.exeris.sdk.annotations.ActionParam;
            import eu.exeris.sdk.annotations.ExerisDomain;
            import eu.exeris.sdk.annotations.Field;
            import eu.exeris.sdk.annotation.Relationship;
            import eu.exeris.sdk.annotation.Relationship.RelationshipType;

            @ExerisDomain(name = "Order", restApi = true, graphqlApi = true)
            public class Order {

                @Field(required = true)
                private String code;

                private double total;

                @Relationship(relationshipType = RelationshipType.ONE_TO_MANY)
                private java.util.List<OrderItem> items;

                @Action(httpMethod = "POST")
                public void submit(@ActionParam(required = true) String reason) {
                }
            }
            """;

    private WebSocketServerEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception _) {
                // Teardown only: a failed close cannot change the verdict of the test that ran.
            }
        }
    }

    @Test
    @Timeout(30)
    void parityBetweenStdioAndWebSocketTransports(@TempDir Path workspace) throws Exception {
        Path pkg = workspace.resolve("com/example/shop");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Order.java"), ORDER_SOURCE);

        List<JsonObject> script = readOnlyScript(workspace);

        // 1. stdio: LSP4J's base-protocol framing over a byte stream, the exact launcher LspMain uses.
        List<JsonElement> overStdio;
        try (StdioClient stdio = StdioClient.start()) {
            overStdio = stdio.run(script);
        }

        // 2. WebSocket: one message per JSON-RPC message, through the kernel provider.
        startEngine();
        List<JsonElement> overWebSocket;
        try (WsClient client = WsClient.connect(engine.boundPort())) {
            overWebSocket = new ArrayList<>();
            for (JsonObject request : script) {
                overWebSocket.add(client.send(request).get("result"));
            }

            client.call(99, "shutdown", new JsonObject());
            client.notify("exit");
            assertThat(client.awaitClose()).isEqualTo(WebSocket.NORMAL_CLOSURE);
        }

        // One surface: every result is byte-for-byte the same JSON on both transports.
        assertThat(overWebSocket).hasSize(script.size());
        for (int i = 0; i < script.size(); i++) {
            assertThat(overWebSocket.get(i))
                    .as("%s", script.get(i).get("method").getAsString())
                    .isEqualTo(overStdio.get(i));
        }

        JsonObject described = overStdio.get(2).getAsJsonObject();
        assertThat(described.getAsJsonArray("relationships"))
                .hasToString("[{\"name\":\"items\",\"targetEntity\":\"OrderItem\",\"type\":\"ONE_TO_MANY\"}]");
    }

    @Test
    @Timeout(30)
    void aLargeResponseCrossesTheWebSocketIntact(@TempDir Path workspace) throws Exception {
        // Large enough that LSP4J's write is many times the framing buffer's initial size.
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            fields.append("    private String field").append(i).append("Zażółć;\n");
        }
        Path pkg = workspace.resolve("com/example/big");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Big.java"), """
                package com.example.big;

                import eu.exeris.sdk.annotations.ExerisDomain;

                @ExerisDomain(name = "Big")
                public class Big {
                %s}
                """.formatted(fields));

        startEngine();
        try (WsClient client = WsClient.connect(engine.boundPort())) {
            client.call(1, "initialize", initializeParams(workspace));
            JsonObject describeReq = new JsonObject();
            describeReq.addProperty("qualifiedName", "com.example.big.Big");
            JsonObject described = client.call(2, "exeris/domainDescribe", describeReq).getAsJsonObject("result");

            assertThat(described.getAsJsonArray("fields")).hasSize(2_000);
            assertThat(described.getAsJsonArray("fields").get(1_999).getAsJsonObject().get("name").getAsString())
                    .isEqualTo("field1999Zażółć");
        }
    }

    private void startEngine() {
        WebSocketProvider provider = ServiceLoader.load(WebSocketProvider.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WebSocketProvider not found"));
        WebSocketConfig config = WebSocketConfig.defaultServer("127.0.0.1", 0, List.of());
        engine = provider.createServerEngine(config);
        engine.setHandler(new LspWebSocketConnectionHandler());
        engine.start();
    }

    private static JsonObject initializeParams(Path workspace) {
        JsonObject init = new JsonObject();
        init.addProperty("rootUri", workspace.toUri().toString());
        init.add("capabilities", new JsonObject());
        return init;
    }

    /**
     * initialize, the read trio and a preview, as raw JSON-RPC requests both transports receive
     * verbatim. The preview writes nothing, so it leaves the workspace as the other transport sees it.
     */
    private static List<JsonObject> readOnlyScript(Path workspace) {
        JsonObject describe = new JsonObject();
        describe.addProperty("qualifiedName", "com.example.shop.Order");
        JsonObject op = new JsonObject();
        op.addProperty("op", "addAction");
        op.addProperty("path", "/entities/Order/actions/cancel");
        JsonObject action = new JsonObject();
        action.addProperty("name", "cancel");
        op.add("action", action);
        JsonObject preview = new JsonObject();
        preview.addProperty("qualifiedName", "com.example.shop.Order");
        preview.add("op", op);
        return List.of(
                request(1, "initialize", initializeParams(workspace)),
                request(2, "exeris/domains", new JsonObject()),
                request(3, "exeris/domainDescribe", describe),
                request(4, "exeris/actions", new JsonObject()),
                request(5, "exeris/previewMutation", preview));
    }

    private static JsonObject request(int id, String method, JsonElement params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", method);
        request.add("params", params);
        return request;
    }

    /** Drives an in-process server over piped byte streams with LSP base-protocol framing. */
    private static final class StdioClient implements AutoCloseable {
        private final PipedOutputStream toServer = new PipedOutputStream();
        private final PipedInputStream fromServer = new PipedInputStream(1 << 20);
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private Future<Void> listening;

        static StdioClient start() throws IOException {
            StdioClient client = new StdioClient();
            PipedInputStream serverIn = new PipedInputStream(client.toServer, 1 << 20);
            PipedOutputStream serverOut = new PipedOutputStream(client.fromServer);
            ExerisLanguageServer server = new ExerisLanguageServer(status -> { });
            var launcher = LSPLauncher.createServerLauncher(server, serverIn, serverOut, client.executor, null);
            server.connect(launcher.getRemoteProxy());
            client.listening = launcher.startListening();
            return client;
        }

        List<JsonElement> run(List<JsonObject> script) throws IOException {
            List<JsonElement> results = new ArrayList<>();
            for (JsonObject request : script) {
                byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
                toServer.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                toServer.write(body);
                toServer.flush();
                results.add(readFrame().get("result"));
            }
            return results;
        }

        private JsonObject readFrame() throws IOException {
            int length = -1;
            String line;
            while (!(line = readLine()).isEmpty()) {
                if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                    length = Integer.parseInt(line.substring(15).trim());
                }
            }
            byte[] body = fromServer.readNBytes(length);
            return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        private String readLine() throws IOException {
            StringBuilder line = new StringBuilder();
            int c;
            while ((c = fromServer.read()) != -1 && c != '\n') {
                if (c != '\r') {
                    line.append((char) c);
                }
            }
            return line.toString();
        }

        @Override
        public void close() throws IOException {
            listening.cancel(true);
            toServer.close();
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    void exitWithoutShutdownClosesWithProtocolError(@TempDir Path workspace) throws Exception {
        startEngine();
        int port = engine.boundPort();

        try (WsClient client = WsClient.connect(port)) {
            JsonObject initReq = new JsonObject();
            initReq.addProperty("rootUri", workspace.toUri().toString());
            initReq.add("capabilities", new JsonObject());
            client.call(1, "initialize", initReq);

            // Exit without calling shutdown -> maps to 1002 (PROTOCOL_ERROR)
            client.notify("exit");

            Integer closeCode = client.awaitClose();
            assertThat(closeCode).isEqualTo(1002);
        }
    }

    private static final class WsClient implements AutoCloseable, WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<Integer> closed = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private WebSocket socket;

        static WsClient connect(int port) throws Exception {
            WsClient client = new WsClient();
            client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/lsp"), client)
                    .get(10, TimeUnit.SECONDS);
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

        JsonObject call(int id, String method, JsonElement params) throws Exception {
            return send(request(id, method, params));
        }

        JsonObject send(JsonObject request) throws Exception {
            socket.sendText(request.toString(), true).get(5, TimeUnit.SECONDS);
            String responseStr = messages.poll(10, TimeUnit.SECONDS);
            assertThat(responseStr).as("no response for %s", request.get("method")).isNotNull();
            return JsonParser.parseString(responseStr).getAsJsonObject();
        }

        void notify(String method) throws Exception {
            JsonObject req = new JsonObject();
            req.addProperty("jsonrpc", "2.0");
            req.addProperty("method", method);
            socket.sendText(req.toString(), true).get(5, TimeUnit.SECONDS);
        }

        Integer awaitClose() throws Exception {
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
}
