package com.gathin.tvhome;

/**
 * The centre-crop coordinate model shared by the scenic backdrop and every glass surface: the scene image is
 * scaled to cover the screen (aspect kept) and centred, so a glass panel can paint exactly the part of the
 * (pre-blurred) image that lies under it. Returns float arrays so it runs on the JVM.
 */
final class SceneCrop {
    private SceneCrop() { }

    /** {scale, dx, dy}: screen = image * scale + d. */
    static float[] cover(int imgW, int imgH, int screenW, int screenH) {
        if (imgW <= 0 || imgH <= 0 || screenW <= 0 || screenH <= 0) return new float[]{1f, 0f, 0f};
        float s = Math.max(screenW / (float) imgW, screenH / (float) imgH);
        return new float[]{s, (screenW - imgW * s) / 2f, (screenH - imgH * s) / 2f};
    }

    /** The image rect {l, t, r, b} under a screen rect, clamped to the image. */
    static float[] source(int imgW, int imgH, int screenW, int screenH, float l, float t, float r, float b) {
        float[] c = cover(imgW, imgH, screenW, screenH);
        float s = c[0];
        return new float[]{
                clamp((l - c[1]) / s, imgW), clamp((t - c[2]) / s, imgH),
                clamp((r - c[1]) / s, imgW), clamp((b - c[2]) / s, imgH)};
    }

    private static float clamp(float v, int max) {
        return Math.max(0f, Math.min(max, v));
    }
}
