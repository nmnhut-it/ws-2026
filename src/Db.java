import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public abstract class Db implements AutoCloseable {

    public static final class Row {
        public final String member; public final double score;
        Row(String member, double score) { this.member = member; this.score = score; }
    }

    public abstract void zadd(String key, double score, String member) throws IOException;
    public abstract Double zscore(String key, String member) throws IOException;
    public abstract List<Row> zrevrange(String key, int n) throws IOException;
    public abstract String kind();
    @Override public void close() throws IOException {}

    static final String REDIS_HOST = "localhost";
    static final int    REDIS_PORT = 6379;
    static final String FILE_NAME  = "records.txt";

    public static Db open() {
        try {
            Db r = new RedisDb(REDIS_HOST, REDIS_PORT);
            System.out.println("[db] Redis " + REDIS_HOST + ":" + REDIS_PORT);
            return r;
        } catch (IOException e) {
            System.out.println("[db] khong co Redis (" + e.getMessage() + ") -> dung file " + FILE_NAME);
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

        @Override public synchronized void zadd(String key, double score, String member) throws IOException {
            command("ZADD", key, num(score), member);
        }
        @Override public synchronized Double zscore(String key, String member) throws IOException {
            Object r = command("ZSCORE", key, member);
            return r == null ? null : Double.parseDouble(r.toString());
        }
        @Override public synchronized List<Row> zrevrange(String key, int n) throws IOException {
            Object r = command("ZREVRANGE", key, "0", String.valueOf(n - 1), "WITHSCORES");
            List<Row> rows = new ArrayList<>();
            if (!(r instanceof List)) return rows;
            List<?> flat = (List<?>) r;
            for (int i = 0; i + 1 < flat.size(); i += 2)
                rows.add(new Row(String.valueOf(flat.get(i)), Double.parseDouble(String.valueOf(flat.get(i + 1)))));
            return rows;
        }
        @Override public void close() throws IOException { socket.close(); }

        private static String num(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }

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
            if (type < 0) throw new IOException("Redis dong ket noi");
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
                default: throw new IOException("RESP la: " + (char) type);
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
        private final Map<String, Map<String, Double>> sets = new HashMap<>();

        FileDb(File file) { this.file = file; load(); }

        @Override public String kind() { return "file"; }

        @Override public synchronized void zadd(String key, double score, String member) throws IOException {
            sets.computeIfAbsent(key, k -> new HashMap<>()).put(member, score);
            save();
        }
        @Override public synchronized Double zscore(String key, String member) {
            Map<String, Double> s = sets.get(key);
            return s == null ? null : s.get(member);
        }
        @Override public synchronized List<Row> zrevrange(String key, int n) {
            List<Row> rows = new ArrayList<>();
            for (Map.Entry<String, Double> e : sets.getOrDefault(key, Map.of()).entrySet()) rows.add(new Row(e.getKey(), e.getValue()));
            rows.sort((a, b) -> Double.compare(b.score, a.score));
            return rows.size() > n ? new ArrayList<>(rows.subList(0, n)) : rows;
        }

        private void save() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Map<String, Double>> s : sets.entrySet())
                for (Map.Entry<String, Double> e : s.getValue().entrySet())
                    sb.append(s.getKey()).append('\t').append(e.getValue()).append('\t').append(e.getKey()).append('\n');
            Path tmp = Paths.get(file.getPath() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        private void load() {
            if (!file.exists()) return;
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    String[] p = line.split("\t", 3);
                    if (p.length == 3) sets.computeIfAbsent(p[0], k -> new HashMap<>()).put(p[2], Double.parseDouble(p[1]));
                }
            } catch (IOException e) { System.out.println("[db] doc " + file + ": " + e); }
        }
    }
}
