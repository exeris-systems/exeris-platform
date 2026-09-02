/**
 * Studio backend skeleton.
 *
 * <p><b>Status:</b> the module now carries its own domain
 * ({@link eu.exeris.studio.workspace.Workspace}, modelled with {@code @ExerisDomain} and emitted
 * to the {@code exeris-metadata} corpus by the annotation processor); the REST/HTTP surface over
 * it is still to come.
 *
 * <p>The classes {@code EntityDefinition}, {@code PropertyDefinition},
 * {@code RelationDefinition}, {@code Project}, {@code ProjectStatus} were
 * deliberately removed during the repo split — they introduced a parallel
 * metamodel that diverged from the canonical {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata}.
 * Their names remain banned in this module, which is why the workspace record is called
 * {@code Workspace}.
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
 * already hosts the custom {@code exeris/*} extensions. What remains is this module's own
 * workspace-state surface — the domain is modelled, the surface over it is not built.
 */
package eu.exeris.studio;
