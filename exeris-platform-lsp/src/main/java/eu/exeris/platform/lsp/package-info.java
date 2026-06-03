/**
 * Exeris Platform LSP server skeleton.
 *
 * <p><b>Status:</b> placeholder. Real implementation depends on the
 * {@code exeris-sdk-source-model-io} module (ADR-037) shipping its
 * JavaParser-based parser/writer; the canonical AST records come from
 * {@code exeris-sdk-source-model}.
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
