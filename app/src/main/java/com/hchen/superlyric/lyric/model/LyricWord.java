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
 * A single word/syllable with timing information.
 * Mirrors Lyrico's LyricsWord.
 *
 * @author HChenX
 */
public final class LyricWord {
    public final long start;
    public final long end;
    public final String text;
    public final List<RubySyllable> ruby;

    public LyricWord(long start, long end, String text, List<RubySyllable> ruby) {
        this.start = start;
        this.end = end;
        this.text = text;
        this.ruby = ruby != null ? ruby : Collections.emptyList();
    }

    public LyricWord(long start, long end, String text) {
        this(start, end, text, null);
    }

    /**
     * Create a word without timing info (text-only).
     */
    public static LyricWord of(String text) {
        return new LyricWord(0, 0, text, null);
    }

    @Override
    public String toString() {
        return "LyricWord{" + text + "}";
    }
}
