// TODO (PART 2): add a BlockingQueue as the queue, write submit() and run().
public class FightQueue {

    public interface Handler { void handle(Registry.Player who); }

    private static final String WORKER_NAME = "fight-worker";
    private final Handler handler;

    // TODO (PART 2): your queue goes here
    public FightQueue(Handler handler) { this.handler = handler; }

    public void start() {
        Thread t = new Thread(this::run, WORKER_NAME);
        t.setDaemon(true);
        t.start();
    }

    // TODO (PART 2): put the strike into the queue and return IMMEDIATELY (do not handle it here).
    public void submit(Registry.Player who) {
    }

    // TODO (PART 2): the endless worker loop: take ONE strike, handler.handle(strike), repeat.
    void run() {
    }

    // How many strikes are still waiting. TODO (PART 2) -- Tests.java uses it to wait for the queue to drain.
    public int pending() {
        return 0;
    }
}
