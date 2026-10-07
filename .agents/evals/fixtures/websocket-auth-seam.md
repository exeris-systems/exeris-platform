# Proposal: an authentication seam on the WebSocket endpoint

Add a `ConnectionAuthenticator` interface to `exeris-platform-lsp`, discovered with `ServiceLoader`,
with a single method `boolean accept(Map<String, List<String>> handshakeHeaders)`, consulted once
per WebSocket connection before the language server is allocated. The open distribution ships no
implementation, and with none present every connection is accepted exactly as today; stdio is not
affected. The interface, its Javadoc and a test asserting a connection is accepted without a
provider are the whole change.
