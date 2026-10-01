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
 * A lyric track (original, translation, romanization, background, or other).
 * Mirrors Lyrico's LyricsTrack.
 *
 * @author HChenX
 */
public final class LyricTrack {
    public static final String TYPE_ORIGINAL = "original";
    public static final String TYPE_TRANSLATION = "translation";
    public static final String TYPE_ROMANIZATION = "romanization";
    public static final String TYPE_BACKGROUND = "background";
    public static final String TYPE_OTHER = "other";

    public final String type;
    public final String language;
    public final List<LyricLine> lines;

    public LyricTrack(String type, String language, List<LyricLine> lines) {
        this.type = type != null ? type : TYPE_OTHER;
        this.language = language;
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public LyricTrack(String type, List<LyricLine> lines) {
        this(type, null, lines);
    }

    public boolean hasLines() {
        return lines != null && !lines.isEmpty();
    }

    @Override
    public String toString() {
        return "LyricTrack{type=" + type + ", lines=" + (lines != null ? lines.size() : 0) + "}";
    }
}
