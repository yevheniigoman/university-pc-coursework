package ua.iasa.searchsystem.core;

import java.util.*;

public class DefaultInvertedIndex implements InvertedIndex {
    record Node(int hash, String term, SortedSet<Integer> postings) implements Entry {}

    private static final int DEFAULT_INITIAL_CAPACITY = 32;
    private static final double LOAD_FACTOR = 0.75;

    private LinkedList<Node>[] table;
    private int threshold;
    private int size;

    public DefaultInvertedIndex() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public DefaultInvertedIndex(int initialCapacity) {
       threshold = initialCapacity;
       size = 0;
    }

    @Override
    public void put(String term, int docId) {
        Objects.requireNonNull(term);

        if (table == null) {
            resize();
        }

        int hash = hash(term);
        int index = (table.length - 1) & hash;

        if (table[index] == null) {
            var newNode = new Node(hash, term, new TreeSet<>(Set.of(docId)));
            table[index] = new LinkedList<>(List.of(newNode));
        } else {
            LinkedList<Node> bucket = table[index];
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
            }
        }

        if (++size > threshold) {
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
        int index = tableIndex(table, hash);

        if (table[index] != null) {
            LinkedList<Node> bucket = table[index];
            for (Node node : bucket) {
                if (node.term.equals(term)) {
                    return node.postings;
                }
            }
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    private static int hash(String term) {
        int h;
        return (h = term.hashCode()) ^ (h >>> 16);
    }

    private static int tableIndex(LinkedList<Node>[] table, int hash) {
        return (table.length - 1) & hash;
    }

    private void resize() {
        int newCap = (table == null) ? threshold : table.length << 1;
        int newThr = (int) (newCap * LOAD_FACTOR);

        LinkedList<Node>[] newTable = new LinkedList[newCap];

        if (table != null) {
            for (LinkedList<Node> oldBucket : table) {
                if (oldBucket != null) {
                    for (Node oldNode : oldBucket) {
                        var newNode = new Node(oldNode.hash, oldNode.term, oldNode.postings);
                        int newIndex = tableIndex(newTable, oldNode.hash);
                        if (newTable[newIndex] == null) {
                            newTable[newIndex] = new LinkedList<>(List.of(newNode));
                        } else {
                            LinkedList<Node> bucket = newTable[newIndex];
                            bucket.addLast(newNode);
                        }
                    }
                }
            }
        }

        table = newTable;
        threshold = newThr;
    }
}
