package ua.iasa.searchsystem.core.construction;

import ua.iasa.searchsystem.core.InvertedIndex;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Queue;
import java.util.concurrent.Callable;

public class InvertedIndexConstructionRoutine implements Callable<Void> {
    private final Queue<DocumentMetadata> queue;
    private final InvertedIndex index;

    public InvertedIndexConstructionRoutine(Queue<DocumentMetadata> queue, InvertedIndex index) {
        this.queue = queue;
        this.index = index;
    }

    @Override
    public Void call() throws Exception {
        DocumentMetadata doc;
        while ((doc = queue.poll()) != null) {
            try (var reader = new WordReader(new BufferedReader(new FileReader(doc.path())))) {
                while (reader.hasNext()) {
                    index.put(reader.read(), doc.id());
                }
            }
        }
        return null;
    }
}
