import java.nio.charset.StandardCharsets;

public class Frame {

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

    public static Msg decode(byte[] buf, int len) {
        if (len < 2) return null;

        int b0 = buf[0] & 0xFF;
        int b1 = buf[1] & 0xFF;
        int opcode  = b0 & 0x0F;
        boolean mask = (b1 & 0x80) != 0;
        long payloadLen = b1 & 0x7F;

        int p = 2;

        if (payloadLen == 126) {
            if (len < p + 2) return null;
            payloadLen = ((long)(buf[p] & 0xFF) << 8) | (buf[p+1] & 0xFF);
            p += 2;
        } else if (payloadLen == 127) {
            if (len < p + 8) return null;
            payloadLen = 0;
            for (int i = 0; i < 8; i++) payloadLen = (payloadLen << 8) | (buf[p+i] & 0xFF);
            p += 8;
        }

        if (payloadLen < 0 || payloadLen > MAX_PAYLOAD)
            throw new IllegalArgumentException("khung qua lon / frame too big: " + payloadLen);

        byte[] key = null;
        if (mask) {
            if (len < p + 4) return null;
            key = new byte[]{ buf[p], buf[p+1], buf[p+2], buf[p+3] };
            p += 4;
        }

        int n = (int) payloadLen;
        if (len < p + n) return null;

        byte[] data = new byte[n];
        System.arraycopy(buf, p, data, 0, n);
        if (key != null) for (int i = 0; i < n; i++) data[i] ^= key[i & 3];

        String text = (opcode == 1) ? new String(data, StandardCharsets.UTF_8) : null;
        return new Msg(opcode, text, data, p + n);
    }

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

    public static byte[] encodeControl(int opcode, byte[] payload) {
        byte[] p = payload == null ? new byte[0] : payload;
        byte[] out = new byte[2 + p.length];
        out[0] = (byte) (0x80 | (opcode & 0x0F));
        out[1] = (byte) p.length;
        System.arraycopy(p, 0, out, 2, p.length);
        return out;
    }
}
