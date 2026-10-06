// Proposed: exeris-studio-backend/src/main/java/eu/exeris/studio/tree/EntityNode.java
//
// The workspace tree in Studio needs entity names and their fields without a round-trip to the
// LSP on every expand, so the backend caches them and serves them from GET /api/workspaces/{id}/tree.
package eu.exeris.studio.tree;

import java.util.List;

public record EntityNode(String name, String tableName, List<FieldNode> fields) {

    public record FieldNode(String name, String javaType, boolean nullable) {}
}
