package eu.exeris.platform.lsp;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Scans a workspace root for {@code @ExerisDomain} Java sources and exposes the parsed
 * canonical {@link DomainMetadata}.
 *
 * <p>Read-only: this is the query side backing the {@code exeris/*} methods; it never
 * writes. The index is built lazily on first query and cached; {@link #invalidate()} is called
 * by {@code textDocument/didSave} and {@code workspace/didChangeWatchedFiles} to drop the cache
 * so the next read re-parses from disk.
 */
final class WorkspaceIndex {

    private static final System.Logger LOG = System.getLogger(WorkspaceIndex.class.getName());

    /** A parsed domain paired with the on-disk source it was read from. */
    record IndexedDomain(DomainMetadata metadata, Path sourcePath) {
    }

    private final Path root;
    private final SourceModelReader reader = new SourceModelReader();

    private List<IndexedDomain> cache;

    WorkspaceIndex(Path root) {
        this.root = root;
    }

    synchronized List<IndexedDomain> domains() {
        if (cache == null) {
            cache = scan();
        }
        return cache;
    }

    Optional<IndexedDomain> findByQualifiedName(String qualifiedName) {
        return domains().stream()
                .filter(d -> d.metadata().fullyQualifiedName().equals(qualifiedName))
                .findFirst();
    }

    /** Drops the cached scan so the next query re-reads from disk. */
    synchronized void invalidate() {
        cache = null;
    }

    private List<IndexedDomain> scan() {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        List<IndexedDomain> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> readDomain(p).ifPresent(found::add));
        } catch (IOException | UncheckedIOException e) {
            // An unreadable subdirectory must not sink the whole index. Files.walk surfaces an
            // AccessDeniedException from the initial call as IOException and one hit during lazy
            // traversal as UncheckedIOException — catch both and return whatever was collected.
            // Logged to stderr, which is separate from the stdio JSON-RPC channel on stdout.
            LOG.log(System.Logger.Level.WARNING,
                    () -> "Partial workspace index: walk interrupted under " + root, e);
        }
        return List.copyOf(found);
    }

    private Optional<IndexedDomain> readDomain(Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException unreadable) {
            // An unreadable file is skipped rather than failing the whole index.
            return Optional.empty();
        }
        try {
            return reader.read(source).map(metadata -> new IndexedDomain(metadata, file));
        } catch (RuntimeException readFailure) {
            // The reader throws IllegalArgumentException on unparseable Java; we defensively
            // catch any unchecked failure so a single bad / work-in-progress source (or an
            // internal reader error) is skipped rather than failing the whole index build.
            return Optional.empty();
        }
    }
}
