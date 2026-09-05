import game.Beast;
import game.GameLogic;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class Tests {

    static int passed = 0, failed = 0;
    static void ok(String w)              { passed++; System.out.println("  [ OK ]  " + w); }
    static void bad(String w, String why) { failed++; System.out.println("  [FAIL]  " + w + "\n          -> " + why); }
    static void eq(String w, long got, long want) {
        if (got == want) ok(w + "  (" + got + ")");
        else bad(w, "nhan duoc " + got + ", mong doi " + want);
    }

    static void yes(String w, boolean c, String why) { if (c) ok(w); else bad(w, why); }
    static void info(String w)            { System.out.println("  [INFO]  " + w); }
    static void section(String s){ System.out.println("\n--- " + s + " ---"); }
    static final String[] lastError = { "" };
    static ThreadFactory daemon() { return r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }; }

    private static final int BIG_CROWD = 200;
    static Beast bigBeast() { return GameLogic.conjure(Math.max(GameLogic.summonThreshold(), BIG_CROWD)); }
    static Registry.Player player(long uid) { return new Registry.Player(uid, "SV" + uid, "N", Net.testClient("t" + uid)); }

    public static void main(String[] args) throws Exception {

        section("1. Mot don danh  (PHAN 1 -- strike)");
        {
            BeastState st = new BeastState();
            Beast b = bigBeast();
            st.spawn(b);
            eq("mau ban dau", st.hp(), b.maxHp());
            BeastState.Hit h = st.strike(7, "An");
            if (h == null) bad("strike() tra ve Hit", "null -- chua lam BeastState.strike()?");
            else {
                eq("sat thuong = rollDamage(7, 1)", h.damage, GameLogic.rollDamage(7, 1));
                eq("mau con lai", st.hp(), b.maxHp() - h.damage);
                yes("chua chet", !h.killed, "chet sau 1 don?");
                yes("bang cong co An", !st.board(10).isEmpty() && "An".equals(st.board(10).get(0).name), "board = " + st.board(10).size() + " dong");
            }
        }

        section("2. Danh nhieu lan, nhieu nguoi  (PHAN 1)");
        {
            BeastState st = new BeastState();
            Beast b = bigBeast();
            st.spawn(b);
            long expect = 0;
            for (int i = 1; i <= 5; i++) expect += GameLogic.rollDamage(7, i);
            for (int i = 1; i <= 3; i++) expect += GameLogic.rollDamage(8, i);
            for (int i = 0; i < 5; i++) st.strike(7, "An");
            for (int i = 0; i < 3; i++) st.strike(8, "Binh");
            eq("don thu n cua TUNG uid dung rollDamage(uid, n)", st.hp(), b.maxHp() - expect);
            yes("chua ai ket lieu", st.slainByUid() == BeastState.NO_KILLER, "slainBy = " + st.slainByUid());
            yes("bang cong xep cao truoc", st.board(10).size() == 2 && st.board(10).get(0).total >= st.board(10).get(1).total, "board sai thu tu");
        }

        section("3. 20 luong x 50 don CUNG LUC, goi thang strike()  (PHAN 1 -- chi de XEM, khong tinh diem)");
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
                        try { st.strike(u, "SV" + u); }
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
                info("strike() nem " + lastError[0] + " " + threw + " lan -- HashMap bi hai luong sua CUNG LUC."
                   + "\n          Khong khoa, khong hang doi -> cau truc du lieu vo. PHAN 2 sua bang hang doi (test 4).");
            else if (dealt == expect)
                info("khong mat don nao (" + dealt + ") -- may 'may man' lan nay, hoac ban da khoa strike(). Chay lai vai lan.");
            else
                info("da tru " + dealt + ", dung ra " + expect + "  -> MAT " + (expect - dealt) + " sat thuong."
                   + "\n          Hai luong cung doc hp, cung tru, ghi de len nhau. Khong exception, khong log."
                   + "\n          PHAN 2 sua bang hang doi (test 4). O phan 1 thay dong nay la BINH THUONG.");
        }

        section("4. HANG DOI: 16 luong submit, MOT worker xu ly -> dung MOT nguoi ket lieu  (PHAN 2)");
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
            eq("hang da rong", q.pending(), 0);
            eq("so nguoi ket lieu", kills[0], 1);
            eq("mau cuoi cung = 0", st.hp(), 0);
            yes("co ghi nhan nguoi ket lieu", st.slainByUid() > 0, "slainByUid = " + st.slainByUid() + " -- submit()/run() chua lam?");
        }

        section("5. KY LUC trong database  (PHAN 2)");
        {
            File f = File.createTempFile("records-test", ".txt");
            f.deleteOnExit();
            f.delete();
            try (Db db = new Db.FileDb(f)) {
                Records r = new Records(db);
                yes("lan dau: lap ky luc", r.saveBest("An", 300), "saveBest tra ve false -- chua lam?");
                yes("thap hon: khong ghi de", !r.saveBest("An", 200), "ghi de ky luc thap hon");
                yes("cao hon: cap nhat", r.saveBest("An", 450), "khong cap nhat ky luc cao hon");
                r.saveBest("Binh", 400);
                List<Records.Entry> top = r.top(10);
                yes("top co 2 nguoi, An truoc", top.size() == 2 && "An".equals(top.get(0).name) && top.get(0).best == 450,
                    "top = " + top.size() + (top.isEmpty() ? "" : " dau = " + top.get(0).name + " " + top.get(0).best));
            }
            try (Db db = new Db.FileDb(f)) {
                List<Records.Entry> top = new Records(db).top(10);
                yes("mo lai database: ky luc van con", top.size() == 2, "mat du lieu sau khi mo lai");
            }
        }

        System.out.println("\n========================================");
        System.out.println("  DAT  / passed : " + passed);
        System.out.println("  HONG / failed : " + failed);
        System.out.println("========================================");
        System.exit(failed == 0 ? 0 : 1);
    }
}
