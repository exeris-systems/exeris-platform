package eu.exeris.platform.lsp;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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
 * @param transport      which wire to serve (STDIO or WEBSOCKET)
 * @param host           interface to bind, WebSocket only
 * @param port           port to bind, WebSocket only
 * @param allowedOrigins browser origins admitted by the WebSocket handshake, WebSocket only
 * @param allowRemote    whether a non-loopback {@code host} was explicitly opted into
 */
public record LauncherOptions(Transport transport, String host, int port, List<String> allowedOrigins,
                              boolean allowRemote) {

    public enum Transport { STDIO, WEBSOCKET }

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 5007;

    /** The Studio dev server ({@code ng serve}, port 4200) under both loopback spellings. */
    public static final List<String> DEFAULT_ALLOWED_ORIGINS =
            List.of("http://localhost:4200", "http://127.0.0.1:4200");

    public LauncherOptions {
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    public static LauncherOptions parse(String[] args) {
        Transport transport = Transport.STDIO;
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        List<String> origins = new ArrayList<>();
        boolean allowRemote = false;
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
                origins.isEmpty() ? DEFAULT_ALLOWED_ORIGINS : origins, allowRemote);
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

    public boolean bindsBeyondLoopback() {
        try {
            return !InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return true;
        }
    }
}
