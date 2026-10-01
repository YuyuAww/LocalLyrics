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
package com.hchen.superlyric.lyric.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single lyric line with timing and optional word-level breakdown.
 * Mirrors Lyrico's LyricsLine.
 *
 * @author HChenX
 */
public final class LyricLine {
    public final long start;
    public long end;
    public final String text;
    public final List<LyricWord> words;

    public LyricLine(long start, long end, String text, List<LyricWord> words) {
        this.start = start;
        this.end = end;
        this.text = text;
        this.words = words != null ? words : new ArrayList<>();
    }

    public LyricLine(long start, long end, String text) {
        this(start, end, text, null);
    }

    public boolean hasWords() {
        return words != null && !words.isEmpty() && words.size() > 1;
    }

    @Override
    public String toString() {
        return "LyricLine{start=" + start + ", end=" + end + ", text=" + text + "}";
    }
}
