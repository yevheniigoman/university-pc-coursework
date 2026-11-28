package ua.iasa.searchsystem;

import ua.iasa.searchsystem.dto.*;
import ua.iasa.searchsystem.core.InvertedIndex;
import ua.iasa.searchsystem.core.ConcurrentInvertedIndex;
import ua.iasa.searchsystem.core.AdvancedConcurrentInvertedIndex;
import ua.iasa.searchsystem.core.construction.DocumentMetadata;
import ua.iasa.searchsystem.core.construction.DefaultDocumentMetadata;
import ua.iasa.searchsystem.core.construction.InvertedIndexConstructionRoutine;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class Controller {
    private volatile InvertedIndex index = new AdvancedConcurrentInvertedIndex();
    private final AtomicInteger documents = new AtomicInteger(0);
    private final AtomicInteger threads = new AtomicInteger(0);
    private final AtomicLong buildTime = new AtomicLong(0);

    @GetMapping("/api/health")
    public ResponseEntity<HealthDto> health() {
        var body = new HealthDto("ok", false);
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @PostMapping("/api/index")
    public ResponseEntity<IndexResponseDto> index(@RequestBody IndexRequestDto requestBody) throws IOException {
        InvertedIndex newIndex = new ConcurrentInvertedIndex();

        Queue<DocumentMetadata> queue = new ConcurrentLinkedQueue<>();
        Path dir = Paths.get("../data/D1GB");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            int id = 1;
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    queue.add(new DefaultDocumentMetadata(id++, path.toString()));
                }
            }
        }
        int numDocuments = queue.size();
        int numThreads = requestBody.threads();

        List<Future<Void>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            long start = System.nanoTime();
            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(new InvertedIndexConstructionRoutine(queue, newIndex)));
            }
            for (var future : futures) {
                future.get();
            }
            buildTime.set((System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        index = newIndex;
        documents.set(numDocuments);
        threads.set(numThreads);
        var responseBody = new IndexResponseDto("ok", numDocuments, buildTime.get());
        return ResponseEntity.status(HttpStatus.OK).body(responseBody);
    }

    @PostMapping("/api/search")
    public ResponseEntity<List<SearchResponseDto>> search(@RequestBody SearchRequestDto requestBody) {
        String query = requestBody.query();
        SortedSet<Integer> postings = index.get(query.toLowerCase());

        List<SearchResponseDto> dtos;
        if (postings == null) {
            dtos = List.of();
        } else {
            dtos = postings.stream().map(id -> new SearchResponseDto(id)).toList();
        }
        return ResponseEntity.status(HttpStatus.OK).body(dtos);
    }

    @GetMapping("/api/stats")
    public ResponseEntity<StatsDto> stats() {
        var body = new StatsDto(documents.get(), buildTime.get(), threads.get());
        return ResponseEntity.status(HttpStatus.OK).body(body);
    }
}
