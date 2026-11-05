package com.gathin.tvhome;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Makes an audio-free copy of an aerial's compressed video track, without decoding or recompressing it.
 * MediaPlayer.setVolume(0) still creates an AudioTrack on some Android TV firmware. This is deliberately
 * done off the UI thread and only once per downloaded source. The original video is never modified.
 */
final class AerialVideoOnly {
    private static final String TAG = "TvHome.AerialVideo";
    private static final long MAX_SAMPLE_BYTES = 32L << 20;

    static final class Result {
        final File file;
        final boolean videoOnly;

        Result(File file, boolean videoOnly) {
            this.file = file;
            this.videoOnly = videoOnly;
        }
    }

    private AerialVideoOnly() { }

    static Result prepare(File source, File cacheDir, BooleanSupplier musicIsPlaying) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int video = -1;
            boolean audio = false;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat track = extractor.getTrackFormat(i);
                String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("audio/")) audio = true;
                if (mime.startsWith("video/") && video == -1) {
                    video = i;
                    format = track;
                }
            }
            if (video < 0 || format == null) return new Result(source, false);
            if (!audio) return new Result(source, true);

            // WebM is used for licensed Brazilian videos, MP4 for Apple's MOV files.
            String mime = format.getString(MediaFormat.KEY_MIME);
            boolean webm = mime != null && (mime.equals("video/x-vnd.on2.vp8") || mime.equals("video/x-vnd.on2.vp9"));
            if (mime == null || !(webm || mime.equals("video/avc") || mime.equals("video/hevc")))
                return new Result(source, false);
            if (cacheDir == null || (!cacheDir.isDirectory() && !cacheDir.mkdirs()))
                return new Result(source, false);
            // Cache key changes after an aerial is replaced; no stale file can masquerade as this source.
            String key = Integer.toHexString(source.getAbsolutePath().hashCode()) + "_"
                    + source.length() + "_" + source.lastModified();
            File output = new File(cacheDir, "silent_" + key + (webm ? ".webm" : ".mp4"));
            if (output.isFile() && output.length() > 0 && hasNoAudio(output)) return new Result(output, true);
            // Never perform large disk I/O during Spotify's live/automixed audio playback.
            if (musicIsPlaying.getAsBoolean()) return new Result(source, false);
            long free = new StatFs(cacheDir.getPath()).getAvailableBytes();
            // A copy is the maximum source size; leave space for Android and streaming apps.
            if (!ScenicAudioPolicy.hasRoomForVideoCopy(free, source.length())) {
                Log.w(TAG, "Not enough free storage to remux aerial; playback will yield to music");
                return new Result(source, false);
            }
            File partial = new File(cacheDir, output.getName() + ".part");
            try {
                copyVideoTrack(extractor, video, format, partial, musicIsPlaying,
                        webm ? MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (partial.length() <= 0 || !hasNoAudio(partial) || !partial.renameTo(output))
                    throw new IOException("Cannot verify/commit silent aerial");
                return new Result(output, true);
            } catch (CancellationException e) {
                Log.i(TAG, "Deferring video-only preparation until music stops");
                return new Result(source, false);
            } catch (Exception e) {
                Log.w(TAG, "Cannot strip audio from aerial; source kept unchanged", e);
                return new Result(source, false);
            } finally {
                // Only our incomplete generated file; never the user's downloaded source.
                if (partial.exists() && !partial.delete()) Log.w(TAG, "Cannot remove incomplete silent aerial");
            }
        } catch (Exception e) {
            Log.w(TAG, "Cannot inspect aerial tracks", e);
            return new Result(source, false);
        } finally {
            extractor.release();
        }
    }

    private static boolean hasNoAudio(File file) {
        MediaExtractor check = new MediaExtractor();
        try {
            check.setDataSource(file.getAbsolutePath());
            boolean video = false;
            for (int i = 0; i < check.getTrackCount(); i++) {
                String mime = check.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("audio/")) return false;
                if (mime.startsWith("video/")) video = true;
            }
            return video;
        } catch (Exception ignored) {
            return false;
        } finally {
            check.release();
        }
    }

    private static void copyVideoTrack(MediaExtractor extractor, int sourceTrack, MediaFormat format,
                                       File out, BooleanSupplier musicIsPlaying, int container) throws IOException {
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            muxer = new MediaMuxer(out.getAbsolutePath(), container);
            int destinationTrack = muxer.addTrack(format);
            extractor.selectTrack(sourceTrack);
            muxer.start();
            started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            // API 26/27 cannot ask the extractor for the next sample size. Reserve a bounded
            // buffer from the declared maximum (or 16 MiB), rather than requiring SDK 28.
            int initialSize = 1 << 20;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                initialSize = 16 << 20;
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                    initialSize = Math.max(1 << 20, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            if (initialSize > MAX_SAMPLE_BYTES) throw new IOException("Video sample buffer is too large");
            ByteBuffer buffer = ByteBuffer.allocateDirect(initialSize);
            int samples = 0;
            while (true) {
                if (musicIsPlaying.getAsBoolean()) throw new CancellationException();
                long sampleSize = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? extractor.getSampleSize() : buffer.capacity();
                if (sampleSize < 0) break;
                if (sampleSize > MAX_SAMPLE_BYTES) throw new IOException("Video sample is too large");
                if (sampleSize > buffer.capacity()) buffer = ByteBuffer.allocateDirect((int) sampleSize);
                buffer.clear();
                int bytes = extractor.readSampleData(buffer, 0);
                if (bytes < 0) break;
                int flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                info.set(0, bytes, extractor.getSampleTime(), flags);
                muxer.writeSampleData(destinationTrack, buffer, info);
                samples++;
                extractor.advance();
            }
            if (samples == 0) throw new IOException("Video has no samples");
            muxer.stop();
            started = false;
        } finally {
            if (muxer != null) {
                try { if (started) muxer.stop(); } catch (RuntimeException ignored) { }
                muxer.release();
            }
        }
    }
}
