import java.util.*;

enum Event {START, END}
record Log (long threadId, String taskId, Event event, long ts) {}

public class CpuTimeCalculator {
    
    private static final class Result {
        Map<String, Long> inclusive = new HashMap<>();
        Map<String, Long> exclusive = new HashMap<>();
    }

    private static final class Frame {
        private final String taskId;
        private final long startTs;
        private long lastTs;
        Frame(String taskId, long ts) {
            this.taskId = taskId;
            this.startTs = ts;
            this.lastTs = ts;
        }
        void setLastTs(long ts) {
            lastTs = ts;
        }
    }

    public Result compute(List<Log> logs) {
        Result result = new Result();
        Map<Long, Deque<Frame>> stacks = new HashMap<>();
        for(Log log : logs) {
            Deque<Frame> stack = stacks.computeIfAbsent(log.threadId(), k -> new ArrayDeque<>());
            if (log.event() == Event.START) {
                if(!stack.isEmpty()) {
                    Frame top = stack.peek();
                    add(result.exclusive, top.taskId, log.ts() - top.lastTs);
                } 
                stack.push(new Frame(log.taskId(), log.ts()));
            } else {
                if(stack.isEmpty()) throw new IllegalStateException("END without START");
                Frame top = stack.pop();
                if(!top.taskId.equals(log.taskId())) throw new IllegalStateException("task ID is different between END and START");
                add(result.exclusive, log.taskId(), log.ts() - top.lastTs);
                add(result.inclusive, log.taskId(), log.ts() - top.startTs);
                if(!stack.isEmpty()) {
                    stack.peek().setLastTs(log.ts());
                }
            }
        }
        return result;
    }

    private void add(Map<String, Long> map, String key, long delta) {
        map.put(key, map.getOrDefault(key, 0L) + delta);
    }



    public static void main(String[] args) {
        CpuTimeCalculator calculator = new CpuTimeCalculator();
        List<Log> logs = List.of(
            new Log(1, "A", Event.START, 0),
            new Log(1, "B", Event.START, 3),
            new Log(1, "B", Event.END, 6),
            new Log(1, "A", Event.END, 9),

            new Log(2, "C", Event.START, 2),
            new Log(2, "C", Event.END, 5)
        );

        Result res = calculator.compute(logs);
        System.out.println("inclusive: " + res.inclusive);
        System.out.println("exclusive: " + res.exclusive);

        /*
            inclusive: {A=9, B=3, C=3}
            exclusive: {A=6, B=3, C=3}
        */
    }
}
