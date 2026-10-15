import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// The HTTP "Upgrade" step that turns a plain TCP connection into a WebSocket.
// Given code: you never need to edit this file. Connection.java calls perform() once per connection.
final class Handshake {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String KEY_HEADER = "Sec-WebSocket-Key";
    private static final String END_OF_HEADERS = "\r\n\r\n";
    private static final String LINE_BREAK = "\r\n";
    private static final int MAX_HEADER_CHARS = 16384;
    private static final String HASH = "SHA-1";
    private static final String RESPONSE_TEMPLATE = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: %s\r\n\r\n";

    private Handshake() {}

    // Read the browser's request, answer "101 Switching Protocols". False = not a WebSocket request.
    static boolean perform(InputStream in, OutputStream out) throws IOException, NoSuchAlgorithmException {
        String key = findKey(readHeaders(in));
        if (key == null) return false;
        out.write(response(key).getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }

    // Read bytes until the blank line that ends the HTTP headers. Null if the headers are absurdly long.
    private static String readHeaders(InputStream in) throws IOException {
        StringBuilder headers = new StringBuilder();
        int next;
        while ((next = in.read()) >= 0) {
            headers.append((char) next);
            if (endsWith(headers, END_OF_HEADERS)) break;
            if (headers.length() > MAX_HEADER_CHARS) return null;
        }
        return headers.toString();
    }

    private static boolean endsWith(StringBuilder text, String suffix) {
        int start = text.length() - suffix.length();
        return start >= 0 && text.indexOf(suffix, start) == start;
    }

    // Pick the Sec-WebSocket-Key line out of the headers. Null if the browser did not send one.
    private static String findKey(String headers) {
        if (headers == null) return null;
        for (String line : headers.split(LINE_BREAK)) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(KEY_HEADER))
                return line.substring(colon + 1).trim();
        }
        return null;
    }

    // The server proves it speaks WebSocket: hash the browser's key together with a fixed magic string.
    private static String response(String key) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance(HASH).digest((key + MAGIC).getBytes(StandardCharsets.UTF_8));
        return String.format(RESPONSE_TEMPLATE, Base64.getEncoder().encodeToString(digest));
    }
}