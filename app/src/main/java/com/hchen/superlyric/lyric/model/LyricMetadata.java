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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lyric metadata (title, artist, album, language, timing, offset, extra).
 * Mirrors Lyrico's LyricsMetadata.
 *
 * @author HChenX
 */
public final class LyricMetadata {
    public final String title;
    public final String artist;
    public final String album;
    public final String language;
    public final String timing;
    public final Long offsetMs;
    public final Map<String, String> extra;

    public LyricMetadata(String title, String artist, String album, String language,
                         String timing, Long offsetMs, Map<String, String> extra) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.language = language;
        this.timing = timing;
        this.offsetMs = offsetMs;
        this.extra = extra != null ? extra : Collections.emptyMap();
    }

    public LyricMetadata() {
        this(null, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String artist;
        private String album;
        private String language;
        private String timing;
        private Long offsetMs;
        private final Map<String, String> extra = new HashMap<>();

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setArtist(String artist) {
            this.artist = artist;
            return this;
        }

        public Builder setAlbum(String album) {
            this.album = album;
            return this;
        }

        public Builder setLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder setTiming(String timing) {
            this.timing = timing;
            return this;
        }

        public Builder setOffsetMs(Long offsetMs) {
            this.offsetMs = offsetMs;
            return this;
        }

        public Builder putExtra(String key, String value) {
            this.extra.put(key, value);
            return this;
        }

        public LyricMetadata build() {
            return new LyricMetadata(title, artist, album, language, timing, offsetMs, extra);
        }
    }

    @Override
    public String toString() {
        return "LyricMetadata{title=" + title + ", artist=" + artist + "}";
    }
}
