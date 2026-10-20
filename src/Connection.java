import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

// One browser connection, from the HTTP upgrade until the socket closes.
// Given code: you never need to edit this file. Net.listen() starts one of these per connection,
// on its own thread, and it calls your GameServer through Net.Handler: onOpen, onMessage, onClose.
final class Connection implements Runnable {

    private static final int OP_TEXT = 1;
    private static final int OP_CLOSE = 8;
    private static final int OP_PING = 9;
    private static final int PONG_OPCODE = 0xA;
    private static final int FIRST_BUFFER = 8192;
    private static final int FRAME_HEADER_ROOM = 16;
    private static final String HANDLER_ERROR_CMD = "__handlerError";

    // Thrown when the bytes cannot be read as frames any more: the connection is closed.
    private static final class Broken extends RuntimeException {}

    private final Socket socket;
    private final String id;
    private final Net.Handler handler;
    private InputStream in;
    private OutputStream out;
    private Net.Client client;
    private byte[] buf = new byte[FIRST_BUFFER];
    private int have;

    Connection(Socket socket, String id, Net.Handler handler) {
        this.socket = socket; this.id = id; this.handler = handler;
    }

    // The whole life of the connection: upgrade, read frames until it ends, then clean up.
    @Override
    public void run() {
        try {
            if (open()) readLoop();
        } catch (Exception ignored) {
        } finally {
            close();
        }
    }

    // Do the WebSocket handshake, then tell the GameServer a client has arrived.
    private boolean open() throws Exception {
        socket.setTcpNoDelay(true);
        in = new BufferedInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        if (!Handshake.perform(in, out)) return false;
        client = new Net.Client(socket, out, id);
        handler.onOpen(client);
        return true;
    }

    // Handle every complete frame in the buffer, then read more bytes from the network.
    private void readLoop() throws IOException {
        while (handleBufferedFrames() && makeRoom() && readMore()) { }
    }

    // False when the browser asked to close.
    private boolean handleBufferedFrames() throws IOException {
        Frame.Msg m;
        while ((m = nextFrame()) != null) {
            if (!handle(m)) return false;
        }
        return true;
    }

    // Cut one frame off the front of the buffer. Null = the frame has not fully arrived yet.
    private Frame.Msg nextFrame() {
        Frame.Msg m = decodeOrExplain();
        if (m == null) return null;
        if (m.consumed <= 0 || m.consumed > have) {
            System.out.println("  !! Frame.decode returned consumed = " + m.consumed
                             + " (buffer holds " + have + " bytes) -- check how you count bytes");
            throw new Broken();
        }
        System.arraycopy(buf, m.consumed, buf, 0, have - m.consumed);
        have -= m.consumed;
        return m;
    }

    // Frame.decode with its crash printed, so a broken decoder is easy to spot in the console.
    private Frame.Msg decodeOrExplain() {
        try {
            return Frame.decode(buf, have);
        } catch (Exception badFrame) {
            System.out.println("  !! Frame.decode threw: " + badFrame);
            badFrame.printStackTrace(System.out);
            throw new Broken();
        }
    }

    // What each kind of frame means: close, ping (answer pong), pong (ignore), text (your packet).
    private boolean handle(Frame.Msg m) throws IOException {
        if (m.opcode == OP_CLOSE) return false;
        if (m.opcode == OP_PING) {
            synchronized (out) { out.write(Frame.encodeControl(PONG_OPCODE, m.payload)); out.flush(); }
        } else if (m.opcode == OP_TEXT && m.text != null) {
            deliver(m.text);
        }
        return true;
    }

    // Hand a text packet to the GameServer. A crash there is printed, the connection stays up.
    private void deliver(String text) {
        try {
            handler.onMessage(client, text);
        } catch (Throwable appError) {
            System.out.println();
            System.out.println("  !! your onMessage threw: " + appError);
            appError.printStackTrace(System.out);
            System.out.println();
            JsonObject crash = new JsonObject();
            crash.addProperty("cmd", HANDLER_ERROR_CMD);
            crash.addProperty("error", appError.getClass().getSimpleName());
            client.send(crash.toString());
        }
    }

    // A full buffer doubles, up to the largest frame Frame accepts. False = the frame is too big.
    private boolean makeRoom() {
        if (have < buf.length) return true;
        if (buf.length >= Frame.MAX_PAYLOAD + FRAME_HEADER_ROOM) return false;
        buf = Arrays.copyOf(buf, buf.length * 2);
        return true;
    }

    // Wait for the next bytes from the network. False = the browser went away.
    private boolean readMore() throws IOException {
        int n = in.read(buf, have, buf.length - have);
        if (n < 0) return false;
        have += n;
        return true;
    }

    // Tell the GameServer the client left, then close the socket.
    private void close() {
        if (client != null) {
            client.shutdown();
            try {
                handler.onClose(client);
            } catch (Throwable appError) {
                System.out.println("  !! your onClose threw: " + appError);
                appError.printStackTrace(System.out);
            }
        }
        try { socket.close(); } catch (IOException ignored) { }
    }
}