// TODO 4  queue + one worker: add a BlockingQueue, write submit(), run(), pending()
public class FightQueue {

    public interface Handler { void handle(Registry.Player who); }

    private static final String WORKER_NAME = "fight-worker";
    private final Handler handler;

    // TODO 4 -- your queue goes here
    public FightQueue(Handler handler) { this.handler = handler; }

    public void start() {
        Thread t = new Thread(this::run, WORKER_NAME);
        t.setDaemon(true);
        t.start();
    }

    // TODO 4 -- PUT IT IN THE QUEUE and return NOW. Do not handle the strike here.
    public void submit(Registry.Player who) {
    }

    // TODO 4 -- WORKER LOOP: take ONE strike out, call handler.handle(it), forever.
    void run() {
    }

    // TODO 4 -- how many strikes are still waiting. Tests.java waits for this to reach 0.
    public int pending() {
        return 0;
    }
}
