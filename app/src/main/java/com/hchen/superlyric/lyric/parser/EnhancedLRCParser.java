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

import com.hchen.superlyric.lyric.model.LyricAgent;
import com.hchen.superlyric.lyric.model.LyricFormat;
import com.hchen.superlyric.lyric.model.LyricLine;
import com.hchen.superlyric.lyric.model.LyricMetadata;
import com.hchen.superlyric.lyric.model.LyricTrack;
import com.hchen.superlyric.lyric.model.LyricWord;
import com.hchen.superlyric.lyric.model.ParsedLyrics;
import com.hchen.superlyric.lyric.model.RubySyllable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses enhanced LRC format lyrics.
 * Supports metadata tags, word-level timing, Ruby annotations, and multi-agent markers.
 *
 * @author HChenX
 */
public class EnhancedLRCParser extends VerbatimLRCParser {

    private static final Pattern AGENT_TAG_PATTERN = Pattern.compile(
            "^\\[agent:\\s*(\\S+)\\s*(.*?)\\s*](.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_AGENT_PATTERN = Pattern.compile(
            "^(\\S+):\\s*(.*)$");
    private static final Pattern RUBY_PATTERN = Pattern.compile(
            "<ruby>([^<]*)</ruby>", Pattern.DOTALL);

    @Override
    public ParsedLyrics parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParsedLyrics(null, null, null, LyricFormat.ENHANCED_LRC, content);
        }

        List<LyricAgent> agents = new ArrayList<>();
        List<LyricLine> originalLines = new ArrayList<>();
        List<LyricLine> translationLines = new ArrayList<>();
        LyricMetadata.Builder metaBuilder = LyricMetadata.builder();

        String currentAgentId = null;
        long offsetMs = 0;

        String[] rawLines = content.split("\\r?\\n");
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Agent tag: [agent: id name]
            Matcher agentMatcher = AGENT_TAG_PATTERN.matcher(line);
            if (agentMatcher.matches()) {
                String agentId = agentMatcher.group(1).trim();
                String agentName = agentMatcher.group(2).trim();
                String rest = agentMatcher.group(3).trim();

                if (!rest.isEmpty()) {
                    // Has lyric content after the tag
                    if (currentAgentId == null) {
                        agents.add(new LyricAgent(agentId, LyricAgent.TYPE_PERSON, agentName));
                        currentAgentId = agentId;
                    }
                    // Process as normal lyric line (fall through)
                } else {
                    // Pure agent declaration
                    if (currentAgentId == null || !currentAgentId.equals(agentId)) {
                        agents.add(new LyricAgent(agentId, LyricAgent.TYPE_PERSON, agentName));
                    }
                    currentAgentId = agentId;
                    continue;
                }
            }

            // @agent: lyrics
            Matcher atAgentMatcher = AT_AGENT_PATTERN.matcher(line);
            if (atAgentMatcher.matches() && line.startsWith("@")) {
                // Not an agent marker (no colon after @)
            }

            // Metadata tags
            Matcher metaMatcher = Pattern.compile("^\\[([a-zA-Z#]+):?(.*?)]\\s*(.*)$").matcher(line);
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
                            try { offsetMs = Long.parseLong(value); } catch (NumberFormatException ignored) {}
                            break;
                    }
                    continue;
                }
            }

            // LRC tag line with timing
            Matcher tagMatcher = Pattern.compile("^\\[(\\d{1,3}[ :.]\\d{2}(?:[ :.]\\d{1,3})?](.*)$").matcher(line);
            if (!tagMatcher.matches()) continue;

            String timestampStr = extractTimestamp(line);
            if (timestampStr == null) continue;

            long lineStart = LRCParser.parseTimestamp(timestampStr) + offsetMs;
            if (lineStart < 0) continue;

            String textPart = tagMatcher.group(2);

            // Parse Ruby annotations first
            List<RubySyllable> rubyList = parseRuby(textPart);
            String cleanText = textPart.replaceAll("<ruby>[^<]*</ruby>", "");

            // Parse word-level timing
            List<LyricWord> words = parseWords(cleanText, lineStart);

            // Attach Ruby to words if available
            if (!rubyList.isEmpty() && !words.isEmpty()) {
                for (LyricWord w : words) {
                    if (!w.ruby.isEmpty()) continue;
                    // Simple attachment: first Ruby to first word
                    // More sophisticated matching would be needed for precise alignment
                }
            }

            String plainText = extractPlainText(cleanText);
            if (plainText.isEmpty() && words.isEmpty()) continue;

            long lineEnd = lineStart + 5000;
            if (!words.isEmpty()) {
                lineEnd = words.get(words.size() - 1).end;
            }

            LyricLine lyricLine = new LyricLine(lineStart, lineEnd, plainText, words);
            originalLines.add(lyricLine);
        }

        LRCParser.finalizeEndTimes(originalLines);

        List<LyricTrack> tracks = new ArrayList<>();
        tracks.add(new LyricTrack(LyricTrack.TYPE_ORIGINAL, originalLines));
        if (!translationLines.isEmpty()) {
            tracks.add(new LyricTrack(LyricTrack.TYPE_TRANSLATION, translationLines));
        }

        return new ParsedLyrics(metaBuilder.build(), agents, tracks, LyricFormat.ENHANCED_LRC, content);
    }

    /**
     * Parse Ruby annotations from text.
     */
    private List<RubySyllable> parseRuby(String text) {
        List<RubySyllable> rubyList = new ArrayList<>();
        Matcher matcher = RUBY_PATTERN.matcher(text);
        while (matcher.find()) {
            String rubyText = matcher.group(1).trim();
            if (!rubyText.isEmpty()) {
                rubyList.add(new RubySyllable(rubyText));
            }
        }
        return rubyList;
    }
}
