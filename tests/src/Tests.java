public class Tests {

    static int passed = 0, failed = 0;
    static void ok(String w){ passed++; System.out.println("  [ OK ]  " + w); }
    static void bad(String w, String why){ failed++; System.out.println("  [FAIL]  " + w + "\n          -> " + why); }
    static void eq(String w, long got, long want){
        if (got == want) ok(w + "  (" + got + ")");
        else bad(w, "nhan duoc " + got + ", mong doi " + want);
    }

    static void yes(String w, boolean c, String why){ if (c) ok(w); else bad(w, why); }
    static void section(String s){ System.out.println("\n--- " + s + " ---"); }

    static String last(Net.Client c, String cmd) {
        String found = null;
        for (String m : c.sent()) if (cmd.equals(Net.jsonString(m, "cmd"))) found = m;
        return found;
    }

    static int count(Net.Client c, String cmd) {
        int n = 0;
        for (String m : c.sent()) if (cmd.equals(Net.jsonString(m, "cmd"))) n++;
        return n;
    }

    static long num(String json, String field) {
        if (json == null) return -1;
        int i = json.indexOf("\"" + field + "\":");
        if (i < 0) return -1;
        i += field.length() + 3;
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
        return j == i ? -1 : Long.parseLong(json.substring(i, j));
    }

    static Net.Client call(GameServer server, String id, String name, String group) {
        Net.Client c = Net.testClient(id);
        server.onOpen(c);
        server.onMessage(c, "{\"cmd\":\"call\",\"name\":\"" + name + "\",\"group\":\"" + group + "\"}");
        return c;
    }

    public static void main(String[] args) throws Exception {

        section("1. TODO 1 -- goi -> welcome co TEN cua ban, chi nguoi goi nhan");
        {
            GameServer server = new GameServer();
            Net.Client a = call(server, "a", "An", "Nhom 1");
            String w = last(a, "welcome");
            yes("nguoi goi nhan duoc welcome", w != null, "khong co tin welcome -- chua lam handleCall()?");
            if (w != null) {
                yes("welcome co uid >= 1", num(w, "uid") >= 1, w);
                yes("welcome co dung ten", "An".equals(Net.jsonString(w, "name")), w);
                yes("welcome co nhom", "Nhom 1".equals(Net.jsonString(w, "group")), w);
            }
            Net.Client b = call(server, "b", "Binh", "Nhom 1");
            String w2 = last(b, "welcome");
            yes("nguoi thu hai co uid KHAC", w2 != null && num(w2, "uid") != num(w, "uid"), String.valueOf(w2));
            yes("nguoi thu hai KHONG nhan welcome cua nguoi thu nhat", count(b, "welcome") == 1, "nhan " + count(b, "welcome"));
        }

        section("2. TODO 2 -- thieu name -> error, khong welcome");
        {
            GameServer server = new GameServer();
            Net.Client x = Net.testClient("x");
            server.onOpen(x);
            server.onMessage(x, "{\"cmd\":\"call\"}");
            yes("thieu name -> co error", last(x, "error") != null, "sent=" + x.sent());
            yes("thieu name -> KHONG welcome", last(x, "welcome") == null, "sent=" + x.sent());
            server.onMessage(x, "{\"cmd\":\"call\",\"name\":\"\",\"group\":\"Nhom 9\"}");
            yes("name rong -> cung la error", count(x, "error") == 2 && last(x, "welcome") == null, "sent=" + x.sent());
            Net.Client y = Net.testClient("y");
            server.onOpen(y);
            server.onMessage(y, "{\"cmd\":\"bay\"}");
            yes("lenh la -> error (cho san)", last(y, "error") != null, "sent=" + y.sent());
        }

        section("3. Cho san -- so online, gui cho TAT CA, giam khi thoat");
        {
            GameServer server = new GameServer();
            Net.Client a = call(server, "a", "An", "Nhom 1");
            Net.Client b = call(server, "b", "Binh", "Nhom 2");
            String fa = last(a, "online"), fb = last(b, "online");
            yes("ca hai deu nhan online", fa != null && fb != null, "a=" + fa + " b=" + fb + " -- handleCall() co goi broadcastOnline()?");
            eq("online dem duoc 2", num(fa, "count"), 2);
            a.clearSent();
            server.onClose(b);
            eq("b thoat -> a thay 1", num(last(a, "online"), "count"), 1);
        }

        section("4. Cho san -- server tra loi TUNG nguoi");
        {
            GameServer server = new GameServer();
            Net.Client a = call(server, "a", "An", "Nhom 1");
            Net.Client b = call(server, "b", "Binh", "Nhom 2");
            a.clearSent(); b.clearSent();
            server.onMessage(a, "{\"cmd\":\"broadcast\"}");
            String ea = last(a, "broadcast"), eb = last(b, "broadcast");
            yes("ca hai deu nhan duoc", ea != null && eb != null, "a=" + ea + " b=" + eb);
            if (ea != null && eb != null) {
                yes("moi nguoi nhan uid cua CHINH MINH", num(ea, "uid") != num(eb, "uid"), ea + " | " + eb);
                yes("hai cau KHAC nhau", !Net.jsonString(ea, "text").equals(Net.jsonString(eb, "text")), ea);
            }
        }

        System.out.println("\n========================================");
        System.out.println("  DAT  / passed : " + passed);
        System.out.println("  HONG / failed : " + failed);
        System.out.println("========================================");
        System.exit(failed == 0 ? 0 : 1);
    }
}
