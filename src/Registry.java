import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Who is online in Workshop 2: one Player per logged-in connection.
public class Registry {

    // One logged-in player and the connection to answer it on.
    public static final class Player {
        public final long uid;
        public final String name;
        public final String group;
        public final Net.Client client;

        public Player(long uid, String name, String group, Net.Client client) {
            this.uid = uid; this.name = name; this.group = group; this.client = client;
        }
        @Override public String toString() { return "#" + uid + " " + name + " (" + group + ")"; }
    }

    private static final String NO_GROUP = "-";

    private final Map<Net.Client, Player> players = new ConcurrentHashMap<>();
    private final AtomicLong lastUid = new AtomicLong(0);

    public long nextUid() {
        return lastUid.incrementAndGet();
    }

    // Log a player in: a new uid, a trimmed name, and - when the group is empty.
    public Player add(Net.Client client, String name, String group) {
        String g = (group == null || group.trim().isEmpty()) ? NO_GROUP : group.trim();
        Player s = new Player(nextUid(), name.trim(), g, client);
        players.put(client, s);
        return s;
    }

    public Player remove(Net.Client client) {
        return players.remove(client);
    }

    public Player get(Net.Client client) {
        return players.get(client);
    }

    public int count() {
        return players.size();
    }

    // A copy, so callers can loop while others log in or out.
    public List<Player> all() {
        return new ArrayList<>(players.values());
    }
}
