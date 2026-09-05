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

    private final Map<Long, Long>   strikes = new HashMap<>();
    private final Map<Long, Long>   damage  = new HashMap<>();
    private final Map<Long, String> names   = new HashMap<>();
    private long slainBy = NO_KILLER;

    public void spawn(Beast b) {
        name = b.name(); maxHp = b.maxHp(); hp = b.maxHp();
        strikes.clear(); damage.clear(); names.clear(); slainBy = NO_KILLER;
    }

    public boolean exists() { return name != null; }
    public boolean alive()  { return name != null && hp > 0; }
    public String  name()   { return name; }
    public int     maxHp()  { return maxHp; }
    public int     hp()     { return hp; }
    public long    slainByUid() { return slainBy; }

    public Hit strike(long uid, String who) {
        if (name == null || hp <= 0) return null;
        long n = strikes.merge(uid, 1L, Long::sum);
        int dmg = GameLogic.rollDamage(uid, n);
        int dealt = Math.min(dmg, hp);
        hp -= dealt;
        damage.merge(uid, (long) dealt, Long::sum);
        if (who != null) names.put(uid, who);
        boolean killed = false;
        if (hp <= 0 && slainBy == NO_KILLER) { hp = 0; slainBy = uid; killed = true; }
        return new Hit(uid, who, dealt, hp, killed);
    }

    public List<Entry> board(int limit) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<Long, Long> e : damage.entrySet())
            out.add(new Entry(e.getKey(), names.getOrDefault(e.getKey(), "#" + e.getKey()), e.getValue()));
        out.sort((a, b) -> Long.compare(b.total, a.total));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    public void reset() {
        name = null; maxHp = 0; hp = 0;
        strikes.clear(); damage.clear(); names.clear(); slainBy = NO_KILLER;
    }
}
