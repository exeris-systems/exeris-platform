package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Document-sync notifications. No-op for now — when the {@code didSave}/{@code didChange}
 * invalidation is wired (next slice), these will call {@link WorkspaceIndex#invalidate()} so
 * {@code exeris/*} reads reflect edits made in the editor.
 */
final class ExerisTextDocumentService implements TextDocumentService {

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        // no-op (skeleton)
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // no-op (skeleton)
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        // no-op (skeleton)
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // no-op (skeleton)
    }
}
