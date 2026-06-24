package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Workspace-level notifications. No-op in the 0.2.0 skeleton — {@code didChangeWatchedFiles}
 * is where on-disk edits made outside the editor will later invalidate the domain index.
 */
final class ExerisWorkspaceService implements WorkspaceService {

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // no-op (skeleton)
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // no-op (skeleton)
    }
}
