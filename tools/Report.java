import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Report {

    static final String DEFAULT_HOST = "ws-2026-server.zingplay.dev";
    static final String GROUP_FILE   = "group.txt";
    static final String REPORT_PATH  = "/report";
    static final String DEPLOY_PATH  = "/deploy";
    static final int    TIMEOUT_MS   = 8000;
    static final String NO_GROUP     =
        "  No group yet -- open group.txt, type your group name (for example nhom-3) and save.";

    static String host() {
        String h = System.getProperty("server");
        if (h == null || h.isBlank()) h = System.getenv("WS_SERVER");
        return (h == null || h.isBlank()) ? DEFAULT_HOST : h.trim();
    }

    static boolean isLocal(String host) {
        return host.startsWith("localhost") || host.startsWith("127.0.0.1");
    }

    static String group() {
        try {
            for (String line : Files.readAllLines(Path.of(GROUP_FILE), StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) return s.toLowerCase();
            }
        } catch (IOException e) {
            return "";
        }
        return "";
    }

    static String post(String path, String json) {
        String host = host();
        String url = (isLocal(host) ? "http://" : "https://") + host + path;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setDoOutput(true);
            c.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static void sendResult(int workshop, int level, int passed, int failed) {
        String group = group();
        if (group.isEmpty()) { System.out.println(NO_GROUP); return; }
        JsonObject body = new JsonObject();
        body.addProperty("group", group);
        body.addProperty("workshop", workshop);
        body.addProperty("level", level);
        body.addProperty("passed", passed);
        body.addProperty("failed", failed);
        boolean sent = post(REPORT_PATH, body.toString()) != null;
        System.out.println(sent
            ? "  Result sent to " + host() + "  (group " + group + ", level " + level + ")"
            : "  Could not report to " + host() + " -- no network? The result above still stands.");
    }
}
