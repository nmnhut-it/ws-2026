import com.google.gson.JsonObject;

public class Tests {

    static final int   WORKSHOP = 1;
    static final int[] LEVEL_AT = { 0, 1, 2, 3, 3, 3 };

    static int passed = 0, failed = 0, part = 0, failAt = 0;
    static void ok(String w){ passed++; System.out.println("  [ OK ]  " + w); }
    static void bad(String w, String why){ failed++; if (failAt == 0) failAt = part;
        System.out.println("  [FAIL]  " + w + "\n          -> " + why); }
    static void eq(String w, long got, long want){
        if (got == want) ok(w + "  (" + got + ")");
        else bad(w, "got " + got + ", expected " + want);
    }

    static void yes(String w, boolean c, String why){ if (c) ok(w); else bad(w, why); }
    static void section(String s){ part++; System.out.println("\n--- " + s + " ---"); }

    static JsonObject last(Net.Client c, String cmd) {
        JsonObject found = null;
        for (String m : c.sent()) {
            JsonObject o = Net.json(m);
            if (cmd.equals(Net.str(o, "cmd"))) found = o;
        }
        return found;
    }

    static int count(Net.Client c, String cmd) {
        int n = 0;
        for (String m : c.sent()) if (cmd.equals(Net.str(Net.json(m), "cmd"))) n++;
        return n;
    }

    static long num(JsonObject o, String field) {
        if (o == null || o.get(field) == null || o.get(field).isJsonNull()) return -1;
        return o.get(field).getAsLong();
    }

    static Net.Client login(GameServer server, String id, String name, String group) {
        Net.Client c = Net.testClient(id);
        server.onOpen(c);
        JsonObject packet = new JsonObject();
        packet.addProperty("cmd", "login");
        packet.addProperty("name", name);
        packet.addProperty("group", group);
        server.onMessage(c, packet.toString());
        return c;
    }

    public static void main(String[] args) throws Exception {

        section("1. TODO 1 -- login -> login with YOUR name, only the caller gets it");
        {
            GameServer server = new GameServer();
            Net.Client a = login(server, "a", "An", "Nhom 1");
            JsonObject w = last(a, "login");
            yes("the caller gets an answer", w != null, "nothing came back -- handleLogin() not written yet?");
            if (w != null) {
                yes("the answer carries uid >= 1", num(w, "uid") >= 1, String.valueOf(w));
                yes("the answer carries the right name", "An".equals(Net.str(w, "name")), String.valueOf(w));
                yes("the answer carries the group", "Nhom 1".equals(Net.str(w, "group")), String.valueOf(w));
            }
            Net.Client b = login(server, "b", "Binh", "Nhom 1");
            JsonObject w2 = last(b, "login");
            yes("the second caller gets a DIFFERENT uid", w2 != null && num(w2, "uid") != num(w, "uid"), String.valueOf(w2));
            yes("the second caller does NOT get the first answer", count(b, "login") == 1, "got " + count(b, "login"));
        }

        section("2. TODO 2 -- no name or no group -> error, no login");
        {
            GameServer server = new GameServer();
            Net.Client x = Net.testClient("x");
            server.onOpen(x);
            server.onMessage(x, "{\"cmd\":\"login\"}");
            yes("no name -> an error comes back", last(x, "error") != null, "sent=" + x.sent());
            yes("no name -> NO login answer", last(x, "login") == null, "sent=" + x.sent());
            server.onMessage(x, "{\"cmd\":\"login\",\"name\":\"\",\"group\":\"Nhom 9\"}");
            yes("empty name -> also an error", count(x, "error") == 2 && last(x, "login") == null, "sent=" + x.sent());
            server.onMessage(x, "{\"cmd\":\"login\",\"name\":\"An\"}");
            yes("no group -> also an error", count(x, "error") == 3 && last(x, "login") == null, "sent=" + x.sent());
            Net.Client y = Net.testClient("y");
            server.onOpen(y);
            server.onMessage(y, "{\"cmd\":\"bay\"}");
            yes("unknown cmd -> error (given code)", last(y, "error") != null, "sent=" + y.sent());
        }

        section("3. TODO 3 + 4 -- the session: getUserInfo answers, logout ends it");
        {
            GameServer server = new GameServer();
            Net.Client a = login(server, "a", "An", "Nhom 1");
            long uid = num(last(a, "login"), "uid");
            a.clearSent();
            server.onMessage(a, "{\"cmd\":\"getUserInfo\"}");
            JsonObject who = last(a, "getUserInfo");
            yes("getUserInfo answers", who != null, "sent=" + a.sent() + " -- handleGetUserInfo() not written yet?");
            if (who != null) {
                yes("getUserInfo knows the name, and the getUserInfo packet never carried it",
                    "An".equals(Net.str(who, "name")), String.valueOf(who));
                yes("getUserInfo knows the group", "Nhom 1".equals(Net.str(who, "group")), String.valueOf(who));
                yes("getUserInfo repeats the uid the login gave out", num(who, "uid") == uid,
                    "login uid=" + uid + " getUserInfo=" + who);
                yes("getUserInfo carries seconds >= 0", num(who, "seconds") >= 0, String.valueOf(who));
            }

            Net.Client x = Net.testClient("x");
            server.onOpen(x);
            server.onMessage(x, "{\"cmd\":\"getUserInfo\"}");
            yes("getUserInfo before login -> error", last(x, "error") != null, "sent=" + x.sent());
            yes("getUserInfo before login -> NO getUserInfo", last(x, "getUserInfo") == null, "sent=" + x.sent());

            a.clearSent();
            server.onMessage(a, "{\"cmd\":\"logout\"}");
            yes("logout answers", last(a, "logout") != null, "sent=" + a.sent() + " -- handleLogout() not written yet?");
            a.clearSent();
            server.onMessage(a, "{\"cmd\":\"getUserInfo\"}");
            yes("after logout the session is gone -> error", last(a, "error") != null, "sent=" + a.sent());
            yes("after logout getUserInfo answers nothing else", last(a, "getUserInfo") == null, "sent=" + a.sent());
            server.onMessage(a, "{\"cmd\":\"logout\"}");
            yes("logout twice -> error, no crash", count(a, "error") >= 2, "sent=" + a.sent());
        }

        section("4. Given code -- online count, sent to EVERYONE, drops on leave");
        {
            GameServer server = new GameServer();
            Net.Client a = login(server, "a", "An", "Nhom 1");
            Net.Client b = login(server, "b", "Binh", "Nhom 2");
            JsonObject fa = last(a, "online"), fb = last(b, "online");
            yes("both get an online message", fa != null && fb != null, "a=" + fa + " b=" + fb + " -- does handleLogin() call broadcastOnline()?");
            eq("online counts 2", num(fa, "count"), 2);
            a.clearSent();
            server.onClose(b);
            eq("b leaves -> a sees 1", num(last(a, "online"), "count"), 1);
        }

        section("5. Given code -- the server answers EACH client");
        {
            GameServer server = new GameServer();
            Net.Client a = login(server, "a", "An", "Nhom 1");
            Net.Client b = login(server, "b", "Binh", "Nhom 2");
            a.clearSent(); b.clearSent();
            server.onMessage(a, "{\"cmd\":\"broadcast\"}");
            JsonObject ea = last(a, "broadcast"), eb = last(b, "broadcast");
            yes("both get an answer", ea != null && eb != null, "a=" + ea + " b=" + eb);
            if (ea != null && eb != null) {
                yes("each one gets ITS OWN uid", num(ea, "uid") != num(eb, "uid"), ea + " | " + eb);
                yes("the two texts DIFFER", !Net.str(ea, "text").equals(Net.str(eb, "text")), String.valueOf(ea));
            }
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
