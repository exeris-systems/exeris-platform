// Proposed: exeris-studio-backend/src/main/java/eu/exeris/studio/workspace/WorkspaceSession.java
//
// Tracks which Studio user has which workspace open, so the backend can release the LSP process
// for a workspace nobody has open. Persisted with the rest of the backend's workspace state.
package eu.exeris.studio.workspace;

import java.time.Instant;

public record WorkspaceSession(String workspaceId, String userId, Instant openedAt, Instant lastSeenAt) {}
