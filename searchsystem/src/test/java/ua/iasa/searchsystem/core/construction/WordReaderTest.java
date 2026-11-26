package ua.iasa.searchsystem.core.construction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.io.StringReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;

public class WordReaderTest {
    @ParameterizedTest
    @MethodSource("argumentsProvider")
    public void test() throws IOException {
        String text = """
                hello, how  ;are   you? i   :am fine...
                """;
        var stringReader = new StringReader(text);

        var res = new ArrayList<String>();
        try (var reader = new WordReader(stringReader)) {
            while (reader.hasNext()) {
                res.add(reader.read());
            }
        }

        var actual = List.of("hello", "how", "are", "you", "i", "am", "fine");
        assertIterableEquals(res, actual);
    }

    static Stream<Arguments> argumentsProvider() {
        return Stream.of(
            Arguments.of(
                "hello how  are   you i   am fine  ",
                List.of("hello", "how", "are", "you", "i", "am", "fine")
            ),
            Arguments.of(
                "hello, how  ;are   you? i...   :am fine.",
                List.of("hello", "how", "are", "you", "i", "am", "fine")
            )
        );
    }
}
