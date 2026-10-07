package eu.exeris.platform.lsp;

import java.util.ArrayList;
import java.util.List;

/**
 * A unified diff between two versions of one source, in the format {@code git apply} and
 * {@code patch} read: {@code ---} / {@code +++} headers, then hunks with three lines of context.
 *
 * <p>The writer changes a source locally — one member added, removed or rewritten — so the common
 * head and tail are matched directly and only the region between them goes through a longest-common-
 * subsequence table. A line that ends the text without a newline is followed by the standard
 * {@code \ No newline at end of file} marker, so the patch reproduces the bytes exactly.
 */
final class UnifiedDiff {

    private static final int CONTEXT = 3;
    private static final String NO_NEWLINE = "\\ No newline at end of file";

    private UnifiedDiff() {
    }

    /** One line of the edit script: {@code ' '} kept, {@code '-'} removed, {@code '+'} added. */
    private record Edit(char kind, Line line) {
    }

    /** A line without its terminator, and whether the text ended on it without a newline. */
    private record Line(String text, boolean lastWithoutNewline) {
    }

    /**
     * The diff that turns {@code before} into {@code after}.
     *
     * @param path   the file's path as both headers name it, relative to the workspace when possible
     * @param before the source as it is
     * @param after  the source as it would become
     * @return the unified diff, or the empty string when the two are identical
     */
    static String of(String path, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        List<Edit> edits = edits(lines(before), lines(after));
        StringBuilder out = new StringBuilder()
                .append("--- a/").append(path).append('\n')
                .append("+++ b/").append(path).append('\n');
        appendHunks(out, edits);
        return out.toString();
    }

    private static List<Line> lines(String text) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) {
                lines.add(new Line(text.substring(start), true));
                break;
            }
            lines.add(new Line(text.substring(start, newline), false));
            start = newline + 1;
        }
        return lines;
    }

    private static List<Edit> edits(List<Line> a, List<Line> b) {
        int head = 0;
        while (head < a.size() && head < b.size() && a.get(head).equals(b.get(head))) {
            head++;
        }
        int tail = 0;
        while (tail < a.size() - head && tail < b.size() - head
                && a.get(a.size() - 1 - tail).equals(b.get(b.size() - 1 - tail))) {
            tail++;
        }

        List<Edit> edits = new ArrayList<>();
        for (int i = 0; i < head; i++) {
            edits.add(new Edit(' ', a.get(i)));
        }
        edits.addAll(middle(a.subList(head, a.size() - tail), b.subList(head, b.size() - tail)));
        for (int i = a.size() - tail; i < a.size(); i++) {
            edits.add(new Edit(' ', a.get(i)));
        }
        return edits;
    }

    /** The edit script of the changed region, from its longest common subsequence of lines. */
    private static List<Edit> middle(List<Line> a, List<Line> b) {
        int[][] lcs = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                lcs[i][j] = a.get(i).equals(b.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Edit> edits = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.size() || j < b.size()) {
            if (i < a.size() && j < b.size() && a.get(i).equals(b.get(j))) {
                edits.add(new Edit(' ', a.get(i++)));
                j++;
            } else if (i < a.size() && (j == b.size() || lcs[i + 1][j] >= lcs[i][j + 1])) {
                // On a tie the removal goes first, so a replaced line reads as "-old" then "+new".
                edits.add(new Edit('-', a.get(i++)));
            } else {
                edits.add(new Edit('+', b.get(j++)));
            }
        }
        return edits;
    }

    private static void appendHunks(StringBuilder out, List<Edit> edits) {
        int index = 0;
        while (index < edits.size()) {
            int firstChange = nextChange(edits, index);
            if (firstChange < 0) {
                return;
            }
            int lastChange = firstChange;
            int next = nextChange(edits, lastChange + 1);
            while (next >= 0 && next - lastChange <= 2 * CONTEXT) {
                lastChange = next;
                next = nextChange(edits, lastChange + 1);
            }
            int start = Math.max(0, firstChange - CONTEXT);
            int end = Math.min(edits.size(), lastChange + CONTEXT + 1);
            appendHunk(out, edits, start, end);
            index = end;
        }
    }

    private static int nextChange(List<Edit> edits, int from) {
        for (int i = from; i < edits.size(); i++) {
            if (edits.get(i).kind() != ' ') {
                return i;
            }
        }
        return -1;
    }

    private static void appendHunk(StringBuilder out, List<Edit> edits, int start, int end) {
        int oldBefore = 0;
        int newBefore = 0;
        for (int i = 0; i < start; i++) {
            char kind = edits.get(i).kind();
            if (kind != '+') {
                oldBefore++;
            }
            if (kind != '-') {
                newBefore++;
            }
        }
        int oldCount = 0;
        int newCount = 0;
        for (int i = start; i < end; i++) {
            char kind = edits.get(i).kind();
            if (kind != '+') {
                oldCount++;
            }
            if (kind != '-') {
                newCount++;
            }
        }
        out.append("@@ -").append(range(oldBefore, oldCount))
                .append(" +").append(range(newBefore, newCount)).append(" @@\n");
        for (int i = start; i < end; i++) {
            Edit edit = edits.get(i);
            out.append(edit.kind()).append(edit.line().text()).append('\n');
            if (edit.line().lastWithoutNewline()) {
                out.append(NO_NEWLINE).append('\n');
            }
        }
    }

    /** A hunk range: an empty range names the line before it, as the format requires. */
    private static String range(int before, int count) {
        return (count == 0 ? before : before + 1) + "," + count;
    }
}
