import java.io.IOException;
import java.util.*;

// TODO (PART 2): write saveBest() and top().
public class Records {

    public static final class Entry {
        public final String name; public final long best;
        public Entry(String name, long best) { this.name = name; this.best = best; }
    }

    static final String KEY = "best";

    private final Db db;

    public Records(Db db) { this.db = db; }

    // TODO (PART 2) -- SAVE THE RECORD if total beats the old record.
    public boolean saveBest(String name, long total) throws IOException {
        return false;
    }

    // TODO (PART 2) -- TOP n records, highest first.
    public List<Entry> top(int n) throws IOException {
        return new ArrayList<>();
    }
}
