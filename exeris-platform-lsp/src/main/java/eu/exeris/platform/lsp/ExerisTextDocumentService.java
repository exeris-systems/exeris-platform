package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Document-sync notifications.
 *
 * <p>Only {@code didSave} is wired: when a buffer hits disk we invalidate the workspace index so
 * the next {@code exeris/*} read re-parses it. {@code didOpen} / {@code didChange} /
 * {@code didClose} stay no-ops by design — the index reads {@code .java} from disk via
 * {@code source-model-io}, and an unsaved editor buffer is not on disk, so reacting to keystrokes
 * would only re-read stale content. Out-of-band disk edits are handled by
 * {@code workspace/didChangeWatchedFiles} in {@link ExerisWorkspaceService}.
 */
final class ExerisTextDocumentService implements TextDocumentService {

    private final Runnable onSourcesChanged;

    ExerisTextDocumentService(Runnable onSourcesChanged) {
        this.onSourcesChanged = onSourcesChanged;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        // no-op: opening a buffer doesn't change what's on disk.
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // no-op by design: unsaved edits aren't on disk; the index re-parses on save instead.
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // no-op: closing a buffer doesn't change what's on disk.
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // The buffer just hit disk. Whole-index invalidation is coarse (a later slice can do a
        // targeted single-file refresh); for now the next read re-scans the workspace.
        onSourcesChanged.run();
    }
}
