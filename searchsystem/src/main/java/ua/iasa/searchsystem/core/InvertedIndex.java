package ua.iasa.searchsystem.core;

import java.util.SortedSet;

public interface InvertedIndex {
    void put(String term, int docId);
    SortedSet<Integer> get(String term);
    int size();

    interface Entry {
        String term();
        SortedSet<Integer> postings();
    }
}
