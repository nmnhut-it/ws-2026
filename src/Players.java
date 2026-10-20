import java.io.IOException;
import java.util.Random;

public class Players {

    // A saved player: name, newest group, number of visits.
    public static final class Profile {
        public final String name; public final String group; public final long visits;
        public Profile(String name, String group, long visits) {
            this.name = name; this.group = group; this.visits = visits;
        }
    }

    // Keys in the database: player:<name>, group:<group>, slogan:<name>.
    static final String PREFIX = "player:";
    static final char   SEP    = '|';

    static final String GROUP_PREFIX = "group:";
    static final String SLOGAN_PREFIX = "slogan:";
    static final String[] SLOGANS = {
        "Packet whisperer", "Reads the log first", "Holds Enter like a pro",
        "Red bar sprinter", "Race condition survivor", "Queue believer",
        "Null check enjoyer", "Thread number one", "Stack trace reader",
        "Port 9000 regular", "JSON without typos", "Ctrl-C veteran"
    };
    private static final Random RANDOM = new Random();

    private final Db db;

    public Players(Db db) { this.db = db; }
    // Read the saved profile, stored as group|visits. Nothing saved yet -> null.
    public Profile load(String name) throws IOException {
        String raw = db.get(PREFIX + name);
        if (raw == null) return null;
        int cut = raw.lastIndexOf(SEP);
        if (cut < 0) return null;
        try {
            return new Profile(name, raw.substring(0, cut), Long.parseLong(raw.substring(cut + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Count one more visit and save it, keeping the newest group.
    public Profile visit(String name, String group) throws IOException {
        Profile old = load(name);
        long visits = old == null ? 1 : old.visits + 1;
        String keep = (group == null || group.isEmpty()) && old != null ? old.group : group;
        if (keep == null) keep = "";
        db.set(PREFIX + name, keep + SEP + visits);
        return new Profile(name, keep, visits);
    }

    // How many logins the whole group has had. Nothing saved yet -> 0.
    public long groupVisits(String group) throws IOException {
        String raw = db.get(GROUP_PREFIX + group);
        if (raw == null || raw.isEmpty()) return 0;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // One more login for the group: read, add 1, write back.
    public long addGroupVisit(String group) throws IOException {
        long total = groupVisits(group) + 1;
        db.set(GROUP_PREFIX + group, String.valueOf(total));
        return total;
    }
    // The player's title: picked at random the first time, then always the same one.
    public String slogan(String name) throws IOException {
        String saved = db.get(SLOGAN_PREFIX + name);
        if (saved != null && !saved.isEmpty()) return saved;
        String picked = SLOGANS[RANDOM.nextInt(SLOGANS.length)];
        db.set(SLOGAN_PREFIX + name, picked);
        return picked;
    }
}