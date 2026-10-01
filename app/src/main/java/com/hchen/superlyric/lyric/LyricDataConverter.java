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
package com.hchen.superlyric.lyric;

import com.hchen.superlyric.lyric.model.LyricLine;
import com.hchen.superlyric.lyric.model.LyricMetadata;
import com.hchen.superlyric.lyric.model.LyricTrack;
import com.hchen.superlyric.lyric.model.LyricWord;
import com.hchen.superlyric.lyric.model.ParsedLyrics;
import com.hchen.superlyricapi.SuperLyricData;
import com.hchen.superlyricapi.SuperLyricLine;
import com.hchen.superlyricapi.SuperLyricWord;

import java.util.List;
import java.util.Locale;

/**
 * Converts ParsedLyrics model objects to SuperLyric API data classes.
 *
 * @author HChenX
 */
public final class LyricDataConverter {

    private LyricDataConverter() {}

    /**
     * Convert a LyricLine to a SuperLyricLine.
     */
    public static SuperLyricLine toSuperLyricLine(LyricLine line) {
        if (line == null) return null;

        SuperLyricWord[] words = null;
        if (line.hasWords()) {
            List<LyricWord> lyricWords = line.words;
            words = new SuperLyricWord[lyricWords.size()];
            for (int i = 0; i < lyricWords.size(); i++) {
                LyricWord w = lyricWords.get(i);
                words[i] = new SuperLyricWord(w.text, w.start, w.end);
            }
        }

        return new SuperLyricLine(line.text, words, line.start, line.end);
    }

    /**
     * Convert ParsedLyrics metadata to a SuperLyricData, filling in title/artist/album.
     */
    public static SuperLyricData buildBaseData(ParsedLyrics lyrics) {
        SuperLyricData data = new SuperLyricData();
        if (lyrics == null || lyrics.metadata == null) return data;

        LyricMetadata meta = lyrics.metadata;
        if (meta.title != null) data.setTitle(meta.title);
        if (meta.artist != null) data.setArtist(meta.artist);
        if (meta.album != null) data.setAlbum(meta.album);
        return data;
    }

    /**
     * Find the translation line closest to the given timestamp using binary search.
     * Uses a tolerance window to find the best match.
     *
     * @return the translation line, or null if no match found
     */
    public static LyricLine findClosestTranslationLine(LyricTrack translationTrack, long timestampMs) {
        if (translationTrack == null || !translationTrack.hasLines()) return null;
        return findClosestLine(translationTrack.lines, timestampMs);
    }

    /**
     * Binary search for the line closest to the given timestamp.
     * Finds the line where start <= timestamp < end.
     *
     * @return the matching line, or null
     */
    public static LyricLine findLineAtTime(LyricTrack track, long timestampMs) {
        if (track == null || !track.hasLines()) return null;
        return findClosestLine(track.lines, timestampMs);
    }

    /**
     * Binary search for the line at a given timestamp in a list of lines.
     * Finds the line where start <= timestamp < end.
     * Falls back to the closest line if no exact match.
     */
    static LyricLine findClosestLine(List<LyricLine> lines, long timestampMs) {
        if (lines == null || lines.isEmpty()) return null;

        // Binary search for the rightmost line where start <= timestampMs
        int lo = 0;
        int hi = lines.size() - 1;
        int bestIdx = -1;

        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            LyricLine line = lines.get(mid);
            if (line.start <= timestampMs) {
                bestIdx = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        // Check if bestIdx is within the line's range
        if (bestIdx >= 0) {
            LyricLine line = lines.get(bestIdx);
            if (timestampMs < line.end || (line.end == 0 && timestampMs >= line.start)) {
                return line;
            }
        }

        // Check next line as fallback (within tolerance)
        if (bestIdx + 1 < lines.size()) {
            LyricLine next = lines.get(bestIdx + 1);
            if (next.start - timestampMs <= 1000) {
                return next;
            }
        }

        // Fallback: return the best match regardless of range
        if (bestIdx >= 0) {
            return lines.get(bestIdx);
        }

        return null;
    }
}
