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
package com.hchen.superlyric.hook.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hchen.hooktool.hook.AbsHook;
import com.hchen.hooktool.utils.PrefsTool;
import com.hchen.superlyric.data.PrefsKey;
import com.hchen.superlyric.hook.AbsPublisher;
import com.hchen.superlyric.lyric.LyricDataConverter;
import com.hchen.superlyric.lyric.local.LocalLyricResolver;
import com.hchen.superlyric.lyric.model.LyricLine;
import com.hchen.superlyric.lyric.model.LyricTrack;
import com.hchen.superlyric.lyric.model.ParsedLyrics;
import com.hchen.superlyricapi.SuperLyricData;
import com.hchen.superlyricapi.SuperLyricLine;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * Local lyric publisher: hooks MediaSession to track playback state and
 * resolves local lyric files for the current song.
 *
 * @author HChenX
 */
public class LocalLyricPublisher extends AbsPublisher {

    private static final long LOOP_INTERVAL_MS = 42L;

    // Playback state snapshot (setPlaybackState writes, polling thread reads)
    private volatile PlaybackSnapshot mPlayback = PlaybackSnapshot.INITIAL;

    // Current track + lyrics snapshot (immutable, atomically published)
    private final AtomicReference<TrackSnapshot> mTrackRef = new AtomicReference<>();
    private final AtomicLong mTrackGeneration = new AtomicLong();

    // 42ms polling loop
    private HandlerThread mLyricThread;
    private Handler mLyricHandler;
    private boolean mHooksInitialized;
    private volatile boolean mIsRunning = false;
    private volatile long mLoopToken = 0L;

    private Context mAppContext;
    private LocalLyricResolver mResolver;

    // ---- Immutable snapshot classes ----

    private static final class PlaybackSnapshot {
        static final PlaybackSnapshot INITIAL =
            new PlaybackSnapshot(PlaybackState.STATE_NONE, 0L, 0f, 0L);

        final int state;
        final long position;
        final float speed;
        final long anchorTime;

        PlaybackSnapshot(int state, long position, float speed, long anchorTime) {
            this.state = state;
            this.position = position;
            this.speed = speed;
            this.anchorTime = anchorTime;
        }
    }

    private static final class TrackSnapshot {
        final long generation;
        @NonNull final String songTitle;
        @NonNull final String songArtist;
        @NonNull final String songAlbum;
        @Nullable final ParsedLyrics lyrics;
        final int lastShownIndex;

        TrackSnapshot(long generation, @NonNull String title, @NonNull String artist,
                      @NonNull String album, @Nullable ParsedLyrics lyrics, int lastShownIndex) {
            this.generation = generation;
            this.songTitle = title;
            this.songArtist = artist;
            this.songAlbum = album;
            this.lyrics = lyrics;
            this.lastShownIndex = lastShownIndex;
        }

        @NonNull
        TrackSnapshot withLyrics(@Nullable ParsedLyrics newLyrics) {
            return new TrackSnapshot(generation, songTitle, songArtist, songAlbum,
                    newLyrics, -1);
        }

        @NonNull
        TrackSnapshot withShownIndex(int index) {
            return new TrackSnapshot(generation, songTitle, songArtist, songAlbum,
                    lyrics, index);
        }
    }

    // ---- Lifecycle ----

    @Override
    protected synchronized void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (mHooksInitialized) {
            logD(tag, "Local lyric hooks already initialized for " + param.getPackageName());
            return;
        }
        mLyricThread = new HandlerThread("LocalLyricPublisherThread");
        mLyricThread.start();
        mLyricHandler = new Handler(mLyricThread.getLooper());

        hookMediaSession();
        hookPlaybackState();
        mHooksInitialized = true;
        logI(tag, "Local lyric path hooks loaded (package: " + param.getPackageName() + ")");
    }

    @Override
    protected void onApplicationCreated(@NonNull Context context) {
        super.onApplicationCreated(context);
        mAppContext = context.getApplicationContext();

        // Read from SuperLyric's shared prefs (not the hooked app's private prefs)
        SharedPreferences prefs = PrefsTool.prefs(context);
        String lyricDir = prefs.getString(PrefsKey.LOCAL_LYRIC_DIRECTORY, null);
        mResolver = new LocalLyricResolver(lyricDir);

        logI(tag, "Local lyric resolver initialized, directory: " + (lyricDir != null ? lyricDir : "null"));
    }

    // ---- MediaSession hooks ----

    private void hookMediaSession() {
        hookMethod("android.media.session.MediaSession",
            "setMetadata",
            "android.media.MediaMetadata",
            new AbsHook() {
                @Override
                public void after() {
                    Object arg = getArg(0);
                    if (arg instanceof MediaMetadata metadata) {
                        onMetadataChanged(metadata);
                    }
                }
            }
        );
    }

    private void hookPlaybackState() {
        hookMethod("android.media.session.MediaSession",
            "setPlaybackState",
            "android.media.session.PlaybackState",
            new AbsHook() {
                @Override
                public void after() {
                    Object arg = getArg(0);
                    if (arg instanceof PlaybackState state) {
                        onPlaybackStateChanged(state);
                    }
                }
            }
        );
    }

    // ---- Event handlers ----

    private void onPlaybackStateChanged(@NonNull PlaybackState state) {
        mPlayback = new PlaybackSnapshot(
            state.getState(),
            state.getPosition(),
            state.getPlaybackSpeed(),
            SystemClock.elapsedRealtime()
        );

        logD(tag, "PlaybackState: state=" + mPlayback.state + ", position=" + mPlayback.position
                + ", speed=" + mPlayback.speed);

        switch (mPlayback.state) {
            case PlaybackState.STATE_PLAYING:
                startLoop();
                break;
            case PlaybackState.STATE_STOPPED:
                sendStop();
                stopLoop();
                break;
            case PlaybackState.STATE_BUFFERING:
                // Keep last line, don't stop
                break;
            default:
                // Paused etc.: stop loop but keep last line
                stopLoop();
                break;
        }
    }

    private void onMetadataChanged(@NonNull MediaMetadata metadata) {
        String mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);

        // Get file path for pairing (METADATA_KEY_MEDIA_URI is a String key, not a URI key)
        String uriStr = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI);
        String filePath = uriStr;
        // Resolve content:// URI to real file path
        if (uriStr != null && uriStr.startsWith("content://")) {
            filePath = resolveContentUri(mAppContext, uriStr);
        }

        String songId = mediaId != null ? mediaId : "";
        String safeTitle = title != null ? title : "";
        String safeArtist = artist != null ? artist : "";
        String safeAlbum = album != null ? album : "";

        // Resolve local lyric files
        ParsedLyrics lyrics = mResolver.resolve(songId, filePath, safeTitle, safeArtist);

        if (lyrics == null) {
            logD(tag, "No local lyric file found for song: " + safeTitle + " (id=" + songId + ")");
        } else {
            logI(tag, "Resolved local lyrics: " + lyrics);
        }

        long generation = mTrackGeneration.incrementAndGet();
        mTrackRef.set(new TrackSnapshot(
            generation,
            safeTitle,
            safeArtist,
            safeAlbum,
            lyrics,
            -1
        ));

        // If currently playing, start loop
        PlaybackSnapshot playback = mPlayback;
        if (playback != null && playback.state == PlaybackState.STATE_PLAYING) {
            startLoop();
        }

        // Clear previous lyrics if new song has none
        if (lyrics == null) {
            sendStop();
        }
    }

    // ---- 42ms polling loop ----

    private synchronized void startLoop() {
        if (mIsRunning) return;
        mIsRunning = true;
        final long token = ++mLoopToken;
        mLyricHandler.post(() -> runLoop(token));
    }

    private synchronized void stopLoop() {
        mIsRunning = false;
    }

    private void runLoop(long token) {
        if (!mIsRunning || token != mLoopToken) return;

        try {
            PlaybackSnapshot playback = mPlayback;
            if (playback == null || playback.state != PlaybackState.STATE_PLAYING) {
                mIsRunning = false;
                return;
            }

            TrackSnapshot current = mTrackRef.get();
            if (current == null || current.lyrics == null || !current.lyrics.hasLyrics()) {
                mIsRunning = false;
                return;
            }

            LyricTrack originalTrack = current.lyrics.getOriginalTrack();
            if (originalTrack == null || !originalTrack.hasLines()) {
                mIsRunning = false;
                return;
            }

            List<LyricLine> lines = originalTrack.lines;
            long estimated = estimatePosition(playback, SystemClock.elapsedRealtime());
            int index = findLineIndex(lines, estimated);

            if (index >= 0 && index != current.lastShownIndex) {
                LyricLine line = lines.get(index);
                if (!line.text.isBlank()) {
                    TrackSnapshot updated = current.withShownIndex(index);
                    if (mTrackRef.compareAndSet(current, updated)) {
                        sendCurrentLine(current, line);
                    }
                }
            }
        } catch (Throwable t) {
            logE(tag, "Lyric loop error", t);
        }

        if (mIsRunning && token == mLoopToken) {
            mLyricHandler.postDelayed(() -> runLoop(token), LOOP_INTERVAL_MS);
        }
    }

    private void sendCurrentLine(@NonNull TrackSnapshot snapshot, @NonNull LyricLine line) {
        SuperLyricLine lyricLine = LyricDataConverter.toSuperLyricLine(line);
        if (lyricLine == null) return;

        SuperLyricData data = new SuperLyricData()
            .setTitle(snapshot.songTitle)
            .setArtist(snapshot.songArtist)
            .setAlbum(snapshot.songAlbum)
            .setLyric(lyricLine);

        // Find matching translation line
        LyricTrack translationTrack = snapshot.lyrics.getTranslationTrack();
        if (translationTrack != null) {
            LyricLine translationLine = LyricDataConverter.findClosestTranslationLine(
                    translationTrack, line.start);
            if (translationLine != null && !translationLine.text.isEmpty()) {
                SuperLyricLine translationLineObj = LyricDataConverter.toSuperLyricLine(translationLine);
                if (translationLineObj != null) {
                    data.setTranslation(translationLineObj);
                }
            }
        }

        sendLyric(data);
        logD(tag, "sendLyric: title=" + snapshot.songTitle + ", start=" + line.start);
    }

    // ---- Position estimation ----

    private static long estimatePosition(@NonNull PlaybackSnapshot playback, long now) {
        long base = Math.max(0L, playback.position);
        if (!Float.isFinite(playback.speed) || playback.speed < 0f || now <= playback.anchorTime)
            return base;
        long elapsed = now - playback.anchorTime;
        double delta = elapsed * (double) playback.speed;
        if (!Double.isFinite(delta) || delta >= Long.MAX_VALUE) return Long.MAX_VALUE;
        try {
            return Math.max(0L, Math.addExact(base, (long) delta));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Binary search for the line where start <= position < end.
     */
    private static int findLineIndex(@NonNull List<LyricLine> lines, long position) {
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).start <= position) low = mid + 1;
            else high = mid;
        }
        for (int i = low - 1; i >= 0; i--) {
            LyricLine line = lines.get(i);
            if (position >= line.start && position < line.end) return i;
        }
        return -1;
    }

    /**
     * Resolve a content:// URI to a real file path using MediaStore.
     */
    @Nullable
    private static String resolveContentUri(@NonNull Context context, @NonNull String uriStr) {
        try {
            Uri uri = Uri.parse(uriStr);
            ContentResolver resolver = context.getContentResolver();
            try (Cursor cursor = resolver.query(uri,
                    new String[]{MediaStore.Audio.Media.DATA}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int colIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                    if (colIndex >= 0) {
                        return cursor.getString(colIndex);
                    }
                }
            }
        } catch (Exception e) {
            logE(tag, "Failed to resolve content URI: " + uriStr, e);
        }
        return null;
    }
}
