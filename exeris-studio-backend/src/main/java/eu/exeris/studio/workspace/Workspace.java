package eu.exeris.studio.workspace;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import java.time.Instant;

/**
 * A source tree that Studio has open: where it is on disk, what to call it, and when it was last
 * worked on.
 *
 * <p>A workspace is the unit a user switches between. Everything Studio shows — domains, actions,
 * relationships — is read out of the tree this record points at, on demand and at the time of
 * asking. The record itself holds no domain shape: that is {@code DomainMetadata}, it lives in the
 * SDK, and it reaches Studio over LSP. Nothing about the entities inside the tree is mirrored here,
 * and adding such a field would be the parallel-metamodel regression this module exists to refuse.
 *
 * <p>Named {@code Workspace} rather than {@code Project} on purpose: {@code Project} and
 * {@code ProjectStatus} are among the metamodel types this module deleted and bans by name, and
 * reusing the name would make every future reviewer re-litigate a settled question. It is also the
 * word the protocol already uses — {@code rootUri} at {@code initialize} names exactly this.
 */
@ExerisDomain(
        module = "studio",
        path = "workspaces",
        description = "A source tree open in Studio: its location on disk, its name, and when it was last worked on.")
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
