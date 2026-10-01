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
 * A lyric agent (multi-person/performance actor).
 * Mirrors Lyrico's LyricsAgent.
 *
 * @author HChenX
 */
public final class LyricAgent {
    /** Agent types matching Lyrico's LyricsAgentType. */
    public static final String TYPE_PERSON = "person";
    public static final String TYPE_GROUP = "group";
    public static final String TYPE_CHARACTER = "character";
    public static final String TYPE_ORGANIZATION = "organization";
    public static final String TYPE_OTHER = "other";
    public static final String TYPE_NARRATOR = "narrator";

    public final String id;
    public final String type;
    public final String name;

    public LyricAgent(String id, String type, String name) {
        this.id = id;
        this.type = type != null ? type : TYPE_PERSON;
        this.name = name;
    }

    @Override
    public String toString() {
        return "LyricAgent{id=" + id + ", name=" + name + "}";
    }
}
