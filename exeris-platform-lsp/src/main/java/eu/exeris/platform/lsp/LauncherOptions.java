package eu.exeris.platform.lsp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Parsed command line options for {@link LspMain}.
 *
 * <p><b>Binding beyond loopback is an explicit act.</b> {@code exeris/applyMutation} writes to the
 * workspace's sources and the WebSocket endpoint authenticates nobody, so a non-loopback bind hands
 * that write path to anyone who can reach the port. A warning in a log nobody reads is not consent;
 * {@link #parse} therefore refuses such a bind unless {@code --allow-remote} is given.
 *
 * <p><b>Browser origins are an explicit allowlist.</b> A WebSocket handshake is not subject to CORS,
 * so any page the user visits could otherwise open a connection to a local server. The kernel admits
 * only listed {@code Origin} values (exact match); a client sending no {@code Origin} is not a
 * browser and is not subject to the list. The default admits the Studio dev server only; any other
 * front end is listed with {@code --allowed-origin}, which replaces the default rather than adding
 * to it, so the effective set is always exactly what the command line shows.
 *
 * <p><b>The workspace root can be pinned.</b> Without {@code --root}, every session indexes, and
 * {@code exeris/applyMutation} writes under, whatever root its client names in {@code initialize}.
 * With it, the root is fixed when the server starts, on either transport, and a session whose
 * client names another root is refused (see {@link ExerisLanguageServer}). It is held as a real
 * path, so a symbolic link or a {@code ..} segment names the directory it resolves to.
 *
 * @param transport      which wire to serve (STDIO or WEBSOCKET)
 * @param host           interface to bind, WebSocket only
 * @param port           port to bind, WebSocket only
 * @param allowedOrigins browser origins admitted by the WebSocket handshake, WebSocket only
 * @param allowRemote    whether a non-loopback {@code host} was explicitly opted into
 * @param root           the pinned workspace root as a real path, or {@code null} when the client
 *                       names it
 */
public record LauncherOptions(Transport transport, String host, int port, List<String> allowedOrigins,
                              boolean allowRemote, Path root) {

    /** The wire a launched server speaks JSON-RPC over. */
    public enum Transport {
        /** Standard input and output, the default and what every LSP client consumes. */
        STDIO,
        /** A WebSocket endpoint at {@code ws://<host>:<port>/lsp}, one server per connection. */
        WEBSOCKET
    }

    /** The interface a WebSocket server binds when {@code --host} is not given: loopback only. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** The port a WebSocket server binds without {@code --port}, and where Studio connects. */
    public static final int DEFAULT_PORT = 5007;

    /** The Studio dev server ({@code ng serve}, port 4200) under both loopback spellings. */
    public static final List<String> DEFAULT_ALLOWED_ORIGINS =
            List.of("http://localhost:4200", "http://127.0.0.1:4200");

    /** Holds {@code allowedOrigins} as an unmodifiable copy, so parsed options never change. */
    public LauncherOptions {
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    /**
     * Parses the launcher's command line.
     *
     * @param args the arguments {@link LspMain#main} received
     * @return the options they name, with defaults for every option not given
     * @throws IllegalArgumentException when an option is unknown, lacks its value, has an invalid
     *         value, belongs to the other transport, or binds beyond loopback without
     *         {@code --allow-remote}
     */
    public static LauncherOptions parse(String[] args) {
        Transport transport = Transport.STDIO;
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        List<String> origins = new ArrayList<>();
        boolean allowRemote = false;
        Path root = null;
        boolean explicitTransport = false;
        boolean websocketOptionSeen = false;

        Deque<String> remaining = new ArrayDeque<>(Arrays.asList(args));
        while (!remaining.isEmpty()) {
            String option = remaining.removeFirst();
            switch (option) {
                case "--stdio" -> {
                    transport = Transport.STDIO;
                    explicitTransport = true;
                }
                case "--websocket" -> {
                    transport = Transport.WEBSOCKET;
                    explicitTransport = true;
                }
                case "--host" -> {
                    host = requireValue(remaining, "--host");
                    websocketOptionSeen = true;
                }
                case "--port" -> {
                    port = parsePort(requireValue(remaining, "--port"));
                    websocketOptionSeen = true;
                }
                case "--allowed-origin" -> {
                    origins.add(parseOrigin(requireValue(remaining, "--allowed-origin")));
                    websocketOptionSeen = true;
                }
                case "--allow-remote" -> {
                    allowRemote = true;
                    websocketOptionSeen = true;
                }
                case "--root" -> root = parseRoot(requireValue(remaining, "--root"));
                default -> throw new IllegalArgumentException("unknown option: " + option);
            }
        }

        if (websocketOptionSeen) {
            if (explicitTransport && transport == Transport.STDIO) {
                throw new IllegalArgumentException(
                        "--host, --port, --allowed-origin and --allow-remote apply to --websocket only");
            }
            // A WebSocket-only option names the transport it belongs to, as --port always has.
            transport = Transport.WEBSOCKET;
        }

        LauncherOptions options = new LauncherOptions(transport, host, port,
                origins.isEmpty() ? DEFAULT_ALLOWED_ORIGINS : origins, allowRemote, root);
        if (options.transport() == Transport.WEBSOCKET && options.bindsBeyondLoopback() && !allowRemote) {
            throw new IllegalArgumentException("--host " + host + " is not a loopback address; the "
                    + "WebSocket endpoint is unauthenticated and exposes exeris/applyMutation, so "
                    + "binding beyond loopback requires --allow-remote");
        }
        return options;
    }

    private static String requireValue(Deque<String> remaining, String option) {
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return remaining.removeFirst();
    }

    private static int parsePort(String raw) {
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("--port is not a number: " + raw);
        }
        if (parsed < 1 || parsed > 65535) {
            throw new IllegalArgumentException("--port out of range (1-65535): " + parsed);
        }
        return parsed;
    }

    private static Path parseRoot(String raw) {
        Path real;
        try {
            real = Path.of(raw).toRealPath();
        } catch (IOException | InvalidPathException _) {
            throw new IllegalArgumentException("--root does not exist: " + raw);
        }
        if (!Files.isDirectory(real)) {
            throw new IllegalArgumentException("--root is not a directory: " + raw);
        }
        return real;
    }

    /**
     * Accepts an origin in the exact serialization a browser sends ({@code scheme://host[:port]},
     * RFC 6454): the kernel compares it byte for byte, so a trailing slash, a path or an upper-case
     * scheme would be a list entry that silently matches nothing.
     */
    private static String parseOrigin(String raw) {
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException _) {
            throw new IllegalArgumentException("--allowed-origin is not a URI: " + raw);
        }
        String scheme = uri.getScheme();
        boolean webScheme = "http".equals(scheme) || "https".equals(scheme);
        boolean bare = uri.getRawPath() != null && uri.getRawPath().isEmpty()
                && uri.getRawQuery() == null && uri.getRawFragment() == null
                && uri.getRawUserInfo() == null;
        if (!webScheme || uri.getHost() == null || !bare
                || !raw.equals(raw.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("--allowed-origin must be a lower-case browser origin "
                    + "of the form http[s]://host[:port] with no path or trailing slash: " + raw);
        }
        return raw;
    }

    /**
     * Whether {@code host} names an interface other than loopback. A host that does not resolve
     * counts as beyond loopback, so an unresolvable name is never bound without consent.
     *
     * @return {@code true} when binding {@code host} would expose the endpoint beyond this machine
     */
    public boolean bindsBeyondLoopback() {
        try {
            return !InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return true;
        }
    }
}
