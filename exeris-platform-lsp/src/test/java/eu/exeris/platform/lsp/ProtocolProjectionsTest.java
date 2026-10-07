package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.DomainDescription;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.RelationshipDescription;
import eu.exeris.platform.lsp.WorkspaceIndex.IndexedDomain;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata.RelationType;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.jupiter.api.Test;

/**
 * Pins what {@code exeris/domainDescribe} puts on the wire for the {@code relationships} facet.
 *
 * <p>Serialization goes through LSP4J's own {@link MessageJsonHandler}, built exactly as the
 * launcher builds it (supported methods of {@link ExerisLanguageServer}, default Gson), so the
 * assertions are about the JSON-RPC response bytes and not about a mapper the wire never uses.
 */
class ProtocolProjectionsTest {

    private static final MessageJsonHandler WIRE =
            new MessageJsonHandler(ServiceEndpoints.getSupportedMethods(ExerisLanguageServer.class));

    @Test
    void aFacetTheModelDoesNotCarryIsOmittedFromTheWire() {
        JsonObject result = describeOnTheWire(domainWith(null));

        assertThat(result.has("relationships")).as("null facet = not carried = absent").isFalse();
        assertThat(result.has("fields")).isTrue();
    }

    @Test
    void aCarriedButEmptyFacetIsAnEmptyArrayOnTheWire() {
        JsonObject result = describeOnTheWire(domainWith(List.of()));

        assertThat(result.getAsJsonArray("relationships")).as("[] = declares none").isEmpty();
    }

    @Test
    void aRelationshipCarriesTheSdkComponentNamesAndTheSdkSerializedType() {
        RelationshipMetadata items = RelationshipMetadata.builder("items", "OrderItem")
                .type(RelationType.ONE_TO_MANY)
                .build();

        JsonObject result = describeOnTheWire(domainWith(List.of(items)));

        assertThat(result.getAsJsonArray("relationships"))
                .hasToString("[{\"name\":\"items\",\"targetEntity\":\"OrderItem\",\"type\":\"ONE_TO_MANY\"}]");
    }

    @Test
    void theTypeIsWhateverTheSdkMapperSerializesNotAPlatformSpelling() {
        for (RelationType type : RelationType.values()) {
            String sdkForm = SdkJson.MAPPER.writeValueAsString(type);
            RelationshipDescription projected = ProtocolProjections.toDescription(indexed(domainWith(
                    List.of(RelationshipMetadata.builder("r", "T").type(type).build()))))
                    .relationships().get(0);

            assertThat('"' + projected.type() + '"').as("%s", type).isEqualTo(sdkForm);
        }
    }

    @Test
    void anAbsentTypeIsOmittedRatherThanInvented() {
        RelationshipMetadata untyped = new RelationshipMetadata("owner", "owner", "Customer", null,
                null, true, null, null, true, false, null, null, null, List.of());

        JsonObject relationship = describeOnTheWire(domainWith(List.of(untyped)))
                .getAsJsonArray("relationships").get(0).getAsJsonObject();

        assertThat(relationship.has("type")).isFalse();
        assertThat(relationship.get("targetEntity").getAsString()).isEqualTo("Customer");
    }

    @Test
    void sourceDigestIsCarriedWhenPresentAndOmittedWhenAbsent() {
        IndexedDomain withDigest = new IndexedDomain(domainWith(List.of()), Path.of("/ws/com/example/Order.java"), "sha256:abc");
        JsonObject resultWith = describeIndexedOnTheWire(withDigest);
        assertThat(resultWith.has("sourceDigest")).isTrue();
        assertThat(resultWith.get("sourceDigest").getAsString()).isEqualTo("sha256:abc");

        IndexedDomain withoutDigest = new IndexedDomain(domainWith(List.of()), Path.of("/ws/com/example/Order.java"), null);
        JsonObject resultWithout = describeIndexedOnTheWire(withoutDigest);
        assertThat(resultWithout.has("sourceDigest")).isFalse();
    }

    private static DomainMetadata domainWith(List<RelationshipMetadata> relationships) {
        return DomainMetadata.builder("Order", "com.example").relationships(relationships).build();
    }

    private static IndexedDomain indexed(DomainMetadata metadata) {
        return new IndexedDomain(metadata, Path.of("/ws/com/example/Order.java"));
    }

    private static JsonObject describeOnTheWire(DomainMetadata metadata) {
        return describeIndexedOnTheWire(indexed(metadata));
    }

    private static JsonObject describeIndexedOnTheWire(IndexedDomain indexed) {
        DomainDescription description = ProtocolProjections.toDescription(indexed);
        ResponseMessage response = new ResponseMessage();
        response.setId(1);
        response.setResult(description);
        return JsonParser.parseString(WIRE.serialize(response)).getAsJsonObject().getAsJsonObject("result");
    }
}
