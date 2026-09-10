import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

// TODO 1  handleLogin()    open a session, reply to the caller  -> "login"
// TODO 2  handleLogin()    no "name" or no "group"              -> "error"
// TODO 3  handleGetUserInfo()   answer from the session, not from the packet
// TODO 4  handleLogout()   close the session
public class GameServer implements Net.Handler {

    private static final int DEFAULT_PORT = 9000;
    static final String MISSING_NAME = "Missing \"name\" -- a login must carry a name: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String MISSING_GROUP = "Missing \"group\" -- check in with your group: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String NOT_LOGGED_IN = "No session on this connection -- send {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"} first";
    static final String PONG_TEXT = "pong";
    static final String BROADCAST_TEXT = "Server is listening, client number ";

    private final ConcurrentMap<Net.Client, Session> sessions = new ConcurrentHashMap<>();
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

    // TODO 1 -- OPEN A SESSION, then reply to that one client only:
    //   read:    String name = Net.str(msg, "name");  read "group" the same way
    //   number:  long uid = lastUid.incrementAndGet();
    //   session: sessions.put(client, new Session(uid, name.trim(), group.trim()));
    //   packet:  {"cmd":"login","uid":1,"name":"An","group":"nhom-3"}
    //   send:    build a JsonObject, then client.send(...)  -- handleBroadcast() below is the model
    //   last:    call broadcastOnline() so everyone sees the new count.
    // TODO 2 -- FIELD MISSING, NO SESSION:
    //   "name" null or empty  -> client.send(error(MISSING_NAME));  return;
    //   "group" null or empty -> client.send(error(MISSING_GROUP)); return;
    void handleLogin(Net.Client client, JsonObject msg) {
    }

    // TODO 3 -- WHO IS ON THIS CONNECTION? The packet carries nothing but the cmd,
    //   so the answer has to come out of the session the login left behind:
    //   Session s = sessions.get(client);
    //   no session -> client.send(error(NOT_LOGGED_IN)); return;
    //   packet:  {"cmd":"getUserInfo","uid":1,"name":"An","group":"nhom-3","seconds":12}
    //   seconds: s.seconds() -- how long this session has been open.
    void handleGetUserInfo(Net.Client client) {
    }

    // TODO 4 -- CLOSE THE SESSION, keep the connection open:
    //   no session -> client.send(error(NOT_LOGGED_IN)); return;
    //   remove it:  sessions.remove(client)
    //   packet:     {"cmd":"logout","uid":1}   then broadcastOnline();
    //   after this a "getUserInfo" on the same connection must answer error again.
    void handleLogout(Net.Client client) {
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
