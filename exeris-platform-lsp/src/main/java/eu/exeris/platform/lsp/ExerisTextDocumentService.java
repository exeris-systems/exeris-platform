package eu.exeris.platform.lsp;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Document-sync notifications. No-op in the 0.2.0 skeleton — once the workspace index
 * lands, {@code didSave} / {@code didChange} will invalidate cached {@code DomainMetadata}
 * for the affected source so {@code exeris/*} reads stay fresh.
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
