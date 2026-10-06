package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.exeris.platform.lsp.LauncherOptions.Transport;
import org.junit.jupiter.api.Test;

/** Covers every branch of the launcher's command line options. */
class LauncherOptionsTest {

    @Test
    void noArgumentsServeStdio() {
        assertThat(LauncherOptions.parse(new String[] {}).transport()).isEqualTo(Transport.STDIO);
    }

    @Test
    void websocketDefaultsToLoopback() {
        LauncherOptions options = LauncherOptions.parse(new String[] {"--websocket"});
        assertThat(options.transport()).isEqualTo(Transport.WEBSOCKET);
        assertThat(options.host()).isEqualTo("127.0.0.1");
        assertThat(options.port()).isEqualTo(5007);
        assertThat(options.bindsBeyondLoopback()).isFalse();
    }

    @Test
    void portFlagAloneEnablesWebSocketTransport() {
        LauncherOptions options = LauncherOptions.parse(new String[] {"--port", "7443"});
        assertThat(options.transport()).isEqualTo(Transport.WEBSOCKET);
        assertThat(options.port()).isEqualTo(7443);
        assertThat(options.host()).isEqualTo("127.0.0.1");
    }

    @Test
    void hostAndPortAreHonoured() {
        LauncherOptions options = LauncherOptions.parse(
                new String[] {"--websocket", "--host", "0.0.0.0", "--port", "9000", "--allow-remote"});
        assertThat(options.host()).isEqualTo("0.0.0.0");
        assertThat(options.port()).isEqualTo(9000);
        assertThat(options.bindsBeyondLoopback()).isTrue();
        assertThat(options.allowRemote()).isTrue();
    }

    @Test
    void aNonLoopbackBindWithoutAllowRemoteIsRefused() {
        for (String host : new String[] {"0.0.0.0", "192.0.2.10", "::"}) {
            assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--host", host}))
                    .as("host %s", host)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("--allow-remote")
                    .hasMessageContaining("exeris/applyMutation");
        }
    }

    @Test
    void allowRemoteIsNotNeededForLoopback() {
        LauncherOptions options = LauncherOptions.parse(new String[] {"--websocket", "--host", "localhost"});
        assertThat(options.allowRemote()).isFalse();
    }

    @Test
    void theDefaultOriginAllowlistIsTheStudioDevServerOnly() {
        assertThat(LauncherOptions.parse(new String[] {"--websocket"}).allowedOrigins())
                .containsExactly("http://localhost:4200", "http://127.0.0.1:4200");
    }

    @Test
    void allowedOriginIsRepeatableAndReplacesTheDefault() {
        LauncherOptions options = LauncherOptions.parse(new String[] {
                "--websocket",
                "--allowed-origin", "https://studio.example.test",
                "--allowed-origin", "http://localhost:8080"});
        assertThat(options.allowedOrigins())
                .containsExactly("https://studio.example.test", "http://localhost:8080");
    }

    @Test
    void anOriginTheKernelCouldNeverMatchIsRejected() {
        for (String origin : new String[] {
                "http://localhost:4200/", "http://localhost:4200/studio", "localhost:4200",
                "ws://localhost:4200", "HTTP://localhost:4200", "http://LOCALHOST:4200",
                "http://localhost:4200?x=1", "*"}) {
            assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--allowed-origin", origin}))
                    .as("origin %s", origin)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("--allowed-origin");
        }
    }

    @Test
    void aWebSocketOnlyOptionSelectsTheWebSocketTransport() {
        for (String[] args : new String[][] {
                {"--host", "127.0.0.1"},
                {"--allowed-origin", "http://localhost:4200"}}) {
            assertThat(LauncherOptions.parse(args).transport()).isEqualTo(Transport.WEBSOCKET);
        }
    }

    @Test
    void loopbackSpellingsAreAllRecognisedAsLoopback() {
        for (String host : new String[] {"127.0.0.1", "localhost", "::1"}) {
            assertThat(LauncherOptions.parse(new String[] {"--websocket", "--host", host})
                    .bindsBeyondLoopback())
                    .as("%s is loopback", host)
                    .isFalse();
        }
    }

    @Test
    void transportOptionsOnExplicitStdioAreRejected() {
        for (String[] args : new String[][] {
                {"--stdio", "--host", "0.0.0.0"},
                // Spelling a default value is still a WebSocket option on a stdio launch.
                {"--stdio", "--port", "5007"},
                {"--stdio", "--allowed-origin", "http://localhost:4200"},
                {"--stdio", "--allow-remote"}}) {
            assertThatThrownBy(() -> LauncherOptions.parse(args))
                    .as(String.join(" ", args))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("--websocket only");
        }
    }

    @Test
    void malformedInputIsRejectedWithAnActionableMessage() {
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--unknown-flag"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown option");
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--port"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a value");
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--port", "not-a-number"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number");
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--allowed-origin"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a value");
    }

    @Test
    void portZeroAndOutOfRangePortsAreRejected() {
        for (String port : new String[] {"0", "-1", "65536"}) {
            assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--port", port}))
                    .as("port %s", port)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("out of range");
        }
    }
}
