import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// The race for the prize: who won, and every group that was told it won.
public class Race {

    // What to do with each claim. GameServer.decide is plugged in here.
    public interface Decider { void decide(Registry.Player who); }

    static final long SLOW_MS = 300;

    private final Decider decider;
    private final BlockingQueue<Registry.Player> queue = new LinkedBlockingQueue<>();

    private String winner;
    private final List<String> told = new ArrayList<>();

    public Race(Decider decider) { this.decider = decider; }

    // Only the worker thread ever gets here, so the read, the wait and the write
    // cannot be cut into by anybody -- no lock needed.
    public void open() { winner = null; told.clear(); }
    public String winner() { return winner; }
    public int toldCount() { return told.size(); }
    public List<String> everyoneTold() { return new ArrayList<>(told); }

    // Did this group win? Read the winner, wait, then write it.
    public boolean take(String group) {
        String seen = winner;   // read:  has anybody won yet?
        slow();                 // pretend the server is busy right here
        if (seen != null) return false;
        winner = group;         // write: the winner is this group
        told.add(group);
        return true;
    }

    // Called on a connection thread: queue the player and return at once.
    public void submit(Registry.Player who) {
        queue.add(who);
    }

    // The one worker: take the next player off the queue and decide, forever.
    public void run() {
        while (true) {
            try {
                decider.decide(queue.take());
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // How many claims are waiting in the queue.
    public int pending() {
        return queue.size();
    }

    // Start the worker thread that runs run().
    public void start() {
        Thread worker = new Thread(this::run, "race-worker");
        worker.setDaemon(true);
        worker.start();
    }

    // Sleep SLOW_MS: stands in for a busy server.
    static void slow() {
        if (SLOW_MS <= 0) return;
        try {
            Thread.sleep(SLOW_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
