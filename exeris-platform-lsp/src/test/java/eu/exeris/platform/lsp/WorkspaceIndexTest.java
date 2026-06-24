package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;

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
}
