import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GameServer implements Net.Handler {

    private static final int DEFAULT_PORT = 9000;
    static final String MISSING_NAME = "Missing \"name\" -- a login must carry a name: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String MISSING_GROUP = "Missing \"group\" -- check in with your group: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String NOT_LOGGED_IN = "No session on this connection -- send {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"} first";
    static final String PONG_TEXT = "pong";
    static final String BROADCAST_TEXT = "Server is listening, client number ";

    private final Map<Net.Client, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong lastUid = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        System.out.println("========================================================");
        System.out.println("  Client calls server  --  Workshop 1");
        System.out.println("  Server:   ws://localhost:" + port);
        System.out.println("  Client:   open client/index.html, Server box = localhost:" + port);
        System.out.println("========================================================");
        Net.listen(port, new GameServer());
    }

    @Override
    public void onOpen(Net.Client client) {
        System.out.println("[+] open: " + client.id);
    }

    @Override
    public void onMessage(Net.Client client, String message) {
        System.out.println("[>] " + client.id + ": " + message);
        JsonObject msg = Net.json(message);
        String cmd = Net.str(msg, "cmd");
        if ("login".equals(cmd))     { handleLogin(client, msg); return; }
        if ("getUserInfo".equals(cmd))    { handleGetUserInfo(client); return; }
        if ("logout".equals(cmd))    { handleLogout(client); return; }
        if ("ping".equals(cmd))      { handlePing(client, msg); return; }
        if ("broadcast".equals(cmd)) { handleBroadcast(); return; }
        client.send(error("unknown cmd: " + cmd));
    }

    @Override
    public void onClose(Net.Client client) {
        System.out.println("[-] close: " + client.id);
        sessions.remove(client);
        broadcastOnline();
    }

    void handleLogin(Net.Client client, JsonObject msg) {
        String name  = Net.str(msg, "name");
        String group = Net.str(msg, "group");
        if (name == null || name.trim().isEmpty()) {
            client.send(error(MISSING_NAME));
            return;
        }
        if (group == null || group.trim().isEmpty()) {
            client.send(error(MISSING_GROUP));
            return;
        }
        long uid = lastUid.incrementAndGet();
        Session s = new Session(uid, name.trim(), group.trim());
        sessions.put(client, s);
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "login");
        out.addProperty("uid", s.uid);
        out.addProperty("name", s.name);
        out.addProperty("group", s.group);
        client.send(out.toString());
        broadcastOnline();
    }

    void handleGetUserInfo(Net.Client client) {
        Session s = sessions.get(client);
        if (s == null) {
            client.send(error(NOT_LOGGED_IN));
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "getUserInfo");
        out.addProperty("uid", s.uid);
        out.addProperty("name", s.name);
        out.addProperty("group", s.group);
        out.addProperty("seconds", s.seconds());
        client.send(out.toString());
    }

    void handleLogout(Net.Client client) {
        Session s = sessions.remove(client);
        if (s == null) {
            client.send(error(NOT_LOGGED_IN));
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "logout");
        out.addProperty("uid", s.uid);
        client.send(out.toString());
        broadcastOnline();
    }

    static String error(String text) {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "error");
        out.addProperty("text", text);
        return out.toString();
    }

    void broadcastOnline() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "online");
        out.addProperty("count", sessions.size());
        sendAll(out.toString());
    }

    void sendAll(String json) {
        for (Net.Client c : sessions.keySet()) c.send(json);
    }

    void handleBroadcast() {
        for (Map.Entry<Net.Client, Session> e : sessions.entrySet()) {
            long uid = e.getValue().uid;
            JsonObject out = new JsonObject();
            out.addProperty("cmd", "broadcast");
            out.addProperty("uid", uid);
            out.addProperty("text", BROADCAST_TEXT + uid);
            e.getKey().send(out.toString());
        }
    }

    void handlePing(Net.Client client, JsonObject msg) {
        String text = Net.str(msg, "text");
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "pong");
        out.addProperty("text", text == null || text.isEmpty() ? PONG_TEXT : text);
        client.send(out.toString());
    }
}
