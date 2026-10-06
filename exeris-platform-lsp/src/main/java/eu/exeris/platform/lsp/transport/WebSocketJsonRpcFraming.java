package eu.exeris.platform.lsp.transport;

import eu.exeris.kernel.spi.websocket.WebSocketExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Stream adapter converting between raw WebSocket text frames (raw JSON) and LSP-framed
 * JSON-RPC streams with {@code Content-Length: <n>\r\n\r\n} headers.
 *
 * <p>Over WebSocket the message boundary is the WebSocket message itself: each text message carries
 * exactly one raw JSON-RPC message, with no base-protocol header. LSP4J's reader and writer speak
 * the stdio base protocol ({@code Content-Length} framing), so inbound messages are framed before
 * LSP4J reads them and outbound frames are unframed before they are sent. The JSON-RPC surface is
 * untouched in both directions; only the framing differs between the two transports.
 */
public final class WebSocketJsonRpcFraming {

    private WebSocketJsonRpcFraming() {
    }

    /**
     * Frames one WebSocket text message as one LSP base-protocol message
     * ({@code Content-Length: <n>\r\n\r\n<body>}).
     *
     * <p>Framing is unconditional: one WebSocket text message is exactly one JSON-RPC message body,
     * never a pre-framed byte stream. Passing a peer's own {@code Content-Length} header through
     * would let a message declare a length other than its own and desynchronise every message
     * boundary after it on the session.
     */
    public static byte[] frame(String json) {
        Objects.requireNonNull(json, "json");
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] framed = new byte[header.length + body.length];
        System.arraycopy(header, 0, framed, 0, header.length);
        System.arraycopy(body, 0, framed, header.length, body.length);
        return framed;
    }

    public static MessageInputStream createInputStream() {
        return new MessageInputStream();
    }

    public static FramingOutputStream createOutputStream(Consumer<String> messageSender) {
        return new FramingOutputStream(messageSender);
    }

    public static FramingOutputStream createOutputStream(WebSocketExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return new FramingOutputStream(exchange::send);
    }

    /**
     * Bounded handoff {@link InputStream} feeding LSP4J from WebSocket text messages.
     *
     * <p>The handoff holds at most {@link #CAPACITY} messages. A feeder that finds it full parks
     * until LSP4J's reader takes one, so a peer that sends faster than the server consumes stalls
     * its own receive loop (ADR-043 backpressure) instead of growing an on-heap queue without bound.
     * {@link #close()} releases a parked feeder, and anything fed after close is dropped.
     */
    public static final class MessageInputStream extends InputStream {
        /** Messages in flight between the receive loop and LSP4J's reader. */
        static final int CAPACITY = 16;

        private static final long PARK_SLICE_MILLIS = 50;

        private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(CAPACITY);
        private byte[] current;
        private int index;
        private volatile boolean closed;

        /**
         * Hands one framed message to the reader, parking while the handoff is full.
         *
         * @return {@code true} if the message was accepted, {@code false} if the stream is closed
         * @throws InterruptedIOException if the feeding thread is interrupted while parked
         */
        public boolean feed(byte[] data) throws InterruptedIOException {
            Objects.requireNonNull(data, "data");
            try {
                while (!closed) {
                    if (queue.offer(data, PARK_SLICE_MILLIS, TimeUnit.MILLISECONDS)) {
                        return true;
                    }
                }
                return false;
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while handing a WebSocket message to LSP4J");
            }
        }

        /** Frames and hands one WebSocket text message to the reader; see {@link #feed(byte[])}. */
        public boolean feedMessage(String message) throws InterruptedIOException {
            return message == null || feed(frame(message));
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int n = read(single, 0, 1);
            return n == -1 ? -1 : (single[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.requireNonNull(b, "buffer");
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }

            while (current == null || index >= current.length) {
                if (closed && queue.isEmpty()) {
                    return -1;
                }
                try {
                    // Parks in slices, as the feeder does, so close() needs no sentinel: a closed
                    // stream ends once the reader has drained what was accepted before close.
                    byte[] next = queue.poll(PARK_SLICE_MILLIS, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        current = next;
                        index = 0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for WebSocket input", e);
                }
            }

            int available = current.length - index;
            int toRead = Math.min(len, available);
            System.arraycopy(current, index, b, off, toRead);
            index += toRead;
            return toRead;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * {@link OutputStream} that parses the LSP base-protocol frames LSP4J writes and dispatches each
     * message body, unframed, to a consumer (typically {@code WebSocketExchange::send}).
     *
     * <p>Bytes are appended to one growable buffer and scanned from where the previous write left
     * off, so a message costs time linear in its size however LSP4J chunks it. The header is parsed
     * strictly per the base protocol: header lines end in {@code \r\n}, the header ends at the first
     * empty line, and {@code Content-Length} is required and non-negative.
     */
    public static final class FramingOutputStream extends OutputStream {
        /** Far above any header LSP4J writes; bounds the buffer while no header end is in sight. */
        static final int MAX_HEADER_BYTES = 8 * 1024;

        private static final String CONTENT_LENGTH = "content-length:";

        private final Consumer<String> messageSender;
        private byte[] buf = new byte[8 * 1024];
        private int size;
        /** Index from which the header-terminator search resumes; never re-scans a seen prefix. */
        private int scanFrom;
        private int headerEnd = -1;
        private int contentLength = -1;

        public FramingOutputStream(Consumer<String> messageSender) {
            this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        }

        @Override
        public synchronized void write(int b) throws IOException {
            ensureCapacity(size + 1);
            buf[size++] = (byte) b;
            drain();
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            ensureCapacity(size + len);
            System.arraycopy(b, off, buf, size, len);
            size += len;
            drain();
        }

        private void ensureCapacity(int needed) {
            if (needed > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(needed, buf.length * 2));
            }
        }

        /** Dispatches every complete message in the buffer, then compacts the remainder. */
        private void drain() throws IOException {
            int start = 0;
            while (completeMessageAt(start)) {
                String json = new String(buf, headerEnd, contentLength, StandardCharsets.UTF_8);
                start = headerEnd + contentLength;
                scanFrom = start;
                headerEnd = -1;
                contentLength = -1;
                send(json);
            }
            if (start > 0) {
                int remaining = size - start;
                System.arraycopy(buf, start, buf, 0, remaining);
                size = remaining;
                scanFrom -= start;
                if (headerEnd != -1) {
                    headerEnd -= start;
                }
            }
        }

        /**
         * Whether a whole message (header and body) starts at {@code start}; parses its header the
         * first time it is complete.
         */
        private boolean completeMessageAt(int start) throws IOException {
            if (headerEnd == -1) {
                int end = findHeaderEnd(start);
                if (end == -1) {
                    if (size - start > MAX_HEADER_BYTES) {
                        throw new IOException("LSP frame header exceeds " + MAX_HEADER_BYTES
                                + " bytes without a terminating empty line");
                    }
                    return false;
                }
                headerEnd = end;
                contentLength = parseContentLength(
                        new String(buf, start, end - start, StandardCharsets.US_ASCII));
            }
            return size - headerEnd >= contentLength;
        }

        private void send(String json) throws IOException {
            try {
                messageSender.accept(json);
            } catch (RuntimeException e) {
                // OutputStream's contract is IOException; LSP4J maps that onto a closed-stream
                // JsonRpcException rather than letting a transport failure escape as unchecked.
                throw new IOException("WebSocket send failed", e);
            }
        }

        /** Returns the index just past the first {@code \r\n\r\n} at or after {@code start}. */
        private int findHeaderEnd(int start) {
            int i = Math.max(start, scanFrom);
            for (; i + 3 < size; i++) {
                if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                    scanFrom = i;
                    return i + 4;
                }
            }
            // The last three bytes may begin a terminator that the next write completes.
            scanFrom = Math.max(start, size - 3);
            return -1;
        }

        private static int parseContentLength(String headerText) throws IOException {
            int length = -1;
            for (String line : headerText.split("\r\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith(CONTENT_LENGTH)) {
                    String value = line.substring(CONTENT_LENGTH.length()).trim();
                    try {
                        length = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid Content-Length: " + value, e);
                    }
                    if (length < 0) {
                        throw new IOException("Negative Content-Length: " + value);
                    }
                }
            }
            if (length == -1) {
                throw new IOException("Missing Content-Length header in LSP frame: " + headerText);
            }
            return length;
        }
    }
}
