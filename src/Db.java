import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public abstract class Db implements AutoCloseable {

    public abstract void set(String key, String value) throws IOException;
    public abstract String get(String key) throws IOException;
    public abstract String kind();
    @Override public void close() throws IOException {}

    static final String REDIS_HOST = "localhost";
    static final int    REDIS_PORT = 6379;
    static final String FILE_NAME  = "players.txt";

    public static Db open() {
        try {
            Db r = new RedisDb(REDIS_HOST, REDIS_PORT);
            System.out.println("[db] Redis " + REDIS_HOST + ":" + REDIS_PORT);
            return r;
        } catch (IOException e) {
            System.out.println("[db] no Redis (" + e.getMessage() + ") -> falling back to file " + FILE_NAME);
            return new FileDb(new File(FILE_NAME));
        }
    }

    static final class RedisDb extends Db {
        private final Socket socket;
        private final OutputStream out;
        private final BufferedInputStream in;

        RedisDb(String host, int port) throws IOException {
            socket = new Socket(host, port);
            socket.setSoTimeout(2000);
            out = socket.getOutputStream();
            in = new BufferedInputStream(socket.getInputStream());
            Object pong = command("PING");
            if (!"PONG".equals(pong)) throw new IOException("PING -> " + pong);
        }

        @Override public String kind() { return "redis"; }

        @Override public synchronized void set(String key, String value) throws IOException {
            command("SET", key, value);
        }
        @Override public synchronized String get(String key) throws IOException {
            Object r = command("GET", key);
            return r == null ? null : r.toString();
        }
        @Override public void close() throws IOException { socket.close(); }

        Object command(String... args) throws IOException {
            StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
            for (String a : args) {
                byte[] b = a.getBytes(StandardCharsets.UTF_8);
                sb.append('$').append(b.length).append("\r\n").append(a).append("\r\n");
            }
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readReply();
        }

        private Object readReply() throws IOException {
            int type = in.read();
            if (type < 0) throw new IOException("Redis closed the connection");
            String line = readLine();
            switch (type) {
                case '+': return line;
                case ':': return Long.parseLong(line);
                case '-': throw new IOException("Redis: " + line);
                case '$': {
                    int len = Integer.parseInt(line);
                    if (len < 0) return null;
                    byte[] data = in.readNBytes(len);
                    readLine();
                    return new String(data, StandardCharsets.UTF_8);
                }
                case '*': {
                    int n = Integer.parseInt(line);
                    if (n < 0) return null;
                    List<Object> items = new ArrayList<>();
                    for (int i = 0; i < n; i++) items.add(readReply());
                    return items;
                }
                default: throw new IOException("unexpected RESP type: " + (char) type);
            }
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int c; (c = in.read()) >= 0; ) {
                if (c == '\r') { in.read(); break; }
                b.write(c);
            }
            return b.toString(StandardCharsets.UTF_8);
        }
    }

    static final class FileDb extends Db {
        private final File file;
        private final Map<String, String> values = new HashMap<>();

        FileDb(File file) { this.file = file; load(); }

        @Override public String kind() { return "file"; }

        @Override public synchronized void set(String key, String value) throws IOException {
            values.put(key, value);
            save();
        }
        @Override public synchronized String get(String key) {
            return values.get(key);
        }

        private void save() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : values.entrySet())
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            Path tmp = Paths.get(file.getPath() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        private void load() {
            if (!file.exists()) return;
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    String[] p = line.split("\t", 2);
                    if (p.length == 2) values.put(p[0], p[1]);
                }
            } catch (IOException e) { System.out.println("[db] read " + file + ": " + e); }
        }
    }
}
