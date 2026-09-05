import game.Beast;
import game.GameLogic;
import java.util.*;

public class BeastState {

    public static final class Hit {
        public final long uid; public final String name;
        public final int damage; public final int hpAfter; public final boolean killed;
        public Hit(long uid, String name, int damage, int hpAfter, boolean killed) {
            this.uid=uid; this.name=name; this.damage=damage; this.hpAfter=hpAfter; this.killed=killed;
        }
    }

    public static final class Entry {
        public final long uid; public final String name; public final long total;
        public Entry(long uid, String name, long total){ this.uid=uid; this.name=name; this.total=total; }
    }

    public static final long NO_KILLER = -1;

    private String name = null;
    private int maxHp = 0;
    private int hp = 0;

    // TODO (PART 1) -- ADD YOUR DATA STRUCTURES HERE.
    public void spawn(Beast b) {
        name = b.name(); maxHp = b.maxHp(); hp = b.maxHp();
        // TODO (PART 1): clear your data structures (new fight)
    }

    public boolean exists() { return name != null; }
    public boolean alive()  { return name != null && hp > 0; }
    public String  name()   { return name; }
    public int     maxHp()  { return maxHp; }
    public int     hp()     { return hp; }

    // uid of the killer, NO_KILLER if nobody yet. TODO (PART 1)
    public long slainByUid() {
        return NO_KILLER;
    }

    // ONE STRIKE.  TODO (PART 1) -- the most important method of the workshop.
    public Hit strike(long uid, String who) {
        return null;
    }

    // The damage board, highest first, at most limit rows. TODO (PART 1)
    public List<Entry> board(int limit) {
        return new ArrayList<>();
    }

    public void reset() {
        name = null; maxHp = 0; hp = 0;
        // TODO (PART 1): clear your data structures
    }
}
