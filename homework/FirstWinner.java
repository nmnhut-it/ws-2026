import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
// Homework: exactly one first winner, without synchronized and without a queue.
// Run it from the w2 folder: java homework/FirstWinner.java   (JDK 17+)
public class FirstWinner {

    // The race: who claimed first. Many threads call claim() at the same moment.
    static final class Race {
        private String winner;   // TODO -- you may change the type of this field

        // TODO -- make this correct WITHOUT synchronized, a Lock, a Semaphore or a queue.
        // Right now it reads, waits, then writes: every thread that reads inside the wait wins.
        boolean claim(String who) {
            String seen = winner;
            pause();
            if (seen != null) return false;
            winner = who;
            return true;
        }

        String winner() { return winner; }
    }
    // ---- given: the test. Do not change anything below. ----
    static final int THREADS = 12, ROUNDS = 20, PAUSE_MS = 20;
    static final String[] FORBIDDEN = { "synchronized", "Lock", "Semaphore", "Queue", "newSingleThreadExecutor" };
    static final String[] SOURCES = { "homework/FirstWinner.java", "FirstWinner.java" };
    static final String GIVEN_MARK = "// ---- given:";

    // A busy server: the gap between reading and writing.
    static void pause() {
        try { Thread.sleep(PAUSE_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // One round: THREADS players claim at once. Returns how many were told they won.
    static int round(int n) throws InterruptedException {
        Race race = new Race();
        CountDownLatch go = new CountDownLatch(1);
        List<String> winners = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String who = "player-" + n + "-" + i;
            Thread t = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                if (race.claim(who)) winners.add(who);
            });
            threads.add(t); t.start();
        }
        go.countDown();
        for (Thread t : threads) t.join();
        if (winners.size() == 1 && !winners.get(0).equals(race.winner()))
            System.out.println("  [FAIL]  round " + n + ": told " + winners.get(0) + " it won, but winner() says " + race.winner());
        return winners.size();
    }

    // The part above the given test must not use the tools this homework rules out.
    static String forbiddenWord() throws Exception {
        for (String path : SOURCES) {
            Path p = Path.of(path);
            if (!Files.exists(p)) continue;
            String mine = Files.readString(p).split(GIVEN_MARK)[0].replaceAll("//[^\n]*", "").replaceAll("(?s)/\\*.*?\\*/", "");
            for (String word : FORBIDDEN) if (mine.contains(word)) return word;
            return null;
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        String word = forbiddenWord();
        if (word != null) { System.out.println("  [FAIL]  your code uses " + word + " -- find another way"); System.exit(1); }
        int bad = 0;
        for (int n = 1; n <= ROUNDS; n++) {
            int told = round(n);
            if (told != 1) { bad++; System.out.println("  [FAIL]  round " + n + ": " + told + " players were told they won"); }
        }
        System.out.println(bad == 0 ? "\n  PASS  exactly one winner in all " + ROUNDS + " rounds" : "\n  " + bad + " of " + ROUNDS + " rounds were wrong");
        System.exit(bad == 0 ? 0 : 1);
    }
}