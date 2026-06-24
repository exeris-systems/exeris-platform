/**
 * Exeris Platform LSP server.
 *
 * <p><b>Status:</b> the server speaks JSON-RPC over stdio (LSP4J), answers the base
 * lifecycle ({@code initialize} / {@code shutdown} / {@code exit}), and serves the read-only
 * {@code exeris/*} trio ({@code exeris/domains}, {@code exeris/domainDescribe},
 * {@code exeris/actions}) backed by the {@code exeris-sdk-source-model-io} reader (ADR-037);
 * the canonical AST records come from {@code exeris-sdk-source-model}. This unblocks the
 * {@code lsp:*} tool family in exeris-ai-bridge (ADR-025). Write-back is a later slice.
 *
 * <p>Planned scope:
 * <ul>
 *   <li>Standard LSP transport (JSON-RPC over stdio / TCP / WebSocket) so
 *       that Studio (Angular over WebSocket), IntelliJ plugin (in-JVM), and
 *       VS Code extension (LSP client) all consume the same server.</li>
 *   <li>Custom Exeris extensions: {@code exeris/entityModel},
 *       {@code exeris/applyMutation}, {@code exeris/listCapabilities},
 *       {@code exeris/diagnostics}.</li>
 *   <li>File watching with versioned document handling for conflict-free
 *       Studio↔IDE bidirectional sync.</li>
 *   <li>Idempotent {@code .java} write-back via the
 *       {@code exeris-sdk-source-model-io} writer, preserving formatting,
 *       comments, and {@code *Impl} custom regions.</li>
 * </ul>
 */
package eu.exeris.platform.lsp;
