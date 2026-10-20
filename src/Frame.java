import java.nio.charset.StandardCharsets;

// A WebSocket frame is a few header bytes followed by the payload. This file turns bytes into
// messages (decode) and text into bytes (encodeText). Given code: Connection.java and Net.Client use it.
public class Frame {

    // One decoded frame: its kind (opcode), the payload, and how many bytes of the buffer it used.

    public static final class Msg {
        public final int opcode;
        public final String text;
        public final byte[] payload;
        public final int consumed;
        public Msg(int opcode, String text, byte[] payload, int consumed) {
            this.opcode = opcode; this.text = text; this.payload = payload; this.consumed = consumed;
        }
        @Override public String toString() {
            return "Msg(op=" + opcode + ", len=" + payload.length + ", consumed=" + consumed + ")";
        }
    }

    public static final int MAX_PAYLOAD = 1 << 20;

    private static final int OP_TEXT = 1;
    private static final int HEAD = 2;
    private static final int OPCODE_BITS = 0x0F;
    private static final int MASK_BIT = 0x80;
    private static final int LENGTH_BITS = 0x7F;
    private static final int LENGTH_16 = 126;
    private static final int LENGTH_64 = 127;
    private static final int KEY_BYTES = 4;
    private static final int BYTE = 0xFF;

    // Read one frame from the start of buf. Null when the bytes for a whole frame have not arrived yet.
    public static Msg decode(byte[] buf, int len) {
        if (len < HEAD) return null;

        // Byte 0 holds the opcode, byte 1 the mask bit and a 7-bit length.
        int opcode = buf[0] & OPCODE_BITS;
        boolean masked = (buf[1] & MASK_BIT) != 0;
        int extra = extraLengthBytes(buf[1] & LENGTH_BITS);
        int p = HEAD + extra;
        if (len < p) return null;
        long payloadLen = payloadLength(buf, extra);
        if (payloadLen < 0 || payloadLen > MAX_PAYLOAD)
            throw new IllegalArgumentException("frame too big: " + payloadLen);

        // Frames from a browser are masked: 4 key bytes come before the payload.
        int keyAt = masked ? p : -1;
        if (masked) p += KEY_BYTES;
        int n = (int) payloadLen;
        if (len < p + n) return null;

        byte[] data = unmask(buf, p, n, keyAt);
        String text = (opcode == OP_TEXT) ? new String(data, StandardCharsets.UTF_8) : null;
        return new Msg(opcode, text, data, p + n);
    }

    // 126 and 127 mean the real length follows in the next 2 or 8 bytes; anything smaller is the length.
    private static int extraLengthBytes(int shortLength) {
        if (shortLength == LENGTH_16) return Short.BYTES;
        if (shortLength == LENGTH_64) return Long.BYTES;
        return 0;
    }

    // The payload length: the 7 bits in byte 1, or the 2 or 8 bytes after it, big-endian.
    private static long payloadLength(byte[] buf, int extra) {
        if (extra == 0) return buf[1] & LENGTH_BITS;
        long value = 0;
        for (int i = 0; i < extra; i++) value = (value << Byte.SIZE) | (buf[HEAD + i] & BYTE);
        return value;
    }

    // Copy the payload out and XOR it with the 4 key bytes. keyAt < 0 = not masked.
    private static byte[] unmask(byte[] buf, int from, int n, int keyAt) {
        byte[] data = new byte[n];
        System.arraycopy(buf, from, data, 0, n);
        if (keyAt < 0) return data;
        for (int i = 0; i < n; i++) data[i] ^= buf[keyAt + (i % KEY_BYTES)];
        return data;
    }
    // Wrap text in a frame for the browser: 0x81 (final text frame), the length, then the bytes. No mask.
    public static byte[] encodeText(String text) {
        byte[] p = text.getBytes(StandardCharsets.UTF_8);
        int n = p.length;
        int head = n < 126 ? 2 : n < 65536 ? 4 : 10;
        byte[] out = new byte[head + n];
        out[0] = (byte) 0x81;
        if (n < 126) {
            out[1] = (byte) n;
        } else if (n < 65536) {
            out[1] = 126;
            out[2] = (byte) (n >>> 8);
            out[3] = (byte) n;
        } else {
            out[1] = 127;
            for (int i = 0; i < 8; i++) out[2 + i] = (byte) ((long) n >>> (8 * (7 - i)));
        }
        System.arraycopy(p, 0, out, head, n);
        return out;
    }

    // A small control frame such as pong. Payload under 126 bytes.
    public static byte[] encodeControl(int opcode, byte[] payload) {
        byte[] p = payload == null ? new byte[0] : payload;
        byte[] out = new byte[2 + p.length];
        out[0] = (byte) (0x80 | (opcode & 0x0F));
        out[1] = (byte) p.length;
        System.arraycopy(p, 0, out, 2, p.length);
        return out;
    }
}
