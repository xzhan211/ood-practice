import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobScheduler implements AutoCloseable {

    public static final class Handle {
        final UUID id = UUID.randomUUID();
        @Override public String toString() { return id.toString(); }
    }

    private static final class Job implements Delayed {
        final Handle handle;
        final Runnable fn;
        final long periodNanos;               // periodic interval
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile long nextRunNanos;           // absolute nanoTime

        Job(Handle h, Runnable fn, long firstRunNanos, long periodNanos) {
            this.handle = h;
            this.fn = fn;
            this.nextRunNanos = firstRunNanos;
            this.periodNanos = periodNanos;
        }

        @Override public long getDelay(TimeUnit unit) {
            return unit.convert(nextRunNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override public int compareTo(Delayed o) {
            return Long.compare(this.nextRunNanos, ((Job) o).nextRunNanos);
        }
    }

    private final DelayQueue<Job> q = new DelayQueue<>();
    private final ConcurrentHashMap<UUID, Job> live = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    public JobScheduler() {
        worker = new Thread(this::loop, "scheduler");
        worker.setDaemon(true);
        worker.start();
    }

    // schedule by Hz (e.g. 50Hz => 20ms)
    public Handle schedule(double hz, Runnable fn) {
        if (hz <= 0) throw new IllegalArgumentException("hz must be > 0");
        if (fn == null) throw new NullPointerException("fn");

        long periodNanos = Math.max(1L, Math.round(1_000_000_000.0 / hz));
        Handle h = new Handle();
        long first = System.nanoTime() + periodNanos;

        Job job = new Job(h, fn, first, periodNanos);
        live.put(h.id, job);
        q.put(job);
        return h;
    }

    public boolean cancel(Handle h) {
        if (h == null) return false;
        Job job = live.remove(h.id);
        if (job == null) return false;
        job.cancelled.set(true);
        q.remove(job); // best-effort
        return true;
    }

    private void loop() {
        while (running.get()) {
            try {
                Job job = q.take(); // blocks until due
                if (job.cancelled.get() || !live.containsKey(job.handle.id)) continue;

                // execute in scheduler thread (simple)
                try { job.fn.run(); }
                catch (Throwable t) { System.err.println("job error: " + t); }

                // reschedule (fixed-rate-ish, avoids drift)
                job.nextRunNanos += job.periodNanos;
                long now = System.nanoTime();
                if (job.nextRunNanos < now) {
                    long missed = (now - job.nextRunNanos) / job.periodNanos + 1;
                    job.nextRunNanos += missed * job.periodNanos;
                }

                if (!job.cancelled.get() && live.containsKey(job.handle.id)) q.put(job);

            } catch (InterruptedException e) {
                // shutdown
                if (!running.get()) return;
            }
        }
    }

    @Override public void close() {
        running.set(false);
        worker.interrupt();
    }

    // Demo: proves it runs
    public static void main(String[] args) throws Exception {
        try (JobScheduler s = new JobScheduler()) {
            Handle h = s.schedule(50, () -> System.out.println("tick " + System.currentTimeMillis()));
            Thread.sleep(300);
            System.out.println("cancel");
            s.cancel(h);
            Thread.sleep(200);
        }
    }
}
