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
package com.hchen.superlyric.lyric.local;

import com.hchen.superlyric.lyric.model.ParsedLyrics;
import com.hchen.superlyric.lyric.parser.LyricParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolves local lyric files by matching song IDs and file paths.
 * Supports paired files (e.g., Song.mp3 → Song.lrc) and ID-based lookup.
 *
 * @author HChenX
 */
public class LocalLyricResolver {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    private final String lyricDirectory;

    /**
     * @param lyricDirectory the user-configured lyric directory, or null
     */
    public LocalLyricResolver(String lyricDirectory) {
        this.lyricDirectory = lyricDirectory;
    }

    /**
     * Resolve local lyrics for the given song.
     *
     * @param songId   the song ID from MediaSession (may be null)
     * @param filePath the audio file path from MediaSession (may be null)
     * @return parsed lyrics, or null if no matching file found
     */
    public ParsedLyrics resolve(String songId, String filePath) {
        // Strategy 1: Song ID matching (priority)
        if (songId != null && !songId.isEmpty() && lyricDirectory != null && !lyricDirectory.isEmpty()) {
            File idFile = findById(lyricDirectory, songId);
            if (idFile != null) {
                String content = readFile(idFile);
                if (content != null) {
                    return LyricParserFactory.parse(content);
                }
            }
        }

        // Strategy 2: File path matching (fallback)
        if (filePath != null && !filePath.isEmpty()) {
            File pairedFile = findPairedFile(filePath);
            if (pairedFile != null) {
                String content = readFile(pairedFile);
                if (content != null) {
                    return LyricParserFactory.parse(content);
                }
            }
        }

        return null;
    }

    /**
     * Look up lyric files by song ID in the configured directory.
     */
    private File findById(String directory, String songId) {
        File dir = new File(directory);
        if (!dir.isDirectory()) return null;

        // Try exact match with common extensions
        String[] extensions = {".lrc", ".txt", ".ttml"};
        for (String ext : extensions) {
            File f = new File(dir, songId + ext);
            if (f.isFile() && isValidFile(f)) return f;
        }

        // Try case-insensitive match
        File[] files = dir.listFiles();
        if (files == null) return null;

        String lowerId = songId.toLowerCase(Locale.ROOT);
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            String nameNoExt = name.contains(".")
                    ? name.substring(0, name.lastIndexOf('.'))
                    : name;
            if (nameNoExt.equals(lowerId)) {
                if (isValidFile(f)) return f;
            }
        }

        return null;
    }

    /**
     * Find a paired lyric file for the given audio file path.
     * E.g., /Music/Song.mp3 → /Music/Song.lrc
     */
    static File findPairedFile(String filePath) {
        File audioFile = new File(filePath);
        if (!audioFile.isFile()) return null;

        String path = filePath;
        int dotIdx = path.lastIndexOf('.');
        String base = dotIdx > 0 ? path.substring(0, dotIdx) : path;

        // Try common extensions in order of priority
        String[] extensions = {".lrc", ".txt", ".ttml"};
        for (String ext : extensions) {
            File f = new File(base + ext);
            if (f.isFile() && isValidFile(f)) return f;
        }

        return null;
    }

    /**
     * Check if a file is valid for parsing (non-empty, within size limit).
     */
    private static boolean isValidFile(File file) {
        return file.isFile() && file.length() > 0 && file.length() <= MAX_FILE_SIZE;
    }

    /**
     * Read a file as UTF-8 text.
     *
     * @return the file content, or null on error
     */
    private static String readFile(File file) {
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_FILE_SIZE) {
            return null;
        }

        StringBuilder sb = new StringBuilder((int) Math.min(file.length(), 65536));
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int len;
            long totalBytes = 0;
            while ((len = reader.read(buffer)) != -1) {
                totalBytes += len;
                if (totalBytes > MAX_FILE_SIZE) {
                    return null;
                }
                sb.append(buffer, 0, len);
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
