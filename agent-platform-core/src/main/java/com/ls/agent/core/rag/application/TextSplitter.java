package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.dto.RagTextChunkDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class TextSplitter {

    private static final int DEFAULT_CHUNK_TOKENS = 400;

    public List<RagTextChunkDTO> split(
            String documentTitle,
            String sourceUri,
            String content,
            int chunkTokenBudget,
            int overlapTokens
    ) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        int budget = chunkTokenBudget <= 0 ? DEFAULT_CHUNK_TOKENS : chunkTokenBudget;
        int overlap = Math.max(0, Math.min(overlapTokens, budget - 1));
        List<Section> sections = parseMarkdownSections(content);
        List<RagTextChunkDTO> chunks = new ArrayList<>();
        int index = 0;
        for (Section section : sections) {
            List<String> pieces = splitSection(section.content(), budget, overlap);
            for (String piece : pieces) {
                String normalized = piece.strip();
                if (normalized.isBlank()) {
                    continue;
                }
                chunks.add(new RagTextChunkDTO(
                        documentTitle,
                        sourceUri,
                        section.headingPath(),
                        index++,
                        normalized,
                        estimateTokens(normalized),
                        sha256(normalized)
                ));
            }
        }
        return chunks;
    }

    private List<Section> parseMarkdownSections(String content) {
        String[] lines = content.replace("\r\n", "\n").split("\n");
        List<Section> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        for (String line : lines) {
            Heading heading = parseHeading(line);
            if (heading != null) {
                flushSection(sections, currentHeading, current);
                while (headingStack.size() >= heading.level()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading.title());
                currentHeading = String.join(" > ", headingStack);
                current = new StringBuilder();
                continue;
            }
            current.append(line).append('\n');
        }
        flushSection(sections, currentHeading, current);
        if (sections.isEmpty()) {
            sections.add(new Section("", content));
        }
        return sections;
    }

    private Heading parseHeading(String line) {
        String stripped = line == null ? "" : line.strip();
        if (!stripped.startsWith("#")) {
            return null;
        }
        int level = 0;
        while (level < stripped.length() && stripped.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level >= stripped.length() || stripped.charAt(level) != ' ') {
            return null;
        }
        return new Heading(level, stripped.substring(level + 1).strip());
    }

    private void flushSection(List<Section> sections, String heading, StringBuilder current) {
        String body = current == null ? "" : current.toString().strip();
        if (!body.isBlank()) {
            sections.add(new Section(heading, body));
        }
    }

    private List<String> splitSection(String content, int budget, int overlap) {
        List<String> paragraphs = List.of(content.split("\\n\\s*\\n"));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalized = paragraph.strip().replaceAll("\\s+", " ");
            if (normalized.isBlank()) {
                continue;
            }
            if (estimateTokens(normalized) > budget) {
                flushChunk(chunks, current);
                chunks.addAll(splitByWordWindow(normalized, budget, overlap));
                continue;
            }
            String candidate = current.isEmpty() ? normalized : current + "\n" + normalized;
            if (estimateTokens(candidate) > budget) {
                flushChunk(chunks, current);
                current.append(normalized);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        if (current != null && !current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private List<String> splitByWordWindow(String content, int budget, int overlap) {
        String[] words = content.strip().split("\\s+");
        if (words.length <= budget) {
            return List.of(content);
        }
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, budget - overlap);
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(words.length, start + budget);
            chunks.add(String.join(" ", java.util.Arrays.copyOfRange(words, start, end)));
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String stripped = text.strip();
        if (stripped.contains(" ")) {
            return stripped.split("\\s+").length;
        }
        return Math.max(1, (int) Math.ceil(stripped.length() / 4.0));
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record Section(String headingPath, String content) {
    }

    private record Heading(int level, String title) {
    }
}
