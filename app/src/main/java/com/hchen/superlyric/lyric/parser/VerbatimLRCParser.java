/*
 * This file is part of SuperLyric.
 *
 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * Merchantability or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyric.parser;

import com.hchen.superlyric.lyric.model.LyricLine;
import com.hchen.superlyric.lyric.model.LyricWord;
import com.hchen.superlyric.lyric.model.LyricFormat;
import com.hchen.superlyric.lyric.model.ParsedLyrics;
import com.hchen.superlyric.lyric.model.LyricMetadata;
import com.hchen.superlyric.lyric.model.LyricTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses verbatim LRC format lyrics (word-level timing with &lt;word&gt; markers).
 * <p>
 * Format: [00:01.00]&lt;word&gt;text&lt;start&gt;00:01.00&lt;/start&gt;&lt;end&gt;00:02.00&lt;/end&gt;
 * or: [00:01.00]word&lt;start&gt;00:01.00&lt;/start&gt;&lt;end&gt;00:02.00&lt;/end&gt;word
 *
 * @author HChenX
 */
public class VerbatimLRCParser {

    private static final Pattern LRC_TAG_PATTERN = Pattern.compile(
            "^\\[(\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?](.*)$");
    private static final Pattern WORD_TAG_PATTERN = Pattern.compile(
            "<word>(.*?)<start>(.*?)</start><end>(.*?)</end>", Pattern.DOTALL);
    private static final Pattern RAW_WORD_PATTERN = Pattern.compile(
            "(.*?)<start>(.*?)</start><end>(.*?)</end>", Pattern.DOTALL);
    private static final Pattern META_PATTERN = Pattern.compile(
            "^\\[([a-zA-Z#]+):?(.*?)]\\s*(.*)$");

    public ParsedLyrics parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParsedLyrics(null, null, null, LyricFormat.VERBATIM_LRC, content);
        }

        List<LyricLine> lines = new ArrayList<>();
        long offsetMs = 0;
        LyricMetadata.Builder metaBuilder = LyricMetadata.builder();

        String[] rawLines = content.split("\\r?\\n");
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Metadata tag
            Matcher metaMatcher = META_PATTERN.matcher(line);
            if (metaMatcher.matches() && metaMatcher.group(3) == null
                    || (metaMatcher.matches() && metaMatcher.group(3).isEmpty())) {
                String key = metaMatcher.group(1).toLowerCase();
                String value = metaMatcher.group(2) != null ? metaMatcher.group(2) : "";
                if ("offset".equals(key)) {
                    try { offsetMs = Long.parseLong(value); } catch (NumberFormatException ignored) {}
                }
                continue;
            }

            // Must start with [timestamp]
            Matcher tagMatcher = LRC_TAG_PATTERN.matcher(line);
            if (!tagMatcher.matches()) continue;

            String timestampStr = extractTimestamp(line);
            if (timestampStr == null) continue;

            long lineStart = LRCParser.parseTimestamp(timestampStr) + offsetMs;
            if (lineStart < 0) continue;

            String textPart = tagMatcher.group(2);

            // Parse word-level timing
            List<LyricWord> words = parseWords(textPart, lineStart);

            // Extract plain text (strip all tags)
            String plainText = extractPlainText(textPart);

            if (plainText.isEmpty() && words.isEmpty()) continue;

            long lineEnd = lineStart + 5000;
            if (!words.isEmpty()) {
                lineEnd = words.get(words.size() - 1).end;
            }

            lines.add(new LyricLine(lineStart, lineEnd, plainText, words));
        }

        // Finalize end times
        LRCParser.finalizeEndTimes(lines);

        List<LyricTrack> tracks = new ArrayList<>();
        tracks.add(new LyricTrack(LyricTrack.TYPE_ORIGINAL, lines));

        return new ParsedLyrics(metaBuilder.build(), null, tracks, LyricFormat.VERBATIM_LRC, content);
    }

    /**
     * Parse word-level timing from a text segment.
     */
    List<LyricWord> parseWords(String textPart, long lineStart) {
        List<LyricWord> words = new ArrayList<>();

        // Try <word> markers first
        Matcher wordMatcher = WORD_TAG_PATTERN.matcher(textPart);
        int lastEnd = 0;
        boolean foundWordTags = false;

        while (wordMatcher.find()) {
            foundWordTags = true;
            // Text before this word (from last end)
            String prefix = textPart.substring(lastEnd, wordMatcher.start()).trim();
            if (!prefix.isEmpty()) {
                words.add(LyricWord.of(prefix));
            }
            lastEnd = wordMatcher.end();

            String wordText = wordMatcher.group(1);
            String startStr = wordMatcher.group(2);
            String endStr = wordMatcher.group(3);
            long wStart = LRCParser.parseTimestamp(startStr);
            long wEnd = LRCParser.parseTimestamp(endStr);
            if (wStart < 0) wStart = lineStart;
            if (wEnd < 0) wEnd = wStart + 500;
            words.add(new LyricWord(wStart, wEnd, wordText));
        }

        if (foundWordTags) {
            // Remaining text after last word
            String suffix = textPart.substring(lastEnd).trim();
            if (!suffix.isEmpty()) {
                words.add(LyricWord.of(suffix));
            }
            return words;
        }

        // Try raw word markers (without <word> wrapper)
        Matcher rawMatcher = RAW_WORD_PATTERN.matcher(textPart);
        lastEnd = 0;
        boolean foundRaw = false;

        while (rawMatcher.find()) {
            foundRaw = true;
            String prefix = textPart.substring(lastEnd, rawMatcher.start()).trim();
            if (!prefix.isEmpty()) {
                words.add(LyricWord.of(prefix));
            }
            lastEnd = rawMatcher.end();

            String wordText = rawMatcher.group(1);
            String startStr = rawMatcher.group(2);
            String endStr = rawMatcher.group(3);
            long wStart = LRCParser.parseTimestamp(startStr);
            long wEnd = LRCParser.parseTimestamp(endStr);
            if (wStart < 0) wStart = lineStart;
            if (wEnd < 0) wEnd = wStart + 500;
            words.add(new LyricWord(wStart, wEnd, wordText));
        }

        if (foundRaw) {
            String suffix = textPart.substring(lastEnd).trim();
            if (!suffix.isEmpty()) {
                words.add(LyricWord.of(suffix));
            }
            return words;
        }

        // No word markers found - single word for entire line
        String plainText = extractPlainText(textPart);
        if (!plainText.isEmpty()) {
            words.add(new LyricWord(lineStart, lineStart + 5000, plainText));
        }
        return words;
    }

    /**
     * Extract plain text by stripping all XML-like tags.
     */
    static String extractPlainText(String text) {
        // Remove <word>...</word> wrapping
        text = text.replaceAll("<word>|</word>", "");
        // Remove <start>...</start> and <end>...</end> tags but keep content
        text = text.replaceAll("<start>[^<]*</start>", "");
        text = text.replaceAll("<end>[^<]*</end>", "");
        // Remove any remaining tags
        text = text.replaceAll("<[^>]+>", "");
        return text.trim();
    }

    static String extractTimestamp(String line) {
        int start = line.indexOf('[');
        if (start < 0) return null;
        int end = line.indexOf(']');
        if (end < 0 || end <= start) return null;
        return line.substring(start + 1, end);
    }
}
