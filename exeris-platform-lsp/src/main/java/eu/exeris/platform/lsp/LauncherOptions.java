package eu.exeris.platform.lsp;

/**
 * Parsed command line for {@link LspMain}.
 *
 * <p>Split out from the launcher so the parsing — including every rejection — is testable without
 * binding a port or taking over stdio.
 *
 * @param transport which wire to serve
 * @param host      interface to bind, WebSocket only
 * @param port      port to bind, WebSocket only
 */
record LauncherOptions(Transport transport, String host, int port) {

    enum Transport { STDIO, WEBSOCKET }

    /**
     * Loopback, deliberately, and not a placeholder for "0.0.0.0 later".
     *
     * <p>This server exposes {@code exeris/applyMutation}, which writes to {@code .java} files
     * anywhere under the workspace root the client names at {@code initialize}. There is no
     * authentication on the socket — LSP has no such concept — so the bind address IS the access
     * control. Bound to a routable interface, this is remote arbitrary file write on the
     * developer's machine. Overriding it is possible for the container case, and
     * {@link LspMain} says so out loud when someone does.
     */
    static final String DEFAULT_HOST = "127.0.0.1";

    /** Arbitrary but stable; the frontend dev proxy points here. */
    static final int DEFAULT_PORT = 5007;

    static LauncherOptions parse(String[] args) {
        Transport transport = Transport.STDIO;
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--stdio" -> transport = Transport.STDIO;
                case "--websocket" -> transport = Transport.WEBSOCKET;
                case "--host" -> host = requireValue(args, ++i, "--host");
                case "--port" -> port = parsePort(requireValue(args, ++i, "--port"));
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }

        // Accepted but meaningless: silently ignoring them would let someone believe they had
        // moved the port when they had not.
        if (transport == Transport.STDIO && (!DEFAULT_HOST.equals(host) || port != DEFAULT_PORT)) {
            throw new IllegalArgumentException("--host and --port apply to --websocket only");
        }
        return new LauncherOptions(transport, host, port);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }

    private static int parsePort(String raw) {
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("--port is not a number: " + raw);
        }
        // 0 would bind an ephemeral port the caller cannot discover — the launcher prints the
        // port it was given, not the one the OS picked.
        if (parsed < 1 || parsed > 65535) {
            throw new IllegalArgumentException("--port out of range (1-65535): " + parsed);
        }
        return parsed;
    }

    /** True when the socket is reachable from beyond this machine. */
    boolean bindsBeyondLoopback() {
        return !DEFAULT_HOST.equals(host) && !"localhost".equals(host) && !"::1".equals(host);
    }
}
