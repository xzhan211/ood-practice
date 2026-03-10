import java.util.*;

public class InMemoryDatabase {
    
    private final Map<String, String> store = new HashMap<>();
    private final Deque<Transaction> txStack = new ArrayDeque<>();

    public String get(String key) {
        for(Iterator<Transaction> it = txStack.descendingIterator(); it.hasNext(); ) {
            Transaction tx = it.next();
            if(tx.changes.containsKey(key)) {
                ValueEntry entry = tx.changes.get(key);
                return entry.deleted ? null : entry.value;
            }
        }
        return store.get(key);
    }

    public void set(String key, String value) {
        if(!txStack.isEmpty()) {
            txStack.peek().changes.put(key, ValueEntry.ofValue(value));
        } else {
            store.put(key, value);
        }
    }

    public void delete(String key) {
        if(!txStack.isEmpty()) {
            txStack.peek().changes.put(key, ValueEntry.deleted());
        } else {
            store.remove(key);
        }
    }

    public void begin() {
        txStack.push(new Transaction());
    }

    public void commit() {
        if(txStack.isEmpty()) {
            throw new IllegalStateException("NO TRANSACTION");
        }

        Transaction current = txStack.pop();

        if(!txStack.isEmpty()) {
            Transaction parent = txStack.peek();
            for(Map.Entry<String, ValueEntry> entry : current.changes.entrySet()) {
                parent.changes.put(entry.getKey(), entry.getValue());
            }
        } else {
            applyToStore(current);
        }
    }

    public void rollback() {
        if(txStack.isEmpty()) {
            throw new IllegalStateException("NO TRANSACTION");
        }
        txStack.pop();
    }

    private void applyToStore(Transaction tx) {
        for(Map.Entry<String, ValueEntry> entry : tx.changes.entrySet()) {
            String key = entry.getKey();
            ValueEntry valueEntry = entry.getValue();

            if(valueEntry.deleted) {
                store.remove(key);
            } else {
                store.put(key, valueEntry.value);
            }
        }
    }

    private static class Transaction {
        Map<String, ValueEntry> changes = new HashMap<>();
    }

    private static class ValueEntry {
        boolean deleted;
        String value;

        private ValueEntry(boolean deleted, String value) {
            this.deleted = deleted;
            this.value = value;
        }

        static ValueEntry ofValue(String value) {
            return new ValueEntry(false, value);
        }

        static ValueEntry deleted() {
            return new ValueEntry(true, null);
        }
    }

    public static void main(String[] args) {
        InMemoryDatabase db = new InMemoryDatabase();

        db.set("a", "10");
        System.out.println(db.get("a"));

        db.begin();
        db.set("a", "20");
        System.out.println(db.get("a"));
        db.rollback();
        System.out.println(db.get("a"));

        db.begin();
        db.set("a", "30");
        db.begin();
        db.set("a", "40");
        System.out.println(db.get("a"));
        db.commit();
        System.out.println(db.get("a"));
        db.rollback();
        System.out.println(db.get("a"));
    }
}
