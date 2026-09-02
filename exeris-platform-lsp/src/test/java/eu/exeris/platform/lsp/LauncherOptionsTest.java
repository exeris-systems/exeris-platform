package eu.exeris.platform.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.exeris.platform.lsp.LauncherOptions.Transport;
import org.junit.jupiter.api.Test;

/** Covers every branch of the launcher's command line without binding a port or taking stdio. */
class LauncherOptionsTest {

    @Test
    void noArgumentsServeStdio() {
        // The contract exeris-ai-bridge depends on: `java -jar <launcher>` with nothing after it
        // is the stdio server. Any change here breaks a consumer silently.
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
    void hostAndPortAreHonoured() {
        LauncherOptions options =
                LauncherOptions.parse(new String[] {"--websocket", "--host", "0.0.0.0", "--port", "9000"});
        assertThat(options.host()).isEqualTo("0.0.0.0");
        assertThat(options.port()).isEqualTo(9000);
        // Permitted, but the launcher has to be able to say so — this socket is an unauthenticated
        // write path into the workspace.
        assertThat(options.bindsBeyondLoopback()).isTrue();
    }

    @Test
    void loopbackSpellingsAreAllRecognisedAsLoopback() {
        // Every one of these is loopback, and a string-matching check got three of them wrong.
        // A warning that fires on a safe bind is a warning people stop reading.
        for (String host : new String[] {
                "127.0.0.1", "localhost", "::1", "[::1]", "0:0:0:0:0:0:0:1", "127.0.0.2"}) {
            assertThat(LauncherOptions.parse(new String[] {"--websocket", "--host", host})
                    .bindsBeyondLoopback())
                    .as("%s is loopback", host)
                    .isFalse();
        }
    }

    @Test
    void routableAndUnresolvableHostsBothCountAsExposed() {
        for (String host : new String[] {"0.0.0.0", "192.0.2.1"}) {
            assertThat(LauncherOptions.parse(new String[] {"--websocket", "--host", host})
                    .bindsBeyondLoopback())
                    .as("%s is not loopback", host)
                    .isTrue();
        }
        // Cannot be resolved, so cannot be shown to be safe. Warning is the safe direction: the
        // bind will fail anyway, and silence about an unknown host is worse than a stray warning.
        assertThat(LauncherOptions.parse(
                        new String[] {"--websocket", "--host", "no.such.host.invalid"})
                .bindsBeyondLoopback())
                .isTrue();
    }

    @Test
    void transportOptionsOnStdioAreRejectedRatherThanIgnored() {
        // Silently accepting them would let someone believe they had moved the port when the
        // server is not even listening on one.
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--port", "9000"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--websocket only");
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--stdio", "--host", "0.0.0.0"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--websocket only");
    }

    @Test
    void malformedInputIsRejectedWithAnActionableMessage() {
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--wat"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown option");
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--port"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a value");
        assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--port", "http"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    void portZeroAndOutOfRangePortsAreRejected() {
        // 0 would bind an ephemeral port the caller cannot discover: the launcher reports the port
        // it was given, not the one the OS chose.
        for (String port : new String[] {"0", "-1", "65536"}) {
            assertThatThrownBy(() -> LauncherOptions.parse(new String[] {"--websocket", "--port", port}))
                    .as("port %s", port)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("out of range");
        }
    }
}
