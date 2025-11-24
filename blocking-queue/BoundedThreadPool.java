import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

public class BoundedThreadPool {

    private final BoundedBlockingQueue<Runnable> taskQueue;
    private final List<Worker> workers = new ArrayList<>();
    private volatile boolean isShutdown = false;

    public BoundedThreadPool(int poolSize, int queueCapacity) {
        this.taskQueue = new BoundedBlockingQueue<>(queueCapacity);
        for (int i = 0; i < poolSize; i++) {
            Worker w = new Worker("worker-" + i);
            workers.add(w);
            w.start();
        }
    }

    // 提交任务：这里用“阻塞”的 backpressure 版本
    public void submit(Runnable task) throws InterruptedException {
        if (task == null) throw new NullPointerException();
        if (isShutdown) {
            throw new RejectedExecutionException("ThreadPool is shutdown");
        }
        // 队列满了会阻塞，体现 bounded 的作用
        taskQueue.put(task);
    }

    // 如果你不想阻塞，可以做个 trySubmit，队列满了直接拒绝
    public boolean trySubmit(Runnable task) {
        if (task == null) throw new NullPointerException();
        if (isShutdown || taskQueue.isFull()) {
            return false; // 简单粗暴拒绝
        }
        try {
            taskQueue.put(task);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // 优雅关闭：不再接任务，等 worker 把队列里的任务消费完
    public void shutdown() {
        isShutdown = true;
        // 唤醒可能因 take() 阻塞的 worker
        for (Worker w : workers) {
            w.interrupt();
        }
    }

    // 等所有 worker 结束
    public void awaitTermination() throws InterruptedException {
        for (Worker w : workers) {
            w.join();
        }
    }

    private class Worker extends Thread {
        Worker(String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // 如果已经 shutdown 且队列为空，退出
                    if (isShutdown && taskQueue.isEmpty()) {
                        break;
                    }
                    Runnable task;
                    try {
                        task = taskQueue.take();  // 队列空会阻塞
                    } catch (InterruptedException e) {
                        // shutdown 时会 interrupt，把状态恢复一下，再检查条件
                        if (isShutdown && taskQueue.isEmpty()) {
                            break;
                        }
                        // 否则继续循环
                        continue;
                    }
                    try {
                        task.run();
                    } catch (Throwable t) {
                        // 生产级别这里要记录日志，这里简单吞掉
                        System.err.println(getName() + " error: " + t);
                    }
                }
            } finally {
                // worker 退出前可以做清理
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // 3 个 worker 线程，队列容量 5
        BoundedThreadPool pool = new BoundedThreadPool(3, 5);

        for (int i = 1; i <= 20; i++) {
            int id = i;
            pool.submit(() -> {
                System.out.println(Thread.currentThread().getName() +
                        " running task " + id);
                try {
                    Thread.sleep(500); // 模拟任务耗时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            System.out.println("submitted task " + id);
        }

        pool.shutdown();
        pool.awaitTermination();
        System.out.println("all tasks done");
    }
}

