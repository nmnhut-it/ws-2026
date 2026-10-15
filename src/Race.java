import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

// TODO 4  submit() / run()   one queue, one worker: only that worker touches the race
public class Race {

    // What to do with each claim. GameServer.decide is plugged in here.
    public interface Decider { void decide(Registry.Player who); }

    // A real server is busy before it can answer: it reads a database, calls another service, or the
    // machine is loaded. SLOW_MS stands in for that. Do not delete it -- the bug is not the waiting,
    // the bug is how many threads touch the race while somebody is waiting.
    static final long SLOW_MS = 300;

    private final Decider decider;

    private String winner;
    private final List<String> told = new ArrayList<>();

    public Race(Decider decider) { this.decider = decider; }

    // ---- the race itself. Only the worker thread is allowed in here. ----

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

    // ---- the queue ----

    // TODO 4 -- ONE QUEUE, ONE WORKER.
    // Right now submit() decides on the spot, so every connection thread walks into take() at once
    // and every group is told it won.
    // Add a BlockingQueue<Registry.Player> here. submit() puts the player in it and returns straight
    // away. run() is the loop of ONE worker: take() a player off the queue, then decider.decide(it).
    // When one thread owns the race, nobody can read a winner that is about to change.
    public void submit(Registry.Player who) {
        decider.decide(who);
    }

    // The one worker: take the next player off the queue and decide, forever.
    public void run() {
    }

    // How many claims are waiting in the queue.
    public int pending() {
        return 0;
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
