package eu.exeris.platform.lsp;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one Jackson mapper through which this server speaks the SDK's JSON contract.
 *
 * <p>Anything that crosses the wire in the SDK's own serialized form — the {@code MutationOp} /
 * {@code MutationResult} payloads, and the enum constants the read projections carry — goes through
 * this mapper, so the wire value is whatever the SDK's Jackson annotations say it is rather than a
 * platform-side spelling of it. Configured per the SDK consumer contract (source-model AST
 * {@code package-info}): Jackson 3 with null-to-primitive coercion tolerated. Thread-safe.
 */
final class SdkJson {

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    private SdkJson() {
    }

    /** The SDK's serialized form of an enum constant, or {@code null} for a null constant. */
    static String enumValue(Enum<?> constant) {
        return constant == null ? null : MAPPER.convertValue(constant, String.class);
    }
}
