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

import com.hchen.superlyric.lyric.model.LyricFormat;
import com.hchen.superlyric.lyric.model.ParsedLyrics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-detects lyric format and delegates to the appropriate parser.
 * Mirrors Lyrico's LyricParserFactory.
 *
 * @author HChenX
 */
public final class LyricParserFactory {

    private static final Pattern TTML_DETECT_PATTERN = Pattern.compile(
            "^\\s*<\\?xml.*?|<tt\\s|<tt>", Pattern.DOTALL);
    private static final Pattern WORD_TAG_PATTERN = Pattern.compile(
            "<word>|</word>|<start>|</start>|<end>|</end>", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUBY_PATTERN = Pattern.compile(
            "<ruby>|</ruby>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_PATTERN = Pattern.compile(
            "^\\[(ti|ar|al|by|offset):", Pattern.CASE_INSENSITIVE);

    private LyricParserFactory() {}

    /**
     * Parse lyric content, auto-detecting the format.
     *
     * @return ParsedLyrics result, or null if content is empty
     */
    public static ParsedLyrics parse(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        LyricFormat format = detectFormat(content);
        switch (format) {
            case PLAIN_LRC:
                return new LRCParser().parse(content);
            case VERBATIM_LRC:
                return new VerbatimLRCParser().parse(content);
            case ENHANCED_LRC:
                return new EnhancedLRCParser().parse(content);
            case TTML:
                return new TTMLParser().parse(content);
            default:
                return new LRCParser().parse(content);
        }
    }

    /**
     * Detect the lyric format from content.
     *
     * @return the detected format, defaulting to PLAIN_LRC
     */
    public static LyricFormat detectFormat(String content) {
        if (content == null || content.isEmpty()) return LyricFormat.PLAIN_LRC;

        String trimmed = content.trim();

        // TTML detection: starts with <?xml or <tt
        if (TTML_DETECT_PATTERN.matcher(trimmed).find()) {
            return LyricFormat.TTML;
        }

        // Check for word-level timing tags
        boolean hasWordTags = WORD_TAG_PATTERN.matcher(trimmed).find();

        // Check for Ruby annotations
        boolean hasRuby = RUBY_PATTERN.matcher(trimmed).find();

        // Check for metadata tags
        boolean hasMetadata = false;
        for (String line : trimmed.split("\\r?\\n")) {
            if (META_PATTERN.matcher(line.trim()).find()) {
                hasMetadata = true;
                break;
            }
        }

        // Check for agent markers
        boolean hasAgent = trimmed.contains("[agent:") || trimmed.contains("<agent ");

        if (hasWordTags || hasRuby || hasAgent) {
            if (hasMetadata) {
                return LyricFormat.ENHANCED_LRC;
            }
            if (hasWordTags) {
                return LyricFormat.VERBATIM_LRC;
            }
            return LyricFormat.ENHANCED_LRC;
        }

        return LyricFormat.PLAIN_LRC;
    }
}
