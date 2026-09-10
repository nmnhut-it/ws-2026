import com.google.gson.JsonObject;
import game.Beast;
import game.GameLogic;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class Tests {

    static final int   WORKSHOP = 2;
    static final int[] LEVEL_AT = { 0, 0, 0, 1, 1, 2, 2, 3 };

    static int passed = 0, failed = 0, part = 0, failAt = 0;
    static void ok(String w)              { passed++; System.out.println("  [ OK ]  " + w); }
    static void bad(String w, String why) { failed++; if (failAt == 0) failAt = part;
        System.out.println("  [FAIL]  " + w + "\n          -> " + why); }
    static void eq(String w, long got, long want) {
        if (got == want) ok(w + "  (" + got + ")");
        else bad(w, "got " + got + ", expected " + want);
    }

    static void yes(String w, boolean c, String why) { if (c) ok(w); else bad(w, why); }
    static void info(String w)            { System.out.println("  [INFO]  " + w); }
    static void section(String s){ part++; System.out.println("\n--- " + s + " ---"); }
    static final String[] lastError = { "" };
    static ThreadFactory daemon() { return r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }; }

    private static final int BIG_CROWD = 200;
    static Beast bigBeast() { return GameLogic.conjure(Math.max(GameLogic.summonThreshold(), BIG_CROWD)); }
    static Registry.Player player(long uid) { return new Registry.Player(uid, "P" + uid, "N", Net.testClient("t" + uid)); }

    static JsonObject lastOf(Net.Client c, String cmd) {
        JsonObject found = null;
        for (String m : c.sent()) {
            JsonObject o = Net.json(m);
            if (cmd.equals(Net.str(o, "cmd"))) found = o;
        }
        return found;
    }

    public static void main(String[] args) throws Exception {

        section("1. PLAYER PROFILE in the database  (TODO 1)");
        {
            File f = File.createTempFile("players-test", ".txt");
            f.deleteOnExit();
            f.delete();
            try (Db db = new Db.FileDb(f)) {
                Players p = new Players(db);
                yes("never played -> load() returns null", p.load("An") == null, "load() returned something");
                Players.Profile first = p.visit("An", "Nhom 1");
                yes("first time: visits = 1", first != null && first.visits == 1,
                    first == null ? "visit() returned null -- not written?" : "visits = " + first.visits);
                Players.Profile again = p.visit("An", "Nhom 1");
                yes("second time: visits = 2", again != null && again.visits == 2,
                    again == null ? "visit() returned null" : "visits = " + again.visits);
                Players.Profile other = p.visit("Binh", "Nhom 2");
                yes("another player counts separately", other != null && other.visits == 1,
                    other == null ? "visit() returned null" : "visits = " + other.visits);
                Players.Profile back = p.load("An");
                yes("load() reads the group back", back != null && "Nhom 1".equals(back.group),
                    back == null ? "load() returned null" : "group = " + back.group);
            }
            try (Db db = new Db.FileDb(f)) {
                Players.Profile back = new Players(db).load("An");
                yes("reopen the database: the profile survives", back != null && back.visits == 2,
                    back == null ? "data lost after reopen" : "visits = " + back.visits);
            }
        }

        section("2. LIFETIME DAMAGE: read it, add to it  (TODO 2)");
        {
            File f = File.createTempFile("damage-test", ".txt");
            f.deleteOnExit();
            f.delete();
            try (Db db = new Db.FileDb(f)) {
                Players p = new Players(db);
                eq("never fought -> damage is 0", p.damage("An"), 0);
                eq("first fight adds up", p.addDamage("An", 120), 120);
                eq("the next fight adds to it", p.addDamage("An", 30), 150);
                eq("reading it back gives the same total", p.damage("An"), 150);
                eq("another player has his own total", p.damage("Binh"), 0);
            }
            try (Db db = new Db.FileDb(f)) {
                eq("reopen the database: the total survives", new Players(db).damage("An"), 150);
            }
        }

        section("3. LOAD AT LOGIN: the login carries the saved total  (TODO 3)");
        {
            File f = File.createTempFile("login-test", ".txt");
            f.deleteOnExit();
            f.delete();
            try (Db db = new Db.FileDb(f)) {
                new Players(db).addDamage("An", 777);
                GameServer server = new GameServer(db);
                Net.Client c = Net.testClient("a");
                server.onOpen(c);
                server.onMessage(c, "{\"cmd\":\"login\",\"name\":\"An\",\"group\":\"Nhom 1\"}");
                JsonObject w = lastOf(c, "login");
                yes("the login comes back", w != null, "sent=" + c.sent());
                yes("the login carries damage", w != null && w.get("damage") != null,
                    "no damage field -- TODO 3 in handleLogin: out.addProperty(\"damage\", lifetime(s));");
                if (w != null && w.get("damage") != null)
                    eq("and it is the total saved before", w.get("damage").getAsLong(), 777);

                Net.Client fresh = Net.testClient("b");
                server.onOpen(fresh);
                server.onMessage(fresh, "{\"cmd\":\"login\",\"name\":\"Binh\",\"group\":\"Nhom 1\"}");
                JsonObject w2 = lastOf(fresh, "login");
                if (w2 != null && w2.get("damage") != null)
                    eq("a name that never fought loads 0", w2.get("damage").getAsLong(), 0);
            }
        }

        section("4. One strike  (given code -- BeastState.strike)");
        {
            BeastState st = new BeastState();
            Beast b = bigBeast();
            st.spawn(b);
            eq("starting hp", st.hp(), b.maxHp());
            BeastState.Hit h = st.strike(7, "An");
            if (h == null) bad("strike() returns a Hit", "null -- BeastState.strike() missing?");
            else {
                eq("damage = rollDamage(7, 1)", h.damage, GameLogic.rollDamage(7, 1));
                eq("hp left", st.hp(), b.maxHp() - h.damage);
                yes("not dead yet", !h.killed, "dead after one strike?");
                yes("the board lists An", !st.board(10).isEmpty() && "An".equals(st.board(10).get(0).name), "board = " + st.board(10).size() + " rows");
            }
        }

        section("5. Many strikes, many players  (given code)");
        {
            BeastState st = new BeastState();
            Beast b = bigBeast();
            st.spawn(b);
            long expect = 0;
            for (int i = 1; i <= 5; i++) expect += GameLogic.rollDamage(7, i);
            for (int i = 1; i <= 3; i++) expect += GameLogic.rollDamage(8, i);
            for (int i = 0; i < 5; i++) st.strike(7, "An");
            for (int i = 0; i < 3; i++) st.strike(8, "Binh");
            eq("strike n of EACH uid uses rollDamage(uid, n)", st.hp(), b.maxHp() - expect);
            yes("nobody landed the kill yet", st.slainByUid() == BeastState.NO_KILLER, "slainBy = " + st.slainByUid());
            yes("the board is sorted high first", st.board(10).size() == 2 && st.board(10).get(0).total >= st.board(10).get(1).total, "board out of order");
        }

        section("6. 20 threads x 50 strikes AT ONCE, calling strike() directly  (LOOK only, not graded)");
        {
            BeastState st = new BeastState();
            Beast b = bigBeast();
            st.spawn(b);
            final int THREADS = 20, EACH = 50;
            long expect = 0;
            for (long uid = 1; uid <= THREADS; uid++) expect += GameLogic.totalDamage(uid, EACH);
            ExecutorService pool = Executors.newFixedThreadPool(THREADS, daemon());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> fs = new ArrayList<>();
            for (long uid = 1; uid <= THREADS; uid++) {
                final long u = uid;
                fs.add(pool.submit(() -> {
                    int threw = 0;
                    try { go.await(); } catch (InterruptedException e) { return 0; }
                    for (int i = 0; i < EACH; i++) {
                        try { st.strike(u, "P" + u); }
                        catch (RuntimeException e) { threw++; lastError[0] = e.getClass().getSimpleName(); }
                    }
                    return threw;
                }));
            }
            go.countDown();
            int threw = 0;
            for (Future<Integer> f : fs) threw += f.get();
            pool.shutdown();
            long dealt = b.maxHp() - st.hp();
            if (threw > 0)
                info("strike() threw " + lastError[0] + " " + threw + " times -- two threads wrote the HashMap AT ONCE."
                   + "\n          No lock, no queue -> the data structure breaks. The queue in test 4 fixes it.");
            else if (dealt == expect)
                info("nothing lost (" + dealt + ") -- lucky run, or you already locked strike(). Run it a few more times.");
            else
                info("dealt " + dealt + ", should be " + expect + "  -> LOST " + (expect - dealt) + " damage."
                   + "\n          Two threads read the same hp, both subtract, one write wins. No exception, no log."
                   + "\n          The queue in test 4 fixes it. Before TODO 1 this line is EXPECTED.");
        }

        section("7. QUEUE: 16 threads submit, ONE worker handles -> exactly ONE killer  (TODO 4, 5)");
        {
            BeastState st = new BeastState();
            st.spawn(GameLogic.conjure(GameLogic.summonThreshold()));
            final int[] kills = {0};
            FightQueue q = new FightQueue(who -> { BeastState.Hit h = st.strike(who.uid, who.name); if (h != null && h.killed) kills[0]++; });
            q.start();
            final int THREADS = 16, EACH = 400;
            ExecutorService pool = Executors.newFixedThreadPool(THREADS, daemon());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> fs = new ArrayList<>();
            for (long uid = 1; uid <= THREADS; uid++) {
                final Registry.Player s = player(uid);
                fs.add(pool.submit(() -> { try { go.await(); } catch (InterruptedException e) { return; }
                                           for (int i = 0; i < EACH; i++) q.submit(s); }));
            }
            go.countDown();
            for (Future<?> f : fs) f.get();
            pool.shutdown();
            long deadline = System.currentTimeMillis() + 10000;
            while ((q.pending() > 0 || st.hp() > 0) && System.currentTimeMillis() < deadline) Thread.sleep(20);
            Thread.sleep(100);
            eq("queue is empty", q.pending(), 0);
            eq("number of killers", kills[0], 1);
            eq("final hp = 0", st.hp(), 0);
            yes("the killer is recorded", st.slainByUid() > 0, "slainByUid = " + st.slainByUid() + " -- submit()/run() not written?");
        }

        int done  = failAt == 0 ? LEVEL_AT.length - 1 : failAt - 1;
        int level = LEVEL_AT[done];
        System.out.println("\n========================================");
        System.out.println("  passed : " + passed);
        System.out.println("  failed : " + failed);
        System.out.println("  level  : " + level + "/3");
        System.out.println("========================================");
        Report.sendResult(WORKSHOP, level, passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
