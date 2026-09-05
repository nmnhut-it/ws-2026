import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GameServer implements Net.Handler {

    private static final int DEFAULT_PORT = 9000;
    static final String MISSING_NAME = "Thieu \"name\" -- goi tin phai co ten: {\"cmd\":\"call\",\"name\":\"...\",\"group\":\"...\"}";
    static final String BROADCAST_TEXT = "Server dang nghe, client so ";

    private final Map<Net.Client, Long> online = new ConcurrentHashMap<>();
    private final AtomicLong lastUid = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        System.out.println("Client goi server -- ws://localhost:" + port);
        Net.listen(port, new GameServer());
    }

    @Override public void onOpen(Net.Client client) { System.out.println("[+] " + client.id); }

    @Override
    public void onMessage(Net.Client client, String message) {
        System.out.println("[>] " + client.id + ": " + message);
        String cmd = Net.jsonString(message, "cmd");
        if ("call".equals(cmd))      { handleCall(client, message); return; }
        if ("broadcast".equals(cmd)) { handleBroadcast(); return; }
        client.send("{\"cmd\":\"error\",\"text\":\"lenh la: " + Net.esc(String.valueOf(cmd)) + "\"}");
    }

    @Override
    public void onClose(Net.Client client) {
        System.out.println("[-] " + client.id);
        online.remove(client);
        broadcastOnline();
    }

    void handleCall(Net.Client client, String message) {
        String name  = Net.jsonString(message, "name");
        String group = Net.jsonString(message, "group");
        if (name == null || name.trim().isEmpty()) {
            client.send("{\"cmd\":\"error\",\"text\":\"" + Net.esc(MISSING_NAME) + "\"}");
            return;
        }
        long uid = lastUid.incrementAndGet();
        online.put(client, uid);
        client.send("{\"cmd\":\"welcome\",\"uid\":" + uid
                  + ",\"name\":\"" + Net.esc(name.trim()) + "\",\"group\":\"" + Net.esc(group == null ? "" : group.trim()) + "\"}");
        broadcastOnline();
    }

    void broadcastOnline() {
        sendAll("{\"cmd\":\"online\",\"count\":" + online.size() + "}");
    }

    void sendAll(String json) {
        for (Net.Client c : online.keySet()) c.send(json);
    }

    void handleBroadcast() {
        for (Map.Entry<Net.Client, Long> e : online.entrySet()) {
            long uid = e.getValue();
            e.getKey().send("{\"cmd\":\"broadcast\",\"uid\":" + uid
                          + ",\"text\":\"" + Net.esc(BROADCAST_TEXT + uid) + "\"}");
        }
    }
}
