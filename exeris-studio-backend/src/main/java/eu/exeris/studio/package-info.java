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
 * <p>Real implementation lands once {@code exeris-sdk-source-model} grows its
 * parser/writer and {@code exeris-platform-lsp} can host the LSP custom
 * extensions for Exeris.
 */
package eu.exeris.studio;
