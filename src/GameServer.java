import game.Beast;
import game.GameLogic;
import java.io.IOException;

public class GameServer implements Net.Handler {

    private static final int DEFAULT_PORT = 9000;
    private static final int BOARD_TOP = 10;
    private static final int RECORDS_TOP = 10;
    static final String MISSING_NAME = "Thieu \"name\" -- goi tin phai co ten: {\"cmd\":\"call\",\"name\":\"...\",\"group\":\"...\"}";
    static final String BROADCAST_TEXT = "Server dang nghe, client so ";

    private final Registry   registry = new Registry();
    private final BeastState fight    = new BeastState();
    private final FightQueue queue    = new FightQueue(this::doStrike);
    private final Records    records;

    public GameServer(Db db) { this.records = new Records(db); }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        GameServer server = new GameServer(Db.open());
        server.queue.start();
        System.out.println("Danh boss va luu tru -- ws://localhost:" + port + "  nguong " + GameLogic.summonThreshold());
        Net.listen(port, server);
    }

    @Override public void onOpen(Net.Client client) {}

    @Override
    public void onMessage(Net.Client client, String message) {
        String cmd = Net.jsonString(message, "cmd");
        if (cmd == null) { client.send("{\"cmd\":\"error\",\"text\":\"JSON khong hop le\"}"); return; }
        switch (cmd) {
            case "call":    handleCall(client, message); return;
            case "broadcast":   handleBroadcast(); return;
            case "attack":  handleAttack(client); return;
            case "board":   client.send("{\"cmd\":\"board\",\"top\":" + boardArray(BOARD_TOP) + "}"); return;
            case "records": handleRecords(client); return;
            case "reset":   fight.reset(); sendAll("{\"cmd\":\"reset\"}"); checkBeast(); return;
            default:        client.send("{\"cmd\":\"error\",\"text\":\"lenh la: " + Net.esc(cmd) + "\"}");
        }
    }

    @Override
    public void onClose(Net.Client client) {
        registry.remove(client);
        broadcastOnline();
    }

    void handleCall(Net.Client client, String message) {
        String name  = Net.jsonString(message, "name");
        String group = Net.jsonString(message, "group");
        if (name == null || name.trim().isEmpty()) {
            client.send("{\"cmd\":\"error\",\"text\":\"" + Net.esc(MISSING_NAME) + "\"}");
            return;
        }
        Registry.Player s = registry.add(client, name, group);
        client.send("{\"cmd\":\"welcome\",\"uid\":" + s.uid
                  + ",\"name\":\"" + Net.esc(s.name) + "\",\"group\":\"" + Net.esc(s.group) + "\"}");
        broadcastOnline();
        if (fight.exists()) client.send(beastJson(null));
        checkBeast();
    }

    void broadcastOnline() { sendAll("{\"cmd\":\"online\",\"count\":" + registry.count() + "}"); }
    void sendAll(String json) { for (Registry.Player s : registry.all()) s.client.send(json); }
    void handleBroadcast() {
        for (Registry.Player s : registry.all())
            s.client.send("{\"cmd\":\"broadcast\",\"uid\":" + s.uid + ",\"text\":\"" + Net.esc(BROADCAST_TEXT + s.uid) + "\"}");
    }

    synchronized void checkBeast() {
        if (fight.exists()) return;
        Beast b = GameLogic.conjure(registry.count());
        if (b == null) return;
        fight.spawn(b);
        sendAll(beastJson(null));
        System.out.println("*** TRIEU HOI: " + b);
    }

    void handleAttack(Net.Client client) {
        Registry.Player s = registry.get(client);
        if (s == null) return;
        queue.submit(s);
    }

    void doStrike(Registry.Player s) {
        BeastState.Hit hit = fight.strike(s.uid, s.name);
        if (hit == null) return;
        sendAll(beastJson("\"lastHit\":{\"uid\":" + hit.uid + ",\"name\":\"" + Net.esc(hit.name)
              + "\",\"damage\":" + hit.damage + "}"));
        if (hit.killed) {
            sendAll("{\"cmd\":\"slain\",\"uid\":" + hit.uid + ",\"name\":\"" + Net.esc(hit.name)
                  + "\",\"finalBlow\":" + hit.damage + ",\"top\":" + boardArray(BOARD_TOP) + "}");
            System.out.println("*** HA GUC boi " + s);
            saveRecords();
        }
    }

    void saveRecords() {
        try {
            for (BeastState.Entry e : fight.board(Integer.MAX_VALUE)) records.saveBest(e.name, e.total);
        } catch (IOException ex) { System.out.println("records: " + ex); }
    }

    void handleRecords(Net.Client client) {
        StringBuilder sb = new StringBuilder("{\"cmd\":\"records\",\"top\":[");
        try {
            boolean first = true;
            for (Records.Entry e : records.top(RECORDS_TOP)) {
                if (!first) sb.append(',');
                sb.append("{\"name\":\"").append(Net.esc(e.name)).append("\",\"best\":").append(e.best).append('}');
                first = false;
            }
        } catch (IOException ex) { System.out.println("records: " + ex); }
        client.send(sb.append("]}").toString());
    }

    String beastJson(String extra) {
        return "{\"cmd\":\"beast\",\"name\":\"" + Net.esc(fight.name())
             + "\",\"maxHp\":" + fight.maxHp() + ",\"hp\":" + fight.hp()
             + (extra == null ? "" : "," + extra) + "}";
    }

    String boardArray(int limit) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (BeastState.Entry e : fight.board(limit)) {
            if (!first) sb.append(',');
            sb.append("{\"uid\":").append(e.uid).append(",\"name\":\"").append(Net.esc(e.name))
              .append("\",\"total\":").append(e.total).append('}');
            first = false;
        }
        return sb.append(']').toString();
    }
}
