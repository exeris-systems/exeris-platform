package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The diff {@code exeris/previewMutation} returns must be one {@code git apply} accepts. */
class UnifiedDiffTest {

    @Test
    void identicalTextsHaveNoDiff() {
        assertThat(UnifiedDiff.of("Order.java", "a\nb\n", "a\nb\n")).isEmpty();
    }

    @Test
    void anInsertedLineIsOneHunkWithThreeLinesOfContext() {
        String before = "1\n2\n3\n4\n5\n6\n7\n8\n";
        String after = "1\n2\n3\n4\nnew\n5\n6\n7\n8\n";

        assertThat(UnifiedDiff.of("src/Order.java", before, after)).isEqualTo("""
                --- a/src/Order.java
                +++ b/src/Order.java
                @@ -2,6 +2,7 @@
                 2
                 3
                 4
                +new
                 5
                 6
                 7
                """);
    }

    @Test
    void aReplacedLineIsRemovedThenAdded() {
        assertThat(UnifiedDiff.of("O.java", "x\nString code;\ny\n", "x\nLong code;\ny\n")).isEqualTo("""
                --- a/O.java
                +++ b/O.java
                @@ -1,3 +1,3 @@
                 x
                -String code;
                +Long code;
                 y
                """);
    }

    @Test
    void distantChangesAreSeparateHunks() {
        String before = "a\n1\n2\n3\n4\n5\n6\n7\n8\nb\n";
        String after = "A\n1\n2\n3\n4\n5\n6\n7\n8\nB\n";

        assertThat(UnifiedDiff.of("f", before, after).lines().filter(l -> l.startsWith("@@")))
                .containsExactly("@@ -1,4 +1,4 @@", "@@ -7,4 +7,4 @@");
    }

    @Test
    void additionsToAnEmptyTextStartAtLineZero() {
        assertThat(UnifiedDiff.of("f", "", "a\n")).isEqualTo("""
                --- a/f
                +++ b/f
                @@ -0,0 +1,1 @@
                +a
                """);
    }

    @Test
    void aMissingFinalNewlineIsMarked() {
        assertThat(UnifiedDiff.of("f", "a\nb", "a\nb\n")).isEqualTo("""
                --- a/f
                +++ b/f
                @@ -1,2 +1,2 @@
                 a
                -b
                \\ No newline at end of file
                +b
                """);
    }
}
