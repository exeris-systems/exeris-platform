package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit coverage for the workspace scan: filtering, caching, and resilience to bad input. */
class WorkspaceIndexTest {

    @Test
    void nullRootYieldsEmptyIndex() {
        assertThat(new WorkspaceIndex(null).domains()).isEmpty();
    }

    @Test
    void plainNonDomainSourcesAreNotIndexed(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("Helper.java"), """
                package com.example;

                public class Helper {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """);

        assertThat(new WorkspaceIndex(workspace).domains()).isEmpty();
    }

    @Test
    void invalidateForcesReReadOnNextQuery(@TempDir Path workspace) throws Exception {
        WorkspaceIndex index = new WorkspaceIndex(workspace);
        assertThat(index.domains()).isEmpty();

        Files.writeString(workspace.resolve("Account.java"), """
                package com.example.bank;

                import eu.exeris.sdk.annotations.ExerisDomain;

                @ExerisDomain(name = "Account")
                public class Account {
                }
                """);

        // Cache still reflects the empty first scan until invalidated.
        assertThat(index.domains()).isEmpty();

        index.invalidate();
        assertThat(index.domains())
                .singleElement()
                .satisfies(d -> assertThat(d.metadata().fullyQualifiedName())
                        .isEqualTo("com.example.bank.Account"));
    }

    @Test
    void unreadableSubdirectoryDoesNotSinkTheScan(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("Account.java"), """
                package com.example.bank;

                import eu.exeris.sdk.annotations.ExerisDomain;

                @ExerisDomain(name = "Account")
                public class Account {
                }
                """);
        Path locked = Files.createDirectory(workspace.resolve("locked"));
        boolean restricted = locked.toFile().setReadable(false) & locked.toFile().setExecutable(false);
        // Skip where the filesystem ignores POSIX perms (e.g. CI running as root) — we can't
        // provoke the AccessDeniedException the test is about.
        assumeTrue(restricted && !Files.isReadable(locked),
                "environment ignores directory permissions; cannot simulate AccessDenied");

        try {
            // Walk hits the unreadable dir mid-traversal (UncheckedIOException); the scan must
            // swallow it and return, not propagate. (Partial contents are order-dependent, so we
            // only assert it doesn't throw.)
            assertThatCode(() -> new WorkspaceIndex(workspace).domains()).doesNotThrowAnyException();
        } finally {
            locked.toFile().setReadable(true);
            locked.toFile().setExecutable(true);
        }
    }
}
