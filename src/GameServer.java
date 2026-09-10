import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import game.Beast;
import game.GameLogic;
import java.io.IOException;

// The player's data first, the fight second.
// TODO 1  Players.java      load() / visit()   the player profile in Db   (its own file)
// TODO 2  Players.java      damage() / addDamage()   the lifetime damage
// TODO 3  here              load it at login, save it when a boss dies
// TODO 4  FightQueue.java   one queue, one worker                        (its own file)
// TODO 5  handleAttack()    swap doStrike(s) for queue.submit(s)
public class GameServer implements Net.Handler {

    private static final int DEFAULT_PORT = 9000;
    private static final int BOARD_TOP = 10;
    private static final int BOARD_ALL = 10000;
    static final String MISSING_NAME = "Missing \"name\" -- a login must carry a name: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String MISSING_GROUP = "Missing \"group\" -- check in with your group: {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"}";
    static final String NOT_LOGGED_IN = "No session on this connection -- send {\"cmd\":\"login\",\"name\":\"...\",\"group\":\"...\"} first";
    static final String PONG_TEXT = "pong";
    static final String BROADCAST_TEXT = "Server is listening, client number ";

    private final Registry   registry = new Registry();
    private final BeastState fight    = new BeastState();
    private final FightQueue queue    = new FightQueue(this::doStrike);
    private final Players    players;

    public GameServer(Db db) { this.players = new Players(db); }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        GameServer server = new GameServer(Db.open());
        server.queue.start();
        System.out.println("========================================================");
        System.out.println("  Fight the boss, save the players  --  Workshop 2");
        System.out.println("  Server:   ws://localhost:" + port + "   summon threshold: " + GameLogic.summonThreshold());
        System.out.println("========================================================");
        Net.listen(port, server);
    }

    @Override public void onOpen(Net.Client client) {}

    @Override
    public void onMessage(Net.Client client, String message) {
        JsonObject msg = Net.json(message);
        String cmd = Net.str(msg, "cmd");
        if (cmd == null) { client.send(error("invalid JSON")); return; }
        switch (cmd) {
            case "login":      handleLogin(client, msg); return;
            case "getUserInfo":    handleGetUserInfo(client); return;
            case "logout":    handleLogout(client); return;
            case "ping":      handlePing(client, msg); return;
            case "broadcast": handleBroadcast(); return;
            case "attack":    handleAttack(client); return;
            case "board":     client.send(boardMsg().toString()); return;
            case "reset":     fight.reset(); sendAll(resetMsg()); checkBeast(); return;
            default:          client.send(error("unknown cmd: " + cmd));
        }
    }

    @Override
    public void onClose(Net.Client client) {
        registry.remove(client);
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
        Registry.Player s = registry.add(client, name, group);
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "login");
        out.addProperty("uid", s.uid);
        out.addProperty("name", s.name);
        out.addProperty("group", s.group);
        out.addProperty("visits", visits(s));
        out.addProperty("slogan", slogan(s));
        // TODO 3 -- LOAD AT LOGIN: the lifetime damage read out of the database goes here.
        //   out.addProperty("damage", lifetime(s));   <- one line, lifetime() is written for you
        client.send(out.toString());
        broadcastOnline();
        if (fight.exists()) client.send(beastMsg().toString());
        checkBeast();
    }

    long lifetime(Registry.Player s) {
        try {
            return players.damage(s.name);
        } catch (IOException e) {
            System.out.println("players: " + e);
            return 0;
        }
    }

    void bank(String name, long amount) {
        try {
            players.addDamage(name, amount);
        } catch (IOException e) {
            System.out.println("players: " + e);
        }
    }

    // TODO 3 -- SAVE WHEN THE FIGHT ENDS: the given kill path calls this once, when a boss dies.
    //   Walk the damage board -- for (BeastState.Entry e : fight.board(BOARD_ALL)) -- and for each
    //   row call bank(e.name, e.total). One write per fighter per fight, not one per strike.
    void saveDamage() {
    }

    String slogan(Registry.Player s) {
        try {
            return players.slogan(s.name);
        } catch (IOException e) {
            System.out.println("players: " + e);
            return "";
        }
    }

    long visits(Registry.Player s) {
        try {
            Players.Profile p = players.visit(s.name, s.group);
            return p == null ? 0 : p.visits;
        } catch (IOException e) {
            System.out.println("players: " + e);
            return 0;
        }
    }

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

    static String error(String text) {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "error");
        out.addProperty("text", text);
        return out.toString();
    }

    static String resetMsg() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "reset");
        return out.toString();
    }

    void broadcastOnline() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "online");
        out.addProperty("count", registry.count());
        sendAll(out.toString());
    }

    void sendAll(String json) { for (Registry.Player s : registry.all()) s.client.send(json); }

    void handleBroadcast() {
        for (Registry.Player s : registry.all()) {
            JsonObject out = new JsonObject();
            out.addProperty("cmd", "broadcast");
            out.addProperty("uid", s.uid);
            out.addProperty("text", BROADCAST_TEXT + s.uid);
            s.client.send(out.toString());
        }
    }

    synchronized void checkBeast() {
        if (fight.exists()) return;
        Beast b = GameLogic.conjure(registry.count());
        if (b == null) return;
        fight.spawn(b);
        sendAll(beastMsg().toString());
        System.out.println("*** SUMMONED: " + b);
    }

    // TODO 5 -- swap doStrike(s) for queue.submit(s) once FightQueue is written.
    void handleAttack(Net.Client client) {
        Registry.Player s = registry.get(client);
        if (s == null) return;
        doStrike(s);
    }

    void doStrike(Registry.Player s) {
        BeastState.Hit hit = fight.strike(s.uid, s.name);
        if (hit == null) return;
        JsonObject lastHit = new JsonObject();
        lastHit.addProperty("uid", hit.uid);
        lastHit.addProperty("name", hit.name);
        lastHit.addProperty("damage", hit.damage);
        JsonObject beat = beastMsg();
        beat.add("lastHit", lastHit);
        sendAll(beat.toString());
        if (hit.killed) {
            JsonObject slain = new JsonObject();
            slain.addProperty("cmd", "slain");
            slain.addProperty("uid", hit.uid);
            slain.addProperty("name", hit.name);
            slain.addProperty("finalBlow", hit.damage);
            slain.add("top", boardArray(BOARD_TOP));
            saveDamage();
            sendAll(slain.toString());
            System.out.println("*** SLAIN by " + s);
        }
    }

    JsonObject beastMsg() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "beast");
        out.addProperty("name", fight.name());
        out.addProperty("maxHp", fight.maxHp());
        out.addProperty("hp", fight.hp());
        return out;
    }

    JsonObject boardMsg() {
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "board");
        out.add("top", boardArray(BOARD_TOP));
        return out;
    }

    JsonArray boardArray(int limit) {
        JsonArray rows = new JsonArray();
        for (BeastState.Entry e : fight.board(limit)) {
            JsonObject row = new JsonObject();
            row.addProperty("uid", e.uid);
            row.addProperty("name", e.name);
            row.addProperty("total", e.total);
            rows.add(row);
        }
        return rows;
    }

    void handlePing(Net.Client client, JsonObject msg) {
        String text = Net.str(msg, "text");
        JsonObject out = new JsonObject();
        out.addProperty("cmd", "pong");
        out.addProperty("text", text == null || text.isEmpty() ? PONG_TEXT : text);
        client.send(out.toString());
    }
}
