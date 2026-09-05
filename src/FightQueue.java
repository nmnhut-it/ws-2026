import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FightQueue {

    public interface Handler { void handle(Registry.Player who); }

    private static final String WORKER_NAME = "fight-worker";
    private final Handler handler;
    private final BlockingQueue<Registry.Player> queue = new LinkedBlockingQueue<>();

    public FightQueue(Handler handler) { this.handler = handler; }

    public void start() {
        Thread t = new Thread(this::run, WORKER_NAME);
        t.setDaemon(true);
        t.start();
    }

    public void submit(Registry.Player who) {
        queue.add(who);
    }

    void run() {
        while (true) {
            try {
                Registry.Player who = queue.take();
                try { handler.handle(who); }
                catch (RuntimeException e) { System.out.println("[queue] handler nem: " + e); }
            } catch (InterruptedException e) { return; }
        }
    }

    public int pending() { return queue.size(); }
}
