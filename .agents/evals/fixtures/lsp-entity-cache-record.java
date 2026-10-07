// Proposed: exeris-platform-lsp/src/main/java/eu/exeris/platform/lsp/EntityDescriptor.java
//
// exeris/domainDescribe re-reads the source on every call. Keep a descriptor per entity, filled on
// first read and updated by applyMutation, and answer describe calls from it.
package eu.exeris.platform.lsp;

import java.util.List;

public record EntityDescriptor(String name, String tableName, List<FieldDescriptor> fields) {

    public record FieldDescriptor(String name, String javaType, boolean nullable) {}
}
