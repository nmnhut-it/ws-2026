import com.google.gson.JsonObject;
import java.io.File;

public class Tests {

    static final int   WORKSHOP = 2;
    static final int[] LEVEL_AT = { 0, 1, 2, 3 };

    static int passed = 0, failed = 0, part = 0, failAt = 0;
    static void ok(String w)              { passed++; System.out.println("  [ OK ]  " + w); }
    static void bad(String w, String why) { failed++; if (failAt == 0) failAt = part;
        System.out.println("  [FAIL]  " + w + "\n          -> " + why); }
    static void yes(String w, boolean c, String why) { if (c) ok(w); else bad(w, why); }
    static void section(String s){ part++; System.out.println("\n--- " + s + " ---"); }

    static JsonObject lastOf(Net.Client c, String cmd) {
        JsonObject found = null;
        for (String m : c.sent()) {
            JsonObject o = Net.json(m);
            if (cmd.equals(Net.str(o, "cmd"))) found = o;
        }
        return found;
    }

    static File freshFile(String name) throws Exception {
        File f = File.createTempFile(name, ".txt");
        f.deleteOnExit(); f.delete();
        return f;
    }

    static long groupVisitsOf(GameServer server, String name, String group) {
        Net.Client c = Net.testClient(name);
        server.onMessage(c, "{\"cmd\":\"login\",\"name\":\"" + name + "\",\"group\":\"" + group + "\"}");
        JsonObject hello = lastOf(c, "login");
        return hello != null && hello.has("groupVisits") ? hello.get("groupVisits").getAsLong() : -1;
    }

    public static void main(String[] args) throws Exception {

        section("1. PLAYER PROFILE in the database  (TODO 1)");
        try (Db db = new Db.FileDb(freshFile("players-test"))) {
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

        section("2. GROUP VISITS in the database  (TODO 2)");
        try (Db db = new Db.FileDb(freshFile("group-test"))) {
            Players p = new Players(db);
            yes("a group that never logged in -> 0", p.groupVisits("Nhom 1") == 0, "groupVisits() = " + p.groupVisits("Nhom 1"));
            yes("first login -> 1", p.addGroupVisit("Nhom 1") == 1, "addGroupVisit() = " + p.groupVisits("Nhom 1"));
            p.addGroupVisit("Nhom 1");
            yes("logins add up", p.groupVisits("Nhom 1") == 2, "groupVisits() = " + p.groupVisits("Nhom 1"));
            yes("another group counts separately", p.groupVisits("Nhom 2") == 0, "groupVisits() = " + p.groupVisits("Nhom 2"));
        }

        section("3. THE LOGIN CARRIES THE GROUP'S COUNT  (TODO 3)");
        {
            File f = freshFile("login-test");
            GameServer server = new GameServer(new Db.FileDb(f));
            long first = groupVisitsOf(server, "An", "Nhom 1");
            yes("the login carries groupVisits", first >= 0,
                "no \"groupVisits\" in the login -- TODO 3: the line in handleLogin() and groupVisit()");
            yes("the first login of a group -> 1", first == 1, "groupVisits = " + first);
            yes("a second member of the group -> 2", groupVisitsOf(server, "Binh", "Nhom 1") == 2,
                "every login of the group adds one, whoever logs in");
            yes("another group starts at 1", groupVisitsOf(server, "Chi", "Nhom 2") == 1, "one key per group: GROUP_PREFIX + group");
            GameServer restarted = new GameServer(new Db.FileDb(f));
            yes("after a restart the count goes on", groupVisitsOf(restarted, "An", "Nhom 1") == 3,
                "the count must come out of the database, not out of memory");
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
}