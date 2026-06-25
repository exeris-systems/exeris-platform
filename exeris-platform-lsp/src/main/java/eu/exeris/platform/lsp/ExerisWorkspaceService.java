package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Workspace-level notifications.
 *
 * <p>{@code didChangeWatchedFiles} invalidates the domain index: it fires when {@code .java}
 * sources change on disk outside the editor (git pull, external tooling, file create/delete),
 * which is exactly the disk-truth the index is built from. The server registers a recursive
 * Java-source watcher at {@code initialized} time for clients that support dynamic registration.
 */
final class ExerisWorkspaceService implements WorkspaceService {

    private final Runnable onSourcesChanged;

    ExerisWorkspaceService(Runnable onSourcesChanged) {
        this.onSourcesChanged = onSourcesChanged;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // no-op: configuration changes don't affect the on-disk domain model.
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // On-disk .java sources changed outside the editor; drop the cached scan.
        onSourcesChanged.run();
    }
}
