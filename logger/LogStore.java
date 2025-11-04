import java.util.*;

/** 日志级别 */
enum Level { DEBUG, INFO, WARN, ERROR }

/** 结构化日志 */
final class LogEntry {
    final long ts;           // 时间戳（long）
    final Level level;       // 级别
    final String message;    // 原始消息

    LogEntry(long ts, Level level, String message) {
        this.ts = ts;
        this.level = Objects.requireNonNull(level);
        this.message = Objects.requireNonNull(message);
    }
    @Override public String toString() {
        return ts + " " + level + " " + message;
    }
}

/** 简单日志存储 + 时间范围查询 */
public class LogStore {
    // 时间索引：同一时间戳可能多条，存 List
    private final TreeMap<Long, List<LogEntry>> byTime = new TreeMap<>();

    /** 方式一：直接传字段 */
    public void append(long ts, Level level, String message) {
        byTime.computeIfAbsent(ts, k -> new ArrayList<>())
              .add(new LogEntry(ts, level, message));
    }

    /** 方式二：解析原始行：格式约定为 "ts|level|message" */
    public void appendRaw(String line) {
        // 示例： 1720000123456|INFO|order placed: #123
        String[] parts = line.split("\\|", 3);
        if (parts.length < 3) return; // 简单容错：丢弃坏行
        long ts = Long.parseLong(parts[0].trim());
        Level lv = Level.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
        String msg = parts[2];
        append(ts, lv, msg);
    }

    /** 按时间范围查询：[start, end]，可选级别过滤（levels 传 null 表示不过滤） */
    public List<LogEntry> query(long startInclusive, long endInclusive, Set<Level> levels) {
        if (startInclusive > endInclusive) return List.of();
        NavigableMap<Long, List<LogEntry>> view =
                byTime.subMap(startInclusive, true, endInclusive, true);

        List<LogEntry> out = new ArrayList<>();
        boolean filter = (levels != null && !levels.isEmpty());
        for (List<LogEntry> bucket : view.values()) {
            if (!filter) {
                out.addAll(bucket);
            } else {
                for (LogEntry e : bucket) if (levels.contains(e.level)) out.add(e);
            }
        }
        // 已按时间有序；如需同一时间再按 level/message，可在这里再排一次
        return out;
    }

    public static void main(String[] args) {
        LogStore ls = new LogStore();

        // 直接字段方式
        ls.append(1000L, Level.INFO, "app start");
        ls.append(1010L, Level.WARN, "slow query");
        ls.append(1010L, Level.ERROR, "db down");
        // 原始行解析方式
        ls.appendRaw("1020|INFO|retry connect");
        ls.appendRaw("1030|DEBUG|heartbeat ok");

        // 查询 [1005, 1025] 且只要 WARN/ERROR
        Set<Level> lv = EnumSet.of(Level.WARN, Level.ERROR);
        List<LogEntry> r1 = ls.query(1005, 1025, lv);
        System.out.println("Q1: " + r1);

        // 查询全范围（不过滤级别）
        List<LogEntry> r2 = ls.query(0, Long.MAX_VALUE, null);
        System.out.println("Q2: " + r2);
    }
}

