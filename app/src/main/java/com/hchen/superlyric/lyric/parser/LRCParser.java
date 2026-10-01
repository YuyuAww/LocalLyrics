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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyric.parser;

import com.hchen.superlyric.lyric.model.LyricLine;
import com.hchen.superlyric.lyric.model.LyricMetadata;
import com.hchen.superlyric.lyric.model.LyricTrack;
import com.hchen.superlyric.lyric.model.ParsedLyrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses plain LRC format lyrics (line-level timing only).
 * Supports metadata tags, multi-tag lines, and offset.
 *
 * @author HChenX
 */
public class LRCParser {

    private static final Pattern LRC_LINE_PATTERN = Pattern.compile(
            "^\\[(\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?](.*)$");
    private static final Pattern LRC_TAG_PATTERN = Pattern.compile(
            "^\\[(\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?](.*)$");
    private static final Pattern LRC_META_PATTERN = Pattern.compile(
            "^\\[([a-zA-Z#]+):?(.*?)]\\s*(.*)$");
    private static final Pattern LRC_TAG_ONLY_PATTERN = Pattern.compile(
            "^\\[\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?](.*)$");
    private static final Pattern MULTI_TAG_PATTERN = Pattern.compile(
            "^((?:\\[\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?])+)(.*)$");

    public ParsedLyrics parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParsedLyrics(null, null, null, null, content);
        }

        LyricMetadata.Builder metaBuilder = LyricMetadata.builder();
        List<LyricLine> originalLines = new ArrayList<>();
        List<LyricLine> translationLines = new ArrayList<>();
        long offsetMs = 0;
        String offsetLine = null;

        String[] lines = content.split("\\r?\\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i++;

            if (line.isEmpty()) continue;

            // Try metadata tags
            Matcher metaMatcher = LRC_META_PATTERN.matcher(line);
            if (metaMatcher.matches()) {
                String key = metaMatcher.group(1).toLowerCase();
                String value = metaMatcher.group(2) != null ? metaMatcher.group(2) : "";
                String rest = metaMatcher.group(3) != null ? metaMatcher.group(3) : "";

                if (rest.isEmpty()) {
                    switch (key) {
                        case "ti": metaBuilder.setTitle(value); break;
                        case "ar": metaBuilder.setArtist(value); break;
                        case "al": metaBuilder.setAlbum(value); break;
                        case "by": metaBuilder.putExtra("by", value); break;
                        case "offset":
                            try {
                                offsetMs = Long.parseLong(value);
                            } catch (NumberFormatException ignored) {}
                            break;
                    }
                }
                continue;
            }

            // Try multi-tag line: [00:01.00][00:31.00]text
            Matcher multiMatcher = MULTI_TAG_PATTERN.matcher(line);
            if (multiMatcher.matches()) {
                String tagsPart = multiMatcher.group(1);
                String text = multiMatcher.group(2).trim();

                // Extract all timestamps from tags
                Pattern tagPattern = Pattern.compile("\\[(\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?)]");
                Matcher tagMatcher = tagPattern.matcher(tagsPart);
                List<Long> timestamps = new ArrayList<>();
                while (tagMatcher.find()) {
                    long start = parseTimestamp(tagMatcher.group(1));
                    if (start >= 0) timestamps.add(start);
                }

                if (text.isEmpty()) continue;

                // Create lines for each timestamp
                if (timestamps.size() == 1) {
                    long start = timestamps.get(0) + offsetMs;
                    long end = start + 5000;
                    originalLines.add(new LyricLine(start, end, text));
                } else {
                    // Multiple timestamps = same text at different times (chorus etc.)
                    // Use the earliest timestamp
                    long start = timestamps.get(0) + offsetMs;
                    long end = start + 5000;
                    originalLines.add(new LyricLine(start, end, text));
                }
                continue;
            }

            // Single tag line
            Matcher tagMatcher = LRC_TAG_ONLY_PATTERN.matcher(line);
            if (tagMatcher.matches()) {
                String timestampStr = extractTimestamp(line);
                String text = tagMatcher.group(1).trim();

                if (timestampStr != null && !text.isEmpty()) {
                    long start = parseTimestamp(timestampStr) + offsetMs;
                    long end = start + 5000;
                    originalLines.add(new LyricLine(start, end, text));
                }
                continue;
            }

            // If line doesn't match any pattern, skip
        }

        // Calculate end times based on next line's start
        finalizeEndTimes(originalLines);

        List<LyricTrack> tracks = new ArrayList<>();
        tracks.add(new LyricTrack(LyricTrack.TYPE_ORIGINAL, originalLines));
        if (!translationLines.isEmpty()) {
            tracks.add(new LyricTrack(LyricTrack.TYPE_TRANSLATION, translationLines));
        }

        return new ParsedLyrics(metaBuilder.build(), null, tracks,
                com.hchen.superlyric.lyric.model.LyricFormat.PLAIN_LRC, content);
    }

    /**
     * Extract the first timestamp from a line like [01:02.34]text.
     */
    static String extractTimestamp(String line) {
        int start = line.indexOf('[');
        if (start < 0) return null;
        int end = line.indexOf(']');
        if (end < 0 || end <= start) return null;
        return line.substring(start + 1, end);
    }

    /**
     * Parse LRC timestamp string "mm:ss.xx" to milliseconds.
     * Fraction: 1 digit = 100ms, 2 digits = 10ms, 3 digits = 1ms.
     */
    static long parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return -1;
        try {
            int sep = -1;
            for (int i = 0; i < ts.length(); i++) {
                char c = ts.charAt(i);
                if (c == ':' || c == ' ') { sep = i; break; }
            }
            if (sep < 0) return -1;

            long minute = Long.parseLong(ts.substring(0, sep));
            String rest = ts.substring(sep + 1);
            int dot = -1;
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (c == '.' || c == ':') { dot = i; break; }
            }

            if (dot < 0) {
                long second = Long.parseLong(rest);
                return minute * 60000 + second * 1000;
            }

            long second = Long.parseLong(rest.substring(0, dot));
            String frac = rest.substring(dot + 1);
            long fracMs;
            if (frac.length() == 1) fracMs = Long.parseLong(frac) * 100;
            else if (frac.length() == 2) fracMs = Long.parseLong(frac) * 10;
            else fracMs = Long.parseLong(frac);

            return Math.addExact(Math.addExact(Math.multiplyExact(minute, 60000L),
                    Math.multiplyExact(second, 1000L)), fracMs);
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    /**
     * Set end time of each line to the start of the next line.
     * Last line gets start + 5000ms fallback.
     */
    static void finalizeEndTimes(List<LyricLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (i + 1 < lines.size()) {
                line.end = lines.get(i + 1).start;
            } else {
                if (line.end == 0) line.end = line.start + 5000;
            }
        }
    }
}
