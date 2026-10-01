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

/**
 * Ruby annotation syllable for phonetic reading (注音).
 * Mirrors Lyrico's LyricsRubySyllable.
 *
 * @author HChenX
 */
public final class RubySyllable {
    public final long start;
    public final long end;
    public final String text;

    public RubySyllable(long start, long end, String text) {
        this.start = start;
        this.end = end;
        this.text = text;
    }

    public RubySyllable(String text) {
        this(0, 0, text);
    }

    @Override
    public String toString() {
        return "RubySyllable{" + text + "}";
    }
}
