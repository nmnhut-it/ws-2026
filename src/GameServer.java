import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;

public class GameServer implements Net.Handler {

    // The port and the texts the server sends back, kept as constants so the tests can compare them.
    private static final int DEFAULT_PORT = 9000;
    static final String MISSING_NAME = "Missing \"name\" -- a login must carry a name: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String MISSING_GROUP = "Missing \"group\" -- check in with your group: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String NOT_LOGGED_IN = "No session on this connection -- send {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"} first";
    static final String PONG_TEXT = "pong";
    static final String BROADCAST_TEXT = "Server is listening, client number ";

    // Who is online, the race for the prize, and the player data kept in the database.
    private final Registry registry = new Registry();
    final Race     race     = new Race(this::decide);
    private final Players  players;

    public GameServer(Db db) { this.players = new Players(db); }

    // Start: print where to connect, then hand the port to Net. Net calls the methods below.
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        GameServer server = new GameServer(Db.open());
        server.race.start();
        System.out.println("========================================================");
        System.out.println("  Race for the prize, remember the players  --  Workshop 2");
        System.out.println("  Server:   ws://localhost:" + port);
        System.out.println("========================================================");
        Net.listen(port, server);
    }

    // A browser connected. Nothing to do until it sends a packet.
    @Override public void onOpen(Net.Client client) {}

    // Routing: read cmd from the packet and call the handler for it.
    @Override
    public void onMessage(Net.Client client, String message) {
        JsonObject msg = Net.json(message);
        String cmd = Net.str(msg, "cmd");
        if (cmd == null) { client.send(error("invalid JSON")); return; }
        switch (cmd) {
            case "login":       handleLogin(client, msg); return;
            case "getUserInfo": handleGetUserInfo(client); return;
            case "logout":      handleLogout(client); return;
            case "ping":        handlePing(client, msg); return;
            case "broadcast":   handleBroadcast(); return;
            case "claim":       handleClaim(client); return;
            case "reset":       handleReset(); return;
            default:            client.send(error("unknown cmd: " + cmd));
        }
    }

    // The browser left: forget its session and tell everyone the new count.
    @Override
    public void onClose(Net.Client client) {
        registry.remove(client);
        broadcastOnline();
    }

    // login: check name and group, open a session, answer the caller, update the online count.
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
        Registry.Player s = registry.add(client, name, group);
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "login");
        out.addProperty("uid", s.uid);
        out.addProperty("name", s.name);
        out.addProperty("group", s.group);
        out.addProperty("visits", visits(s));
        out.addProperty("slogan", slogan(s));
        out.addProperty("wins", lifetime(s));
        client.send(out.toString());
        broadcastOnline();
        client.send(prizeMsg().toString());
    }

    // How many races this group has ever won. A database error counts as 0.
    long lifetime(Registry.Player s) {
        try {
            return players.wins(s.group);
        } catch (IOException e) {
            System.out.println("players: " + e);
            return 0;
        }
    }

    // Write one more win for the group. Called once, for the winner.
    void saveWin(String group) {
        try {
            players.addWin(group);
        } catch (IOException e) {
            System.out.println("players: " + e);
        }
    }

    // claim: a logged-in player asks for the prize. The race decides on its own thread.
    void handleClaim(Net.Client client) {
        Registry.Player s = registry.get(client);
        if (s == null) { client.send(error(NOT_LOGGED_IN)); return; }
        race.submit(s);
    }

    // Runs on the race's thread: win or not, save the win, answer the player, update everyone.
    void decide(Registry.Player s) {
        boolean won = race.take(s.group);
        if (won) saveWin(s.group);
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "claim");
        out.addProperty("win", won);
        out.addProperty("name", s.name);
        out.addProperty("group", s.group);
        out.addProperty("winner", race.winner());
        out.addProperty("told", race.toldCount());
        s.client.send(out.toString());
        sendAll(prizeMsg().toString());
    }

    // reset: open a new race and tell everyone.
    void handleReset() {
        race.open();
        sendAll(prizeMsg().toString());
        System.out.println("*** NEW RACE: first claim wins");
    }

    // The race as a packet: open, the winner, and every group that was told it won.
    JsonObject prizeMsg() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "prize");
        out.addProperty("open", true);
        out.addProperty("winner", race.winner());
        out.addProperty("told", race.toldCount());
        JsonArray groups = new JsonArray();
        for (String g : race.everyoneTold()) groups.add(g);
        out.add("groups", groups);
        return out;
    }

    // The player's title, picked once and then read back from the database.
    String slogan(Registry.Player s) {
        try {
            return players.slogan(s.name);
        } catch (IOException e) {
            System.out.println("players: " + e);
            return "";
        }
    }

    // Count this visit and return how many times this name has logged in.
    long visits(Registry.Player s) {
        try {
            Players.Profile p = players.visit(s.name, s.group);
            return p == null ? 0 : p.visits;
        } catch (IOException e) {
            System.out.println("players: " + e);
            return 0;
        }
    }

    // getUserInfo: answer from the session this connection opened, not from the packet.
    void handleGetUserInfo(Net.Client client) {
        Registry.Player s = registry.get(client);
        if (s == null) { client.send(error(NOT_LOGGED_IN)); return; }
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "getUserInfo");
        out.addProperty("uid", s.uid);
        out.addProperty("name", s.name);
        out.addProperty("group", s.group);
        client.send(out.toString());
    }

    // logout: close the session but keep the connection open.
    void handleLogout(Net.Client client) {
        Registry.Player s = registry.get(client);
        if (s == null) { client.send(error(NOT_LOGGED_IN)); return; }
        registry.remove(client);
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "logout");
        out.addProperty("uid", s.uid);
        client.send(out.toString());
        broadcastOnline();
    }

    // The error packet every handler sends when a request is wrong.
    static String error(String text) {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "error");
        out.addProperty("text", text);
        return out.toString();
    }

    // Tell every logged-in client how many are online.
    void broadcastOnline() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "online");
        out.addProperty("count", registry.count());
        sendAll(out.toString());
    }

    // Send one packet to every logged-in client.
    void sendAll(String json) { for (Registry.Player s : registry.all()) s.client.send(json); }

    // broadcast: every logged-in client gets a packet with its own uid.
    void handleBroadcast() {
        for (Registry.Player s : registry.all()) {
            JsonObject out = new JsonObject();
            out.addProperty("cmd", "broadcast");
            out.addProperty("uid", s.uid);
            out.addProperty("text", BROADCAST_TEXT + s.uid);
            s.client.send(out.toString());
        }
    }

    // ping: answer pong, with the text the client sent if there was one.
    void handlePing(Net.Client client, JsonObject msg) {
        String text = Net.str(msg, "text");
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "pong");
        out.addProperty("text", text == null || text.isEmpty() ? PONG_TEXT : text);
        client.send(out.toString());
    }
}
