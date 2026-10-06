package eu.exeris.platform.lsp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSocketJsonRpcFramingTest {

    @Test
    void frameAddsContentLengthHeader() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1}";
        byte[] framed = WebSocketJsonRpcFraming.frame(json);
        String framedStr = new String(framed, StandardCharsets.UTF_8);

        assertThat(framedStr)
                .startsWith("Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n")
                .endsWith(json);
    }

    @Test
    void aMessageThatLooksPreFramedIsFramedLikeAnyOther() {
        // A peer-supplied header must not reach LSP4J: it could declare any length it liked.
        String spoof = "Content-Length: 2\r\n\r\n{}{\"id\":7}";
        String framed = new String(WebSocketJsonRpcFraming.frame(spoof), StandardCharsets.UTF_8);

        assertThat(framed).isEqualTo("Content-Length: " + spoof.length() + "\r\n\r\n" + spoof);
    }

    @Test
    void frameCountsUtf8BytesNotChars() {
        String json = "{\"name\":\"Zażółć\"}";
        String framed = new String(WebSocketJsonRpcFraming.frame(json), StandardCharsets.UTF_8);

        assertThat(framed).startsWith("Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n");
    }

    @Test
    void messageInputStreamFeedsLsp4jBytes() throws IOException {
        try (var in = WebSocketJsonRpcFraming.createInputStream()) {
            in.feedMessage("{\"test\":true}");
            byte[] buf = new byte[256];
            int read = in.read(buf);
            assertThat(read).isPositive();
            String content = new String(buf, 0, read, StandardCharsets.UTF_8);
            assertThat(content).contains("Content-Length: ");
            assertThat(content).contains("{\"test\":true}");
        }
    }

    @Test
    void framingOutputStreamExtractsCompleteJsonAcrossChunks() throws IOException {
        List<String> received = new ArrayList<>();
        try (var out = WebSocketJsonRpcFraming.createOutputStream(received::add)) {
            String json = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}";
            byte[] framed = ("Content-Length: " + json.length() + "\r\n\r\n" + json).getBytes(StandardCharsets.UTF_8);

            // Write in 5-byte chunks
            for (int i = 0; i < framed.length; i += 5) {
                int len = Math.min(5, framed.length - i);
                out.write(framed, i, len);
            }
        }
        assertThat(received).containsExactly("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
    }

    @Test
    void framingOutputStreamHandlesMultipleMessagesInSingleWrite() throws IOException {
        List<String> received = new ArrayList<>();
        try (var out = WebSocketJsonRpcFraming.createOutputStream(received::add)) {
            String json1 = "{\"id\":1}";
            String json2 = "{\"id\":2}";
            String stream = "Content-Length: " + json1.length() + "\r\n\r\n" + json1
                    + "Content-Length: " + json2.length() + "\r\n\r\n" + json2;

            out.write(stream.getBytes(StandardCharsets.UTF_8));
        }
        assertThat(received).containsExactly("{\"id\":1}", "{\"id\":2}");
    }

    @Test
    void framingOutputStreamDecodesMultiByteUtf8SplitAcrossWrites() throws IOException {
        List<String> received = new ArrayList<>();
        String json = "{\"name\":\"Zażółć gęślą jaźń\"}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] framed = concat(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), body);
        try (var out = WebSocketJsonRpcFraming.createOutputStream(received::add)) {
            for (byte b : framed) {
                out.write(b);
            }
        }
        assertThat(received).containsExactly(json);
    }

    @Test
    void framingOutputStreamHandlesALargeMessageWrittenTheWayLsp4jWritesIt() throws IOException {
        // LSP4J writes the header and the body as two separate writes, then flushes.
        String json = "{\"data\":\"" + "x".repeat(4 * 1024 * 1024) + "\"}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        List<String> received = new ArrayList<>();
        try (var out = WebSocketJsonRpcFraming.createOutputStream(received::add)) {
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.write(("Content-Length: 2\r\n\r\n{}").getBytes(StandardCharsets.US_ASCII));
        }
        assertThat(received).hasSize(2);
        assertThat(received.get(0)).isEqualTo(json);
        assertThat(received.get(1)).isEqualTo("{}");
    }

    @Test
    void framingOutputStreamIsLinearInMessageSizeUnderSmallWrites() throws IOException {
        // A buffer re-copied per write is quadratic here: 4 MiB in 64-byte chunks would copy
        // ~128 GiB. The bound is generous; the quadratic version does not come close to it.
        String json = "{\"data\":\"" + "y".repeat(4 * 1024 * 1024) + "\"}";
        byte[] framed = ("Content-Length: " + json.length() + "\r\n\r\n" + json).getBytes(StandardCharsets.US_ASCII);
        List<String> received = new ArrayList<>();
        long started = System.nanoTime();
        try (var out = WebSocketJsonRpcFraming.createOutputStream(received::add)) {
            for (int i = 0; i < framed.length; i += 64) {
                out.write(framed, i, Math.min(64, framed.length - i));
            }
        }
        assertThat(received).containsExactly(json);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(5));
    }

    @Test
    void aBareLfLfDoesNotEndTheHeader() throws IOException {
        // The base protocol terminates header lines with CRLF; a bare LF LF is header content
        // and the frame stays incomplete until a real CRLF CRLF arrives.
        List<String> received = new ArrayList<>();
        try (var out = WebSocketJsonRpcFraming.createOutputStream(received::add)) {
            out.write("Content-Length: 2\n\n{}".getBytes(StandardCharsets.US_ASCII));
            assertThat(received).isEmpty();
        }
    }

    @Test
    void aMissingNegativeOrMalformedContentLengthIsAnIOException() {
        for (String header : List.of("Content-Type: x\r\n\r\n", "Content-Length: -1\r\n\r\n",
                "Content-Length: abc\r\n\r\n")) {
            var out = WebSocketJsonRpcFraming.createOutputStream(json -> { });
            assertThatThrownBy(() -> out.write(header.getBytes(StandardCharsets.US_ASCII)))
                    .as(header)
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void anUnterminatedHeaderIsBoundedRatherThanBufferedForever() {
        var out = WebSocketJsonRpcFraming.createOutputStream(json -> { });
        byte[] junk = "a".repeat(WebSocketJsonRpcFraming.FramingOutputStream.MAX_HEADER_BYTES + 1)
                .getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> out.write(junk)).isInstanceOf(IOException.class);
    }

    @Test
    void aSendFailureSurfacesAsIOException() {
        var out = WebSocketJsonRpcFraming.createOutputStream(json -> {
            throw new IllegalStateException("peer gone");
        });
        assertThatThrownBy(() -> out.write("Content-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IOException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void messagesFedBeforeCloseAreDeliveredThenEndOfStream() throws IOException {
        var in = WebSocketJsonRpcFraming.createInputStream();
        in.feedMessage("{}");
        in.close();
        in.feedMessage("{\"late\":true}");

        String delivered = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(delivered).isEqualTo("Content-Length: 2\r\n\r\n{}");
        assertThat(in.read()).isEqualTo(-1);
    }

    @Test
    void closeUnblocksAReaderWaitingForInput() throws Exception {
        var in = WebSocketJsonRpcFraming.createInputStream();
        var reader = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return in.read();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertThatThrownBy(() -> reader.get(50, java.util.concurrent.TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        in.close();
        assertThat(reader.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(-1);
    }

    @Test
    void aFullHandoffParksTheFeederUntilTheReaderTakesAMessage() throws Exception {
        var in = WebSocketJsonRpcFraming.createInputStream();
        for (int i = 0; i < WebSocketJsonRpcFraming.MessageInputStream.CAPACITY; i++) {
            assertThat(in.feedMessage("{}")).isTrue();
        }
        var feeder = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return in.feedMessage("{\"overflow\":true}");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertThatThrownBy(() -> feeder.get(150, java.util.concurrent.TimeUnit.MILLISECONDS))
                .as("the feeder is parked on the full handoff")
                .isInstanceOf(java.util.concurrent.TimeoutException.class);

        byte[] oneMessage = new byte["Content-Length: 2\r\n\r\n{}".length()];
        assertThat(in.readNBytes(oneMessage, 0, oneMessage.length)).isEqualTo(oneMessage.length);

        assertThat(feeder.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        in.close();
    }

    @Test
    void closeReleasesAFeederParkedOnAFullHandoff() throws Exception {
        var in = WebSocketJsonRpcFraming.createInputStream();
        for (int i = 0; i < WebSocketJsonRpcFraming.MessageInputStream.CAPACITY; i++) {
            in.feedMessage("{}");
        }
        var feeder = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return in.feedMessage("{\"overflow\":true}");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertThatThrownBy(() -> feeder.get(150, java.util.concurrent.TimeUnit.MILLISECONDS))
                .as("the feeder is parked on the full handoff")
                .isInstanceOf(java.util.concurrent.TimeoutException.class);

        in.close();

        assertThat(feeder.get(5, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
        String drained = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(drained).isEqualTo("Content-Length: 2\r\n\r\n{}".repeat(
                WebSocketJsonRpcFraming.MessageInputStream.CAPACITY));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
