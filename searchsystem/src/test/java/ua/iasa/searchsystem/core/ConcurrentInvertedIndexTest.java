package ua.iasa.searchsystem.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

public class ConcurrentInvertedIndexTest {
    record Document(int id, String[] words) {}

    String text1 = """
            From fairest creatures we desire increase,
            That thereby beauty's rose might never die,
            But as the riper should by time decease,
            His tender heir might bear his memory:
            But thou contracted to thine own bright eyes,
            Feed'st thy light's flame with self-substantial fuel,
            Making a famine where abundance lies,
            Thy self thy foe, to thy sweet self too cruel:
            Thou that art now the world's fresh ornament,
            And only herald to the gaudy spring,
            Within thine own bud buriest thy content,
            And tender churl mak'st waste in niggarding:
            Pity the world, or else this glutton be,
            To eat the world's due, by the grave and thee.
            """;
    String text2 = """
            When forty winters shall besiege thy brow,
            And dig deep trenches in thy beauty's field,
            Thy youth's proud livery so gazed on now,
            Will be a tattered weed of small worth held:
            Then being asked, where all thy beauty lies,
            Where all the treasure of thy lusty days;
            To say within thine own deep sunken eyes,
            Were an all-eating shame, and thriftless praise.
            How much more praise deserved thy beauty's use,
            If thou couldst answer 'This fair child of mine
            Shall sum my count, and make my old excuse'
            Proving his beauty by succession thine.
            This were to be new made when thou art old,
            And see thy blood warm when thou feel'st it cold.
            """;
    String text3 = """
            Look in thy glass and tell the face thou viewest,
            Now is the time that face should form another,
            Whose fresh repair if now thou not renewest,
            Thou dost beguile the world, unbless some mother.
            For where is she so fair whose uneared womb
            Disdains the tillage of thy husbandry?
            Or who is he so fond will be the tomb,
            Of his self-love to stop posterity?
            Thou art thy mother's glass and she in thee
            Calls back the lovely April of her prime,
            So thou through windows of thine age shalt see,
            Despite of wrinkles this thy golden time.
            But if thou live remembered not to be,
            Die single and thine image dies with thee.
            """;

    private final Document[] docs = {
        new Document(1, text1.toLowerCase().split(" ")),
        new Document(2, text2.toLowerCase().split(" ")),
        new Document(3, text3.toLowerCase().split(" "))
    };
    @Test
    public void whenThresholdExceeds_thenResizeWithPreservingExistingItems() {
        InvertedIndex index = new ConcurrentInvertedIndex(2);
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
        InvertedIndex index = new ConcurrentInvertedIndex(2);
        index.put("hello", 1);
        index.put("hello", 2);
        index.put("hello", 3);

        SortedSet<Integer> postings = index.get("hello");
        assertEquals(3, postings.size());
    }

    @Test
    public void whenAddExistingTermWithExistingDocId_thenDoNotStoreDuplicateDocId() {
        InvertedIndex index = new ConcurrentInvertedIndex(2);
        index.put("hello", 3);
        index.put("hello", 4);
        index.put("hello", 4);

        SortedSet<Integer> postings = index.get("hello");
        assertEquals(2, postings.size());
    }

    @Test
    public void test() throws InterruptedException {
        InvertedIndex defaultIndex = new DefaultInvertedIndex();
        for (var doc : docs) {
            for (var word : doc.words) {
                defaultIndex.put(word, doc.id);
            }
        }

        InvertedIndex concurrentIndex = new ConcurrentInvertedIndex(8);
        int threads = 3;
        var latch = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                int docId = i;
                Document doc = docs[docId];
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (String word : doc.words) {
                            concurrentIndex.put(word, doc.id);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            latch.countDown();
        }

        for (var doc : docs) {
            for (String word : doc.words) {
                var concurrentPostings = concurrentIndex.get(word);
                var defaultPostings = defaultIndex.get(word);
                assertNotNull(concurrentPostings, "word " + word);
                assertNotNull(defaultPostings, "word " + word);
                assertEquals(concurrentPostings, defaultPostings, "word " + word);
            }
        }
    }
}
