package ua.iasa.searchsystem.core;

import java.util.Set;
import java.util.List;
import java.util.SortedSet;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.Objects;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class ConcurrentInvertedIndex implements InvertedIndex {
    record Node(int hash, String term, SortedSet<Integer> postings) implements Entry {}

    private static final int DEFAULT_INITIAL_CAPACITY = 1 << 5;
    private static final int MAXIMUM_INITIAL_CAPACITY = 1 << 30;
    private static final double LOAD_FACTOR = 0.75;

    private volatile LinkedList<Node>[] table;
    private final AtomicInteger size = new AtomicInteger(0);
    private final ReentrantLock[] strides = new ReentrantLock[1 << 3];
    private volatile int threshold;

    private final ReentrantLock resizeLock = new ReentrantLock();
    private final Condition resizeFinished = resizeLock.newCondition();
    private boolean resizing = false;

    public ConcurrentInvertedIndex() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public ConcurrentInvertedIndex(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be greater than 0.");
        }
        if (initialCapacity > MAXIMUM_INITIAL_CAPACITY) {
            initialCapacity = MAXIMUM_INITIAL_CAPACITY;
        }
        Arrays.setAll(strides, _ -> new ReentrantLock());
        threshold = tableSizeFor(initialCapacity);
    }

    @Override
    public void put(String term, int docId) {
        Objects.requireNonNull(term);

        if (table == null) {
            initTable();
        }

        int hash = hash(term);
        int bucketIdx;
        int strideIdx;

        resizeLock.lock();
        try {
            while (resizing) {
                resizeFinished.awaitUninterruptibly();
            }

            bucketIdx = bucketIndex(table, hash);
            strideIdx = bucketToStrideIndex(table, strides, bucketIdx);
            strides[strideIdx].lock();
        } finally {
            resizeLock.unlock();
        }

        try {
            if (table[bucketIdx] == null) {
                var newNode = new Node(hash, term, new TreeSet<>(Set.of(docId)));
                table[bucketIdx] = new LinkedList<>(List.of(newNode));
                size.incrementAndGet();
            } else {
                LinkedList<Node> bucket = table[bucketIdx];
                boolean termFound = false;
                for (Node node : bucket) {
                    if (node.term.equals(term)) {
                        termFound = true;
                        node.postings.add(docId);
                        break;
                    }
                }
                if (!termFound) {
                    var newNode = new Node(hash, term, new TreeSet<>(Set.of(docId)));
                    bucket.addLast(newNode);
                    size.incrementAndGet();
                }
            }
        } finally {
            strides[strideIdx].unlock();
        }

        if (size.get() > threshold) {
            resize();
        }
    }

    @Override
    public SortedSet<Integer> get(String term) {
        Objects.requireNonNull(term);

        if (table == null) {
            return null;
        }

        int hash = hash(term);
        int bucketIdx;
        int strideIdx;

        resizeLock.lock();
        try {
            while (resizing) {
                resizeFinished.awaitUninterruptibly();
            }

            bucketIdx = bucketIndex(table, hash);
            strideIdx = bucketToStrideIndex(table, strides, bucketIdx);
            strides[strideIdx].lock();
        } finally {
            resizeLock.unlock();
        }

        try {
            if (table[bucketIdx] != null) {
                LinkedList<Node> bucket = table[bucketIdx];
                for (Node node : bucket) {
                    if (node.term.equals(term)) {
                        return node.postings;
                    }
                }
            }
            return null;
        } finally {
            strides[strideIdx].unlock();
        }
    }

    private void initTable() {
        resizeLock.lock();
        try {
            if (table == null) {
                table = new LinkedList[threshold];
                threshold = (int) (table.length * LOAD_FACTOR);
            }
        } finally {
            resizeLock.unlock();
        }
    }

    private void resize() {
        resizeLock.lock();
        try {
            if (resizing) {
                return;
            }
            resizing = true;
        } finally {
            resizeLock.unlock();
        }

        for (ReentrantLock stride : strides) {
            stride.lock();
        }

        try {
            LinkedList<Node>[] nextTable = new LinkedList[table.length << 1];
            for (LinkedList<Node> bucket : table) {
                if (bucket != null) {
                    for (Node node : bucket) {
                        int nextTabBucketIdx = bucketIndex(nextTable, node.hash);
                        if (nextTable[nextTabBucketIdx] == null) {
                            nextTable[nextTabBucketIdx] = new LinkedList<>(List.of(node));
                        } else {
                            LinkedList<Node> nextTabBucket = nextTable[nextTabBucketIdx];
                            nextTabBucket.addLast(node);
                        }
                    }
                }
            }
            table = nextTable;
            threshold = (int) (table.length * LOAD_FACTOR);
        } finally {
            for (int i = strides.length - 1; i >= 0; i--) {
                strides[i].unlock();
            }

            resizeLock.lock();
            try {
                resizing = false;
                resizeFinished.signalAll();
            } finally {
                resizeLock.unlock();
            }
        }
    }

    @Override
    public int size() {
        return size.get();
    }

    static int tableSizeFor(int capacity) {
        int n = -1 >>> Integer.numberOfLeadingZeros(capacity - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_INITIAL_CAPACITY) ? MAXIMUM_INITIAL_CAPACITY : n + 1;
    }

    private static int hash(String term) {
        int h;
        return (h = term.hashCode()) ^ (h >>> 16);
    }

    private static int bucketIndex(LinkedList<Node>[] table, int hash) {
        return (table.length - 1) & hash;
    }

    private static int bucketToStrideIndex(LinkedList<Node>[] table, Object[] strides, int bucketIdx) {
        Objects.requireNonNull(table);
        if (table.length <= strides.length) {
            return bucketIdx;
        }
        return bucketIdx * strides.length / table.length;
    }
}
