// Proposed: exeris-platform-lsp/src/main/java/eu/exeris/platform/lsp/OpenDocument.java
//
// Tracks which documents a connection has open and at which version, so textDocument/didChange can
// reject an out-of-order version and the server can drop state when the connection closes.
package eu.exeris.platform.lsp;

import java.net.URI;

record OpenDocument(URI uri, int version, String sourceDigest) {}
