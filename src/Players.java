import java.io.IOException;
import java.util.Random;

// TODO 1  load() / visit()          player profile, stored in Db with set() and get()
// TODO 2  damage() / addDamage()   lifetime damage, read at login, added when a boss dies
public class Players {

    public static final class Profile {
        public final String name; public final String group; public final long visits;
        public Profile(String name, String group, long visits) {
            this.name = name; this.group = group; this.visits = visits;
        }
    }

    static final String PREFIX = "player:";
    static final char   SEP    = '|';

    static final String DAMAGE_PREFIX = "damage:";
    static final String SLOGAN_PREFIX = "slogan:";
    static final String[] SLOGANS = {
        "Packet whisperer", "Reads the log first", "Holds Enter like a pro",
        "Boss slayer in training", "Race condition survivor", "Queue believer",
        "Null check enjoyer", "Thread number one", "Stack trace reader",
        "Port 9000 regular", "JSON without typos", "Ctrl-C veteran"
    };
    private static final Random RANDOM = new Random();

    private final Db db;

    public Players(Db db) { this.db = db; }

    // TODO 1 -- READ THE SAVED PROFILE: db.get(PREFIX + name). Nothing saved yet -> null.
    // The stored string looks like "group|visits", split at the last SEP.
    public Profile load(String name) throws IOException {
        return null;
    }

    // TODO 1 -- WRITE THE PROFILE: first visit = 1, otherwise add 1.
    // Keep the newest group, write with db.set(PREFIX + name, ...), return what you wrote.
    public Profile visit(String name, String group) throws IOException {
        return null;
    }

    // TODO 2 -- LIFETIME DAMAGE, READ AT LOGIN: db.get(DAMAGE_PREFIX + name).
    // Nothing saved yet -> 0. The stored string is just the number.
    public long damage(String name) throws IOException {
        return 0;
    }

    // TODO 2 -- ADD TO IT: the given code calls this for every fighter when a boss dies.
    // Read the old total, add amount, write it back with db.set, return the new total.
    public long addDamage(String name, long amount) throws IOException {
        return 0;
    }

    public String slogan(String name) throws IOException {
        String saved = db.get(SLOGAN_PREFIX + name);
        if (saved != null && !saved.isEmpty()) return saved;
        String picked = SLOGANS[RANDOM.nextInt(SLOGANS.length)];
        db.set(SLOGAN_PREFIX + name, picked);
        return picked;
    }
}
