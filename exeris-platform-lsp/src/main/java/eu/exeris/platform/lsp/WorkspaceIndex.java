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
 * writes. The index is built lazily on first query and cached — cache invalidation on
 * {@code didSave}/{@code didChange} is a later slice (see {@link #invalidate()}).
 */
final class WorkspaceIndex {

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
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk workspace root " + root, e);
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
        } catch (IllegalArgumentException notValidJava) {
            // A .java file that doesn't parse (work-in-progress, template) is skipped.
            return Optional.empty();
        }
    }
}
