package ua.iasa.searchsystem.core;

import java.util.Set;
import java.util.List;
import java.util.SortedSet;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.Objects;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;

public class AdvancedConcurrentInvertedIndex implements InvertedIndex {
    record Node(int hash, String term, SortedSet<Integer> postings) implements Entry {}
    record ResizeState(LinkedList<Node>[] tab, LinkedList<Node>[] nextTab, AtomicInteger nextIdx, CountDownLatch latch) {}

    private static final int DEFAULT_INITIAL_CAPACITY = 1 << 5;
    private static final int MAXIMUM_INITIAL_CAPACITY = 1 << 30;
    private static final int NUM_STRIDES = 1 << 7;
    private static final double LOAD_FACTOR = 0.75;

    private volatile LinkedList<Node>[] table;
    private final AtomicInteger size = new AtomicInteger(0);
    private final Lock[] strides = new ReentrantLock[NUM_STRIDES];
    private volatile int threshold;

    private final Lock resizeLock = new ReentrantLock();
    private volatile ResizeState resizeState;

    public AdvancedConcurrentInvertedIndex() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public AdvancedConcurrentInvertedIndex(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be greater than 0.");
        }

        if (initialCapacity < NUM_STRIDES) {
            initialCapacity = NUM_STRIDES;
        } else if (initialCapacity > MAXIMUM_INITIAL_CAPACITY) {
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
        int strideIdx = strideIndex(hash);

        ResizeState state = resizeState;
        if (state != null) {
            helpResize(state);
        }

        strides[strideIdx].lock();
        try {
            int bucketIdx = bucketIndex(table, hash);
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

        if (resizeState == null && size.get() > threshold) {
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
        int strideIdx = strideIndex(hash);

        ResizeState state = resizeState;
        if (state != null) {
            helpResize(state);
        }

        strides[strideIdx].lock();
        try {
            int bucketIdx = bucketIndex(table, hash);
            if (table[bucketIdx] != null) {
                LinkedList<Node> bucket = table[bucketIdx];
                for (Node node : bucket) {
                    if (node.term.equals(term)) {
                        return new TreeSet<>(node.postings);
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
        LinkedList<Node>[] tab = table;
        if (resizeState != null) {
            return;
        }

        for (var stride : strides) {
            stride.lock();
        }

        try {
            if (tab != table || resizeState != null || size.get() <= threshold) {
                return;
            }
            LinkedList<Node>[] nextTab = new LinkedList[table.length << 1];
            var latch = new CountDownLatch(table.length);
            var nextIdx = new AtomicInteger(0);
            resizeState = new ResizeState(table, nextTab, nextIdx, latch);

            ResizeState state = resizeState;
            helpResize(state);

            boolean interrupted = false;
            while (true) {
                try {
                    state.latch.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            threshold = (int) (state.nextTab.length * LOAD_FACTOR);
            table = state.nextTab;
        } finally {
            resizeState = null;

            for (int i = strides.length - 1; i >= 0; i--) {
                strides[i].unlock();
            }
        }
    }

    private void helpResize(ResizeState state) {
        int bucketIdx;
        while ((bucketIdx = state.nextIdx.getAndIncrement()) < state.tab.length) {
            LinkedList<Node> bucket = state.tab[bucketIdx];
            if (bucket != null) {
                for (Node node : bucket) {
                    int newBucketIdx = bucketIndex(state.nextTab, node.hash);
                    if (state.nextTab[newBucketIdx] == null) {
                        state.nextTab[newBucketIdx] = new LinkedList<>();
                    }
                    state.nextTab[newBucketIdx].addLast(node);
                }
            }
            state.latch.countDown();
        }
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void clear() {}

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

    private int strideIndex(int hash) {
        return (strides.length - 1) & hash;
    }
}
