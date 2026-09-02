package eu.exeris.studio.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asserts that the annotation processor still emits this module's domain into the
 * {@code exeris-metadata} corpus, and that it lands where a consumer would look for it.
 *
 * <p>Compiling {@link Workspace} proves nothing about this. The processor is wired through
 * {@code annotationProcessorPaths}, and that wiring can be broken — a dependency-management
 * change, a compiler-plugin upgrade, a stray exclusion — while the module still compiles
 * perfectly, because an annotation processor that never runs is silent by construction. The
 * corpus is the module's actual output: {@code @exeris/codegen-ts} and {@code codegen-java} both
 * read it, so a silent stop here is a downstream break with no local symptom.
 *
 * <p>Deliberately read as a <em>classpath resource</em> rather than from {@code target/classes} by
 * path. That is the same way a packaged consumer sees it, so this covers emission and packaging in
 * one assertion instead of only the first.
 */
class MetadataCorpusTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void theProcessorEmitsThisModulesDomainWhereAConsumerWillFindIt() throws Exception {
        JsonNode metadata = readCorpusEntry("/exeris-metadata/Workspace.json");

        assertThat(metadata.path("entityName").asString()).isEqualTo("Workspace");
        assertThat(metadata.path("packageName").asString()).isEqualTo("eu.exeris.studio.workspace");
        // module + path are what a generated REST surface would be mounted under, so they are
        // part of the contract rather than incidental annotation values.
        assertThat(metadata.path("module").asString()).isEqualTo("studio");
        assertThat(metadata.path("path").asString()).isEqualTo("workspaces");
    }

    @Test
    void theEmittedFieldsAreTheOnesTheClassDeclares() throws Exception {
        JsonNode fields = readCorpusEntry("/exeris-metadata/Workspace.json").path("fields");

        assertThat(fields).hasSize(3);
        assertThat(fields).extracting(f -> f.path("name").asString())
                .containsExactlyInAnyOrder("name", "rootPath", "lastOpenedAt");
    }

    @Test
    void theBaselineTrustFieldsAreStamped() throws Exception {
        JsonNode metadata = readCorpusEntry("/exeris-metadata/Workspace.json");

        // ADR-042: every emitted entity JSON carries the two trust fields, and the LSP's
        // exeris/applyMutation refuses a baseline without them. Their absence would not fail a
        // compile — it would fail a write-back, far from here.
        assertThat(metadata.path("schemaVersion").asString()).isNotBlank();
        assertThat(metadata.path("sourceDigest").asString()).isNotBlank();
    }

    private static JsonNode readCorpusEntry(String resource) throws Exception {
        try (InputStream in = MetadataCorpusTest.class.getResourceAsStream(resource)) {
            assertThat(in)
                    .as("%s on the classpath — the processor did not run, or its output moved", resource)
                    .isNotNull();
            return MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
