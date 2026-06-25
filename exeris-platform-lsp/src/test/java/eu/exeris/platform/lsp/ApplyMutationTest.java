package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.ApplyMutationParams;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.SchemaVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.InitializeParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Drives {@code exeris/applyMutation} through {@link ExerisLanguageServer}, covering the wire
 * bridge (SDK Jackson op/verdict ↔ LSP4J Gson {@link JsonElement}), source resolution, and the
 * idempotent write-back contract (the same op applied twice converges to identical on-disk state).
 */
class ApplyMutationTest {

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

    /** Same domain, but already carrying a plain {@code note} field — used to derive the exact
        {@link FieldMetadata} the reader produces for what the writer emits (so the second apply
        is a clean convergent SUCCESS, not a spurious conflict). */
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

    /** Same domain, but {@code code} has been retyped on disk — a user edit that drifts from the
        baseline, used to provoke a genuine CONFLICT. */
    private static final String ORDER_CODE_INT = """
            package com.example.shop;

            import eu.exeris.sdk.annotations.ExerisDomain;
            import eu.exeris.sdk.annotations.Field;

            @ExerisDomain(name = "Order")
            public class Order {

                @Field(required = true)
                private Integer code;
            }
            """;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation") // rootUri is deprecated in LSP but the path we must support
    void addFieldWithTrustworthyBaselineWritesBackIdempotently(@TempDir Path workspace) throws Exception {
        Path order = workspace.resolve("Order.java");
        Files.writeString(order, ORDER);

        ExerisLanguageServer server = initServer(workspace);

        FieldMetadata note = new SourceModelReader().read(ORDER_WITH_NOTE)
                .orElseThrow().findField("note").orElseThrow();
        MutationOp op = new MutationOp.AddField("/entities/Order/fields/note", note);
        String baseline = trustworthyBaselineFor(ORDER);

        // First apply: clean SUCCESS, the field lands on disk.
        JsonElement first = server.applyMutation(
                new ApplyMutationParams("com.example.shop.Order", opElement(op), baseline, null))
                .get(5, TimeUnit.SECONDS);
        assertThat(outcome(first)).isEqualTo("SUCCESS");
        String afterFirst = Files.readString(order);
        assertThat(afterFirst).contains("note");
        assertThat(afterFirst).isNotEqualTo(ORDER);

        // Second apply of the same op: convergent SUCCESS no-op, on-disk state is byte-identical.
        JsonElement second = server.applyMutation(
                new ApplyMutationParams("com.example.shop.Order", opElement(op), baseline, null))
                .get(5, TimeUnit.SECONDS);
        assertThat(outcome(second)).isEqualTo("SUCCESS");
        assertThat(Files.readString(order)).isEqualTo(afterFirst);
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    void missingBaselineIsReportedAndLeavesSourceUntouched(@TempDir Path workspace) throws Exception {
        Path order = workspace.resolve("Order.java");
        Files.writeString(order, ORDER);
        ExerisLanguageServer server = initServer(workspace);

        MutationOp op = new MutationOp.RemoveField("/entities/Order/fields/code");
        JsonElement result = server.applyMutation(
                new ApplyMutationParams("com.example.shop.Order", opElement(op), null, null))
                .get(5, TimeUnit.SECONDS);

        // No trustworthy baseline → the SDK refuses to apply; the source must be untouched.
        assertThat(outcome(result)).isEqualTo("NO_BASELINE");
        assertThat(Files.readString(order)).isEqualTo(ORDER);
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    void divergedSourceYieldsConflictAndLeavesItUntouched(@TempDir Path workspace) throws Exception {
        // Baseline captured code as String; the user has since retyped it to Integer on disk (drift).
        Path order = workspace.resolve("Order.java");
        Files.writeString(order, ORDER_CODE_INT);
        ExerisLanguageServer server = initServer(workspace);
        String baseline = trustworthyBaselineFor(ORDER);

        // The op wants a third type (Long) — differs from BOTH baseline (String) and current
        // (Integer): a non-convergent collision, the case baseline-trust gating exists for.
        MutationOp op = new MutationOp.ChangeFieldType("/entities/Order/fields/code", "Long");
        JsonElement result = server.applyMutation(
                new ApplyMutationParams("com.example.shop.Order", opElement(op), baseline, null))
                .get(5, TimeUnit.SECONDS);

        assertThat(outcome(result)).isEqualTo("CONFLICT");
        assertThat(Files.readString(order)).isEqualTo(ORDER_CODE_INT); // untouched
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    void staleConcurrencyTokenIsRejectedAndLeavesSourceUntouched(@TempDir Path workspace) throws Exception {
        Path order = workspace.resolve("Order.java");
        Files.writeString(order, ORDER);
        ExerisLanguageServer server = initServer(workspace);

        FieldMetadata note = new SourceModelReader().read(ORDER_WITH_NOTE)
                .orElseThrow().findField("note").orElseThrow();
        MutationOp op = new MutationOp.AddField("/entities/Order/fields/note", note);

        // A concurrency token that does not match the live source's digest → optimistic-concurrency reject.
        JsonElement result = server.applyMutation(new ApplyMutationParams(
                "com.example.shop.Order", opElement(op), trustworthyBaselineFor(ORDER), "not-the-current-digest"))
                .get(5, TimeUnit.SECONDS);

        assertThat(outcome(result)).isEqualTo("NO_BASELINE");
        assertThat(result.getAsJsonObject().get("cause").getAsString()).isEqualTo("STALE_DIGEST");
        assertThat(Files.readString(order)).isEqualTo(ORDER); // untouched
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    void writeBackFailureSurfacesAsVerdictWithoutCorruptingTheSource(@TempDir Path workspace) throws Exception {
        Path order = workspace.resolve("Order.java");
        Files.writeString(order, ORDER);
        ExerisLanguageServer server = initServer(workspace);

        boolean readOnly = order.toFile().setWritable(false);
        // Skip where the filesystem ignores POSIX perms (e.g. CI as root) — we can't provoke the
        // write failure the test is about.
        assumeTrue(readOnly && !Files.isWritable(order),
                "environment ignores file permissions; cannot simulate a write-back failure");
        try {
            FieldMetadata note = new SourceModelReader().read(ORDER_WITH_NOTE)
                    .orElseThrow().findField("note").orElseThrow();
            MutationOp op = new MutationOp.AddField("/entities/Order/fields/note", note);

            JsonElement result = server.applyMutation(new ApplyMutationParams(
                    "com.example.shop.Order", opElement(op), trustworthyBaselineFor(ORDER), null))
                    .get(5, TimeUnit.SECONDS);

            // The op computed, the disk write failed — surfaced as a verdict (not an exception),
            // and the source on disk is intact (no partial write).
            assertThat(outcome(result)).isEqualTo("VALIDATION_ERROR");
            assertThat(Files.readString(order)).isEqualTo(ORDER);
        } finally {
            order.toFile().setWritable(true);
        }
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    void unknownDomainYieldsValidationError(@TempDir Path workspace) throws Exception {
        ExerisLanguageServer server = initServer(workspace);

        MutationOp op = new MutationOp.RemoveField("/entities/Order/fields/code");
        JsonElement result = server.applyMutation(
                new ApplyMutationParams("com.example.shop.Order", opElement(op), null, null))
                .get(5, TimeUnit.SECONDS);

        assertThat(outcome(result)).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Timeout(10)
    @SuppressWarnings("deprecation")
    void nullOpYieldsValidationError(@TempDir Path workspace) throws Exception {
        ExerisLanguageServer server = initServer(workspace);

        JsonElement result = server.applyMutation(
                new ApplyMutationParams("com.example.shop.Order", null, null, null))
                .get(5, TimeUnit.SECONDS);

        assertThat(outcome(result)).isEqualTo("VALIDATION_ERROR");
    }

    // ---- helpers ---------------------------------------------------------

    @SuppressWarnings("deprecation")
    private static ExerisLanguageServer initServer(Path workspace) throws Exception {
        ExerisLanguageServer server = new ExerisLanguageServer();
        InitializeParams params = new InitializeParams();
        params.setRootUri(workspace.toUri().toString());
        server.initialize(params).get(5, TimeUnit.SECONDS);
        return server;
    }

    /** The op as the wire carries it: SDK Jackson JSON, re-parsed into a Gson element. */
    private static JsonElement opElement(MutationOp op) {
        return JsonParser.parseString(MAPPER.writeValueAsString(op));
    }

    /** A trustworthy baseline = the source's DomainMetadata JSON + the current schemaVersion stamp. */
    private static String trustworthyBaselineFor(String source) {
        DomainMetadata model = new SourceModelReader().read(source).orElseThrow();
        ObjectNode node = (ObjectNode) MAPPER.valueToTree(model);
        node.put("schemaVersion", SchemaVersion.CURRENT);
        return MAPPER.writeValueAsString(node);
    }

    private static String outcome(JsonElement result) {
        return result.getAsJsonObject().get("outcome").getAsString();
    }
}
