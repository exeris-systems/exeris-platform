package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * <p>This exists because a launcher whose output is never executed is exactly the failure
 * mode that shipped next door: {@code exeris-kernel-diagnostics-cli} 0.11.0 published a
 * shaded jar that initialised and then died on a {@code NoClassDefFoundError}, because
 * nothing in that build ever started the thing it was publishing. The in-process tests in
 * this module cannot catch that class of bug — they run on the reactor classpath, where
 * every dependency is a separate jar and nothing has been merged.
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

    // --- process + LSP base protocol -------------------------------------------------

    private static Path sourceIn(Path workspace) throws IOException {
        Path pkg = workspace.resolve("com/example/shop");
        Files.createDirectories(pkg);
        return pkg.resolve("Order.java");
    }

    /** Launches the shipped jar with the running JDK — no reliance on whatever {@code java} is
        first on PATH, which on a developer machine is frequently not the one Maven is using. */
    private Process launch(Path workspace) throws IOException {
        String jar = System.getProperty("exeris.lsp.jar");
        assertThat(jar).as("system property exeris.lsp.jar (set by failsafe)").isNotNull();
        assertThat(Path.of(jar)).as("the shaded jar must exist — run `mvn package` first").exists();

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder pb = new ProcessBuilder(List.of(java.toString(), "-jar", jar));
        pb.directory(workspace.toFile());
        // Inherited so a stack trace from the server lands in the failsafe report rather than
        // filling a pipe nobody drains — a full stderr buffer would deadlock the server.
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
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
