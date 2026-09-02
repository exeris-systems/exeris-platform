package eu.exeris.studio.workspace;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import java.time.Instant;

/**
 * A source tree Studio has open, and the first piece of this platform modelled the way the
 * platform asks its users to model.
 *
 * <p>This is deliberately the backend's own operational state and nothing else. It is
 * <em>not</em> a description of a user's domain — that shape is {@code DomainMetadata}, it
 * lives in the SDK, and it reaches Studio over LSP. A workspace records which tree is open and
 * when it was last touched; the entities inside that tree are read from disk on demand and are
 * never mirrored here.
 *
 * <p>Named {@code Workspace} rather than {@code Project} on purpose. {@code Project} and
 * {@code ProjectStatus} are Corelio-era types this module deleted and bans by name, and while a
 * workspace record is a different thing from the metamodel those types carried, reusing the name
 * would make every future reviewer re-litigate that. {@code Workspace} is also what the LSP
 * already calls it — {@code rootUri} at {@code initialize} names exactly this.
 */
@ExerisDomain(
        module = "studio",
        path = "workspaces",
        description = "A source tree open in Studio, and the platform's own first dogfooded domain.")
public class Workspace {

    @Field(label = "Name", required = true, inList = true, searchable = true, order = 10)
    private String name;

    /** Absolute path to the tree root — the same value the LSP receives as {@code rootUri}. */
    @Field(label = "Root path", required = true, inList = true, order = 20)
    private String rootPath;

    @Field(label = "Last opened", sortable = true, inList = true, order = 30)
    private Instant lastOpenedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public void setLastOpenedAt(Instant lastOpenedAt) {
        this.lastOpenedAt = lastOpenedAt;
    }
}
