/**
 * Exeris Platform LSP server.
 *
 * <p><b>Status:</b> the server speaks JSON-RPC over stdio (LSP4J), answers the base
 * lifecycle ({@code initialize} / {@code shutdown} / {@code exit}), serves the read-only
 * {@code exeris/*} trio ({@code exeris/domains}, {@code exeris/domainDescribe},
 * {@code exeris/actions}) backed by the {@code exeris-sdk-source-model-io} reader (ADR-037),
 * and applies single mutations via {@code exeris/applyMutation} (ADR-042) through the
 * conflict-aware, idempotent {@code source-model-io} writer. The canonical AST records, the
 * {@code MutationOp} / {@code MutationResult} wire shapes, and the conflict/baseline-trust
 * semantics all come from {@code exeris-sdk-source-model}(-io). This unblocks the {@code lsp:*}
 * tool family in exeris-ai-bridge (ADR-025).
 *
 * <p>Planned scope:
 * <ul>
 *   <li>Standard LSP transport (JSON-RPC over stdio / TCP / WebSocket) so
 *       that Studio (Angular over WebSocket), IntelliJ plugin (in-JVM), and
 *       VS Code extension (LSP client) all consume the same server.</li>
 *   <li>Custom Exeris extensions under {@code exeris/}: the read-only
 *       {@code exeris/domains}, {@code exeris/domainDescribe}, {@code exeris/actions}
 *       and the write-back {@code exeris/applyMutation} (all shipped).</li>
 *   <li>File watching with versioned document handling for conflict-free
 *       Studio↔IDE bidirectional sync.</li>
 *   <li>Idempotent {@code .java} write-back via the
 *       {@code exeris-sdk-source-model-io} writer, preserving formatting,
 *       comments, and {@code *Impl} custom regions — applying the same
 *       {@code MutationOp} twice converges to identical on-disk state.</li>
 * </ul>
 */
package eu.exeris.platform.lsp;
