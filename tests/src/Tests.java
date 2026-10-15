import com.google.gson.JsonObject;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class Tests {

    static final int   WORKSHOP = 2;
    static final int[] LEVEL_AT = { 0, 0, 0, 1, 2, 2, 3 };

    static final int CROWD = 12;
    static final String GROUP_PREFIX = "nhom-";

    static int passed = 0, failed = 0, part = 0, failAt = 0;
    static void ok(String w)              { passed++; System.out.println("  [ OK ]  " + w); }
    static void bad(String w, String why) { failed++; if (failAt == 0) failAt = part;
        System.out.println("  [FAIL]  " + w + "\n          -> " + why); }
    static void yes(String w, boolean c, String why) { if (c) ok(w); else bad(w, why); }
    static void info(String w)            { System.out.println("  [INFO]  " + w); }
    static void section(String s){ part++; System.out.println("\n--- " + s + " ---"); }
    static ThreadFactory daemon() { return r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }; }

    static JsonObject lastOf(Net.Client c, String cmd) {
        JsonObject found = null;
        for (String m : c.sent()) {
            JsonObject o = Net.json(m);
            if (cmd.equals(Net.str(o, "cmd"))) found = o;
        }
        return found;
    }

    static final int SETTLE_MS = 120;

    static GameServer serverOn(File file) {
        GameServer server = new GameServer(new Db.FileDb(file));
        server.race.start();
        return server;
    }

    // the claim answer comes back on the race worker, not on the calling thread
    static void settle(GameServer server) {
        for (int i = 0; i < 200 && server.race.pending() > 0; i++) nap(SETTLE_MS);
        nap(SETTLE_MS * 4);
    }

    static void nap(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static Net.Client login(GameServer server, String name, String group) {
        Net.Client c = Net.testClient(name);
        server.onMessage(c, "{\"cmd\":\"login\",\"name\":\"" + name + "\",\"group\":\"" + group + "\"}");
        return c;
    }

    public static void main(String[] args) throws Exception {

        section("1. PLAYER PROFILE in the database  (TODO 1)");
        {
            File f = File.createTempFile("players-test", ".txt");
            f.deleteOnExit(); f.delete();
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
                yes("load() reads back what visit() wrote", back != null && back.visits == 2 && "Nhom 1".equals(back.group),
                    back == null ? "load() returned null" : "visits = " + back.visits + ", group = " + back.group);
            }
        }

        section("2. LIFETIME WINS in the database  (TODO 2)");
        {
            File f = File.createTempFile("wins-test", ".txt");
            f.deleteOnExit(); f.delete();
            try (Db db = new Db.FileDb(f)) {
                Players p = new Players(db);
                yes("a group that never won -> 0", p.wins("Nhom 1") == 0, "wins() = " + p.wins("Nhom 1"));
                yes("first win -> 1", p.addWin("Nhom 1") == 1, "addWin() = " + p.wins("Nhom 1"));
                p.addWin("Nhom 1");
                yes("wins add up", p.wins("Nhom 1") == 2, "wins() = " + p.wins("Nhom 1"));
                yes("another group counts separately", p.wins("Nhom 2") == 0, "wins() = " + p.wins("Nhom 2"));
            }
        }

        section("3. THE LOGIN CARRIES THE DATA  (TODO 3)");
        {
            File f = File.createTempFile("login-test", ".txt");
            f.deleteOnExit(); f.delete();
            GameServer server = serverOn(f);
            Net.Client c = login(server, "An", "Nhom 1");
            JsonObject hello = lastOf(c, "login");
            yes("login answers", hello != null, "no login came back");
            yes("login carries visits", hello != null && hello.has("visits"), "no \"visits\" in the login");
            yes("login carries wins", hello != null && hello.has("wins"),
                "no \"wins\" in the login -- TODO 3 is the one line in handleLogin()");
            yes("a group that never won starts at 0", hello != null && hello.has("wins") && hello.get("wins").getAsLong() == 0,
                "wins = " + (hello != null && hello.has("wins") ? hello.get("wins").getAsString() : "?"));

            server.onMessage(c, "{\"cmd\":\"reset\"}");
            server.onMessage(c, "{\"cmd\":\"claim\"}");
            settle(server);
            Net.Client again = login(server, "An", "Nhom 1");
            JsonObject back = lastOf(again, "login");
            yes("the win is written down and read back at the next login",
                back != null && back.has("wins") && back.get("wins").getAsLong() == 1,
                "wins = " + (back != null && back.has("wins") ? back.get("wins").getAsString() : "?")
                + " -- TODO 3 saveWin(), called for you when a group wins");
        }

        section("4. ONE CLAIM, ONE ANSWER");
        {
            File f = File.createTempFile("claim-test", ".txt");
            f.deleteOnExit(); f.delete();
            GameServer server = serverOn(f);
            Net.Client a = login(server, "An", "Nhom 1");
            Net.Client b = login(server, "Binh", "Nhom 2");
            server.onMessage(a, "{\"cmd\":\"reset\"}");

            server.onMessage(a, "{\"cmd\":\"claim\"}");
            settle(server);
            JsonObject first = lastOf(a, "claim");
            yes("the first claim wins", first != null && first.get("win").getAsBoolean(),
                first == null ? "no claim answer" : "win = " + first.get("win").getAsString());

            server.onMessage(b, "{\"cmd\":\"claim\"}");
            settle(server);
            JsonObject second = lastOf(b, "claim");
            yes("the second claim loses", second != null && !second.get("win").getAsBoolean(),
                second == null ? "no claim answer" : "win = " + second.get("win").getAsString());
            yes("the loser is told who won", second != null && "Nhom 1".equals(Net.str(second, "winner")),
                second == null ? "no claim answer" : "winner = " + Net.str(second, "winner"));

            server.onMessage(a, "{\"cmd\":\"reset\"}");
            server.onMessage(b, "{\"cmd\":\"claim\"}");
            settle(server);
            JsonObject afterReset = lastOf(b, "claim");
            yes("reset opens a new race", afterReset != null && afterReset.get("win").getAsBoolean(),
                afterReset == null ? "no claim answer" : "win = " + afterReset.get("win").getAsString());
        }

        section("5. THE WHOLE ROOM CLAIMS AT ONCE  (what the bug looks like)");
        {
            File f = File.createTempFile("crowd-info", ".txt");
            f.deleteOnExit(); f.delete();
            GameServer server = serverOn(f);
            int winners = raceTogether(server, CROWD);
            if (winners == 1) info("exactly one winner -- TODO 4 looks done");
            else info(winners + " of " + CROWD + " groups were told they won"
                      + " -- every connection thread walked into the race at once");
        }

        section("6. EXACTLY ONE WINNER  (TODO 4)");
        {
            File f = File.createTempFile("crowd-test", ".txt");
            f.deleteOnExit(); f.delete();
            GameServer server = serverOn(f);
            int winners = raceTogether(server, CROWD);
            yes(CROWD + " groups claim at the same time -> exactly one wins", winners == 1,
                winners + " groups were told they won -- only one thread may touch the race");
        }

        System.out.println("\n========================================");
        System.out.println("  passed : " + passed);
        System.out.println("  failed : " + failed);
        int done  = failAt == 0 ? LEVEL_AT.length - 1 : failAt - 1;
        int level = LEVEL_AT[done];
        System.out.println("  level  : " + level + "/3");
        System.out.println("========================================");
        Report.sendResult(WORKSHOP, level, passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static int raceTogether(GameServer server, int crowd) throws Exception {
        List<Net.Client> clients = new ArrayList<>();
        for (int i = 1; i <= crowd; i++) clients.add(login(server, "P" + i, GROUP_PREFIX + i));
        server.onMessage(clients.get(0), "{\"cmd\":\"reset\"}");

        ExecutorService pool = Executors.newFixedThreadPool(crowd, daemon());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Net.Client c : clients) {
            futures.add(pool.submit(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                server.onMessage(c, "{\"cmd\":\"claim\"}");
            }));
        }
        go.countDown();
        for (Future<?> fut : futures) {
            try { fut.get(30, TimeUnit.SECONDS); } catch (Exception e) { info("a claim thread threw: " + e); }
        }
        pool.shutdownNow();
        settle(server);

        int winners = 0;
        for (Net.Client c : clients) {
            JsonObject answer = lastOf(c, "claim");
            if (answer != null && answer.has("win") && answer.get("win").getAsBoolean()) winners++;
        }
        return winners;
    }
}
