import java.util.*;

class MyHashMap {

    class Pair<U, V> {
        U first;
        V second;
        Pair(U first, V second) {
            this.first = first;
            this.second = second;
        }
    }

    class Bucket {
        private List<Pair<Integer, Integer>> bucket = new LinkedList<>();

        public Integer get(int key) {
            for(Pair<Integer, Integer> pair : bucket) {
                if(pair.first.equals(key)) {
                    return pair.second;
                }
            }
            return -1;
        }

        public void update(int key, int value) {
            boolean found = false;
            for(Pair<Integer, Integer> pair : bucket) {
                if(pair.first.equals(key)){
                    pair.second = value;
                    found = true;
                    break;
                }
            }
            if(!found) {
                bucket.add(new Pair<>(key, value));
            }
        }

        public void remove(int key) {
            Pair<Integer, Integer> delete = null;
            for(Pair<Integer, Integer> pair : bucket) {
                if(pair.first.equals(key)) {
                    delete = pair;
                    break;
                }
            }
            bucket.remove(delete);
        }
    }

    private static final int BUCKETS_SIZE = 2093; // prime number, reduce collison

    private Bucket[] buckets = new Bucket[BUCKETS_SIZE];
    public MyHashMap() {
        for(int i=0; i<buckets.length; i++) {
            buckets[i] = new Bucket();
        }
    }
    
    public void put(int key, int value) {
        int idx = key % BUCKETS_SIZE;
        Bucket bucket = buckets[idx];
        bucket.update(key, value);
    }
    
    public int get(int key) {
        int idx = key % BUCKETS_SIZE;
        Bucket bucket = buckets[idx];
        return bucket.get(key);
    }
    
    public void remove(int key) {
        int idx = key % BUCKETS_SIZE;
        Bucket bucket = buckets[idx];
        bucket.remove(key);
    }
}

/**
 * Your MyHashMap object will be instantiated and called as such:
 * MyHashMap obj = new MyHashMap();
 * obj.put(key,value);
 * int param_2 = obj.get(key);
 * obj.remove(key);
 */