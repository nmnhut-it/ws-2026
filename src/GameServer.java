import game.Beast;
import game.GameLogic;
import java.io.IOException;

// TODO 1  checkBeast()      summon only when enough players are online   (GameLogic.conjure)
// TODO 2  BeastState.java   add your data structures + strike()          (separate file)
// TODO 3  handleAttack()    every attack request lowers the boss hp -> broadcast
// TODO 4  FightQueue.java   one queue, one worker thread                  (separate file)
// TODO 5  handleAttack()    switch to queue.submit(...)
// TODO 6  Records.java      saveBest() / top()                            (separate file)
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
        System.out.println("========================================================");
        System.out.println("  Danh boss va luu tru  --  Workshop 1");
        System.out.println("  Server:   ws://localhost:" + port + "   nguong trieu hoi: " + GameLogic.summonThreshold());
        System.out.println("========================================================");
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

    // TODO 1 -- SUMMON ONLY WHEN ENOUGH PLAYERS. Called after every "call".
    void checkBeast() {
    }

    // TODO 3 (part 1) / TODO 5 (part 2) -- ONE ATTACK REQUEST.
    void handleAttack(Net.Client client) {
    }

    // TODO 3 -- LOWER THE HP AND TELL EVERYONE (part 2: runs on the worker thread).
    void doStrike(Registry.Player s) {
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
