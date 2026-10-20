package io.github.natnaelmekuriaw.minisearch.index;

import io.github.natnaelmekuriaw.minisearch.text.Tokenizer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class IndexStore {
    private static final int MAGIC = 0x4d534958;
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_PATH_BYTES = 16_384;
    private static final int MAX_TERM_BYTES = 8_192;
    private static final int MAX_CONTENT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DOCUMENTS = 1_000_000;
    private static final int MAX_TERMS = 10_000_000;

    public void save(Path file, InMemoryIndex index) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(index, "index");
        Path target = file.toAbsolutePath().normalize();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName() + ".", ".tmp");
        try {
            try (var output = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                DigestOutputStream digestOutput = new DigestOutputStream(output, sha256());
                DataOutputStream data = new DataOutputStream(digestOutput);
                writeIndex(data, index);
                data.flush();
                digestOutput.on(false);
                data.write(digestOutput.getMessageDigest().digest());
                data.flush();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic index replacement is unavailable: " + target, exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public InMemoryIndex load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path source = file.toAbsolutePath().normalize();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
            DigestInputStream digestInput = new DigestInputStream(input, sha256());
            DataInputStream data = new DataInputStream(digestInput);
            InMemoryIndex index = readIndex(data);
            digestInput.on(false);
            byte[] expected = data.readNBytes(DIGEST_BYTES);
            if (expected.length != DIGEST_BYTES || data.read() != -1
                    || !MessageDigest.isEqual(expected, digestInput.getMessageDigest().digest())) {
                throw new IOException("Index checksum mismatch: " + source);
            }
            return index;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid index data: " + source, exception);
        }
    }

    private static void writeIndex(DataOutputStream data, InMemoryIndex index) throws IOException {
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        data.writeInt(index.documents().size());
        for (IndexedDocument document : index.documents()) {
            writeString(data, document.path(), MAX_PATH_BYTES);
            data.writeInt(document.tokenCount());
            writeString(data, index.content(document.path()).orElseThrow(), MAX_CONTENT_BYTES);
        }
        data.writeInt(index.termCount());
        for (Map.Entry<String, List<Posting>> term : new TreeMap<>(index.allPostings()).entrySet()) {
            writeString(data, term.getKey(), MAX_TERM_BYTES);
            data.writeInt(term.getValue().size());
            for (Posting posting : term.getValue()) {
                writeString(data, posting.path(), MAX_PATH_BYTES);
                data.writeInt(posting.frequency());
                for (int position : posting.positions()) {
                    data.writeInt(position);
                }
            }
        }
    }

    private static InMemoryIndex readIndex(DataInputStream data) throws IOException {
        if (data.readInt() != MAGIC) {
            throw new IOException("Unrecognized index format");
        }
        int version = data.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported index version: " + version);
        }
        int documentCount = readCount(data, MAX_DOCUMENTS, "document count");
        List<IndexedDocument> documents = new ArrayList<>();
        Map<String, String> contents = new HashMap<>();
        Map<String, Integer> lengths = new HashMap<>();
        String priorPath = null;
        for (int i = 0; i < documentCount; i++) {
            String path = readString(data, MAX_PATH_BYTES);
            validatePath(path);
            if (priorPath != null && priorPath.compareTo(path) >= 0) {
                throw new IOException("Index documents are out of order");
            }
            int length = readCount(data, MAX_CONTENT_BYTES, "document token count");
            documents.add(new IndexedDocument(path, length));
            contents.put(path, readString(data, MAX_CONTENT_BYTES));
            lengths.put(path, length);
            priorPath = path;
        }
        int termCount = readCount(data, MAX_TERMS, "term count");
        Map<String, List<Posting>> postings = new TreeMap<>();
        String priorTerm = null;
        for (int i = 0; i < termCount; i++) {
            String term = readString(data, MAX_TERM_BYTES);
            if (term.isBlank() || (priorTerm != null && priorTerm.compareTo(term) >= 0)) {
                throw new IOException("Index terms are invalid or out of order");
            }
            int postingCount = readCount(data, documentCount, "posting count");
            if (postingCount == 0) {
                throw new IOException("Index term has no postings");
            }
            List<Posting> termPostings = new ArrayList<>();
            String priorPosting = null;
            for (int j = 0; j < postingCount; j++) {
                String path = readString(data, MAX_PATH_BYTES);
                if (!lengths.containsKey(path) || (priorPosting != null && priorPosting.compareTo(path) >= 0)) {
                    throw new IOException("Index posting path is invalid or out of order");
                }
                int frequency = readCount(data, lengths.get(path), "posting frequency");
                if (frequency == 0) {
                    throw new IOException("Index posting has no positions");
                }
                List<Integer> positions = new ArrayList<>();
                int priorPosition = -1;
                for (int k = 0; k < frequency; k++) {
                    int position = data.readInt();
                    if (position <= priorPosition || position >= lengths.get(path)) {
                        throw new IOException("Index posting position is invalid");
                    }
                    positions.add(position);
                    priorPosition = position;
                }
                termPostings.add(new Posting(path, positions));
                priorPosting = path;
            }
            postings.put(term, List.copyOf(termPostings));
            priorTerm = term;
        }
        return new InMemoryIndex(documents, postings, contents, new Tokenizer());
    }

    private static void validatePath(String path) throws IOException {
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")
                || Arrays.stream(path.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".")
                        || part.equals(".."))) {
            throw new IOException("Index document path is invalid");
        }
    }

    private static int readCount(DataInputStream data, int maximum, String name) throws IOException {
        int count = data.readInt();
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid " + name + ": " + count);
        }
        return count;
    }

    private static String readString(DataInputStream data, int maximumBytes) throws IOException {
        int length = readCount(data, maximumBytes, "string length");
        byte[] bytes = data.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Truncated index string");
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void writeString(DataOutputStream data, String value, int maximumBytes) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IOException("Index value exceeds " + maximumBytes + " bytes");
        }
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
