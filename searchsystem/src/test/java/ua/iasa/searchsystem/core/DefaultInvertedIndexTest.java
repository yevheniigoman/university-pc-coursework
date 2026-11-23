package ua.iasa.searchsystem.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.SortedSet;

public class DefaultInvertedIndexTest {
    @Test
    public void whenThresholdExceeds_thenResizeWithPreservingExistingItems() {
        int initialCapacity = 2;
        InvertedIndex index = new DefaultInvertedIndex(initialCapacity);
        index.put("football", 1);
        index.put("football", 2);
        index.put("football", 3);
        index.put("basketball", 2);
        index.put("tennis", 1);

        var footballPostings = index.get("football");
        assertNotNull(footballPostings);
        assertEquals(3, footballPostings.size());

        var basketballPostings = index.get("basketball");
        assertNotNull(basketballPostings);
        assertEquals(1, basketballPostings.size());

        var tennisPostings = index.get("tennis");
        assertNotNull(tennisPostings);
        assertEquals(1, tennisPostings.size());
    }

    @Test
    public void whenAddExistingTermWithDifferentDocId_thenAppendDocIdToTermPostings() {
        InvertedIndex index = new DefaultInvertedIndex();
        index.put("hello", 1);
        index.put("hello", 2);
        index.put("hello", 3);

        SortedSet<Integer> postings = index.get("hello");
        assertEquals(3, postings.size());
    }

    @Test
    public void whenAddExistingTermWithExistingDocId_thenDoNotStoreDuplicateDocId() {
        InvertedIndex index = new DefaultInvertedIndex();
        index.put("hello", 3);
        index.put("hello", 4);
        index.put("hello", 4);

        SortedSet<Integer> postings = index.get("hello");
        assertEquals(2, postings.size());
    }
}
