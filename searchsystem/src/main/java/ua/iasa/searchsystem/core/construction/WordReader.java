package ua.iasa.searchsystem.core.construction;

import java.io.Reader;
import java.io.Closeable;
import java.io.IOException;
import java.util.regex.Pattern;

class WordReader implements Closeable {
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("\\p{IsPunctuation}");
    private Reader reader;
    private String buffer;
    private final StringBuilder sb = new StringBuilder();

    public WordReader(Reader reader) throws IOException {
        this.reader = reader;
        read();
    }

    public boolean hasNext() {
        return !buffer.isEmpty();
    }

    public String read() throws IOException {
        String current = buffer;
        int val;
        while ((val = reader.read()) != -1) {
            if (Character.isWhitespace(val)) {
                if (!sb.isEmpty()) {
                    break;
                }
            } else if (PUNCTUATION_PATTERN.matcher(String.valueOf((char) val)).matches()) {
                continue;
            }
            else {
                sb.append((char) val);
            }
        }
        buffer = sb.toString();
        sb.setLength(0);
        return current;
    }

    public void close() throws IOException {
        if (reader == null) {
            return;
        }

        try {
            reader.close();
        } finally {
            reader = null;
        }
    }
}
