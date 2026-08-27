/**
 * Studio backend skeleton.
 *
 * <p><b>Status:</b> placeholder. The Corelio-era classes
 * {@code EntityDefinition}, {@code PropertyDefinition},
 * {@code RelationDefinition}, {@code Project}, {@code ProjectStatus} were
 * deliberately removed during the repo split — they introduced a parallel
 * metamodel that diverged from the canonical {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata}.
 *
 * <p>The replacement design is:
 * <ul>
 *   <li>Studio operates on {@code DomainMetadata} (read) and {@code .java}
 *       sources (write) via {@code exeris-platform-lsp}.</li>
 *   <li>Studio backend has no proprietary domain model. It only holds
 *       project workspace state (filesystem pointers, last-edited cursor,
 *       user preferences).</li>
 *   <li>All domain-shape edits flow through the LSP server.</li>
 * </ul>
 *
 * <p>Both upstream pieces have since landed: the parser/writer ships in
 * {@code exeris-sdk-source-model-io} (ADR-037), and {@code exeris-platform-lsp}
 * already hosts the custom {@code exeris/*} extensions. What remains a
 * placeholder is this module's own workspace-state surface.
 */
package eu.exeris.studio;
