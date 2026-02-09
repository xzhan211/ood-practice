import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Logger implements AutoCloseable {
    private final ConcurrentHashMap<String, Integer> lastPrinted = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleaner;
    private final int windowSeconds;
    private final int ttlSeconds;
    private final int cleanIntervalSeconds;

    public Logger() {
        this(10, 60, 10);
    }

    public Logger(int windowSeconds, int ttlSeconds, int cleanIntervalSeconds) {
        if(ttlSeconds < windowSeconds) {
            throw new IllegalArgumentException("ttlSeconds should be >= windowSeconds");
        }
        this.windowSeconds = windowSeconds;
        this.ttlSeconds = ttlSeconds;
        this.cleanIntervalSeconds = cleanIntervalSeconds;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "logger-rate-limiter-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::cleanup, cleanIntervalSeconds, cleanIntervalSeconds, TimeUnit.SECONDS);
    }

    public boolean shouldPrintMessage(int timestamp, String message) {
        AtomicBoolean ok = new AtomicBoolean(false);

        lastPrinted.compute(message, (k, lastTs) -> {
            if(lastTs == null || timestamp - lastTs >= windowSeconds) {
                ok.set(true);
                return timestamp;
            } else {
                ok.set(false);
                return lastTs;
            }
        });
        return ok.get();
    }

    private void cleanup() {
        int nowSec = (int) (System.currentTimeMillis() / 1000);
        
        for(Map.Entry<String, Integer> e : lastPrinted.entrySet()) {
            Integer lastTs = e.getValue();
            if(lastTs != null && nowSec - lastTs >= ttlSeconds) {
                lastPrinted.remove(e.getKey(), lastTs);
                System.out.println("clean: " + e.getKey());
            }
        }
    }

    @Override
    public void close() {
        cleaner.shutdownNow();
    }

    public static void main(String[] args) {
        Logger logger = new Logger(10, 30, 10);
        int cnt = 0;
        while(cnt < 100) {
            System.out.println(logger.shouldPrintMessage((int)(System.currentTimeMillis()/1000), "message_" + cnt++));
            try {
                Thread.sleep(2000);
            }catch(InterruptedException e) {
                System.out.println("sleep with error.");
            }
        }
    }
}