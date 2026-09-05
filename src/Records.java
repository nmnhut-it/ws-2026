import java.io.IOException;
import java.util.*;

public class Records {

    public static final class Entry {
        public final String name; public final long best;
        public Entry(String name, long best) { this.name = name; this.best = best; }
    }

    static final String KEY = "best";

    private final Db db;

    public Records(Db db) { this.db = db; }

    public boolean saveBest(String name, long total) throws IOException {
        Double old = db.zscore(KEY, name);
        if (old != null && total <= old) return false;
        db.zadd(KEY, total, name);
        return true;
    }

    public List<Entry> top(int n) throws IOException {
        List<Entry> out = new ArrayList<>();
        for (Db.Row r : db.zrevrange(KEY, n)) out.add(new Entry(r.member, (long) r.score));
        return out;
    }
}
