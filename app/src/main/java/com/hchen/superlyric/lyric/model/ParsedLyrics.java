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
package com.hchen.superlyric.lyric.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified parsed lyric result, combining metadata, agents, and tracks.
 * Mirrors Lyrico's LyricsResult structure.
 *
 * @author HChenX
 */
public final class ParsedLyrics {
    public final LyricMetadata metadata;
    public final List<LyricAgent> agents;
    public final List<LyricTrack> tracks;
    public final LyricFormat sourceFormat;
    public final String rawContent;

    public ParsedLyrics(LyricMetadata metadata, List<LyricAgent> agents,
                        List<LyricTrack> tracks, LyricFormat sourceFormat, String rawContent) {
        this.metadata = metadata != null ? metadata : new LyricMetadata();
        this.agents = agents != null ? agents : new ArrayList<>();
        this.tracks = tracks != null ? tracks : new ArrayList<>();
        this.sourceFormat = sourceFormat;
        this.rawContent = rawContent;
    }

    /**
     * Get the first track of the given type, or null if not present.
     */
    public LyricTrack getTrack(String type) {
        if (tracks == null) return null;
        for (LyricTrack track : tracks) {
            if (type.equals(track.type)) return track;
        }
        return null;
    }

    public LyricTrack getOriginalTrack() {
        return getTrack(LyricTrack.TYPE_ORIGINAL);
    }

    public LyricTrack getTranslationTrack() {
        return getTrack(LyricTrack.TYPE_TRANSLATION);
    }

    public LyricTrack getRomanizationTrack() {
        return getTrack(LyricTrack.TYPE_ROMANIZATION);
    }

    public boolean hasLyrics() {
        if (tracks == null) return false;
        for (LyricTrack track : tracks) {
            if (track.hasLines()) return true;
        }
        return false;
    }

    /**
     * Check if any line has word-level timing (for karaoke-style display).
     */
    public boolean isWordByWord() {
        LyricTrack original = getOriginalTrack();
        if (original == null) return false;
        for (LyricLine line : original.lines) {
            if (line.hasWords()) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ParsedLyrics{format=" + sourceFormat + ", tracks=" + (tracks != null ? tracks.size() : 0) + "}";
    }
}
