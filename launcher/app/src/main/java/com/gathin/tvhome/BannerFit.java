package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * How an app's artwork fills a landscape tile without ever scaling X and Y independently.
 *
 * - {@link #CROP}: a true TV banner close to the tile's shape fills it edge to edge (centre crop). Only when the
 *   crop removes at most {@link #MAX_CROP_LOSS} of the image, so a logo near the edge is not cut off.
 * - {@link #LETTERBOX}: a banner of a different landscape shape (2:1, 3:1) is shown whole (fit centre) over a
 *   background sampled from the banner's own edge pixels, so the bars read as part of the artwork.
 * - {@link #ICON}: no banner, or a square/portrait "banner": the app icon at its native proportions, centred on
 *   a brand-coloured card sampled from the icon. Never stretched into landscape, never a black box.
 */
final class BannerFit {
    private BannerFit() { }

    static final int CROP = 0, LETTERBOX = 1, ICON = 2;
    /** The largest share of the image a centre crop may remove (width or height). */
    static final float MAX_CROP_LOSS = 0.10f;
    /** Below this width:height an image is an icon, not a banner (AppAdapter.isBannerShaped agrees). */
    static final float MIN_BANNER_ASPECT = 1.4f;
    /** The fallback icon's box, as a share of the tile height. */
    static final float ICON_FILL = 0.58f;

    /** Mode for artwork of this intrinsic size on a tile of this aspect (w/h). Unknown size (a colour) fills. */
    static int mode(int w, int h, float tileAspect, boolean hasBanner) {
        if (!hasBanner) return ICON;
        if (w <= 0 || h <= 0) return CROP;
        float a = w / (float) h;
        if (a < MIN_BANNER_ASPECT) return ICON;
        return cropLoss(a, tileAspect) <= MAX_CROP_LOSS ? CROP : LETTERBOX;
    }

    /** Share of the source removed by a centre crop to the tile's aspect. */
    static float cropLoss(float sourceAspect, float tileAspect) {
        if (sourceAspect <= 0 || tileAspect <= 0) return 0f;
        return 1f - Math.min(sourceAspect, tileAspect) / Math.max(sourceAspect, tileAspect);
    }

    /**
     * Size {w, h} of an image of intrinsic size iw x ih fitted inside a box, aspect kept. Unknown sizes are
     * treated as square. Never wider or taller than the box.
     */
    static int[] fit(int iw, int ih, int boxW, int boxH) {
        if (iw <= 0 || ih <= 0) { iw = 1; ih = 1; }
        float s = Math.min(boxW / (float) iw, boxH / (float) ih);
        return new int[]{Math.max(1, Math.round(iw * s)), Math.max(1, Math.round(ih * s))};
    }

    /** A dark-enough version of a brand colour for the fallback card, so a white icon still reads on it. */
    static int cardColor(int brand) {
        float[] hsv = new float[3];
        Color.colorToHSV(brand, hsv);
        hsv[2] = Math.min(hsv[2], 0.42f);
        hsv[1] = Math.min(hsv[1], 0.75f);
        return Color.HSVToColor(0xFF, hsv);
    }

    /** Average of the outer pixels of the drawable: the colour letterbox bars take. Draws a private copy. */
    static int edgeColor(Drawable live) {
        if (live == null) return 0xFF202226;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int w = 32, h = 18;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(bmp));
        d.setBounds(saved);
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        bmp.recycle();
        return Color.rgb(edgeAverage(px, w, h, 16), edgeAverage(px, w, h, 8), edgeAverage(px, w, h, 0));
    }

    /** Mean of one channel (bit shift) over the border pixels of a w x h ARGB array; transparent counts as black. */
    static int edgeAverage(int[] px, int w, int h, int shift) {
        long sum = 0;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x != 0 && y != 0 && x != w - 1 && y != h - 1) continue;
                int p = px[y * w + x];
                int a = (p >>> 24) & 0xFF;
                sum += (((p >> shift) & 0xFF) * a) / 255;
                n++;
            }
        }
        return n == 0 ? 0 : (int) (sum / n);
    }

    /**
     * Binds a tile: banner (crop or letterbox) or the icon fallback. tileW/tileH are the card's size in px.
     * label (may be null) is the in-tile name, shown only when showLabel and the tile has no banner.
     */
    static void bind(View card, ImageView banner, ImageView icon, View label, AppAdapter.Entry e,
                     int tileW, int tileH, float radiusPx, boolean showLabel) {
        // The card's own background (tile_bg), kept so a recycled card that showed a fallback colour gets it back.
        if (card.getTag(R.id.tile_bg_orig) == null) card.setTag(R.id.tile_bg_orig, card.getBackground() != null
                ? card.getBackground() : new GradientDrawable());
        Drawable art = e.banner;
        int m = mode(art != null ? art.getIntrinsicWidth() : 0, art != null ? art.getIntrinsicHeight() : 0,
                tileH > 0 ? tileW / (float) tileH : 144f / 84f, art != null);
        if (m == ICON) {
            banner.setImageDrawable(null);
            banner.setVisibility(View.GONE);
            Drawable ic = e.icon;
            icon.setImageDrawable(ic);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int box = Math.round(tileH * ICON_FILL);
            int[] size = fit(ic != null ? ic.getIntrinsicWidth() : 0, ic != null ? ic.getIntrinsicHeight() : 0, box, box);
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp.width != size[0] || lp.height != size[1]) {
                lp.width = size[0];
                lp.height = size[1];
                if (lp instanceof ViewGroup.MarginLayoutParams) ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0;
                icon.setLayoutParams(lp);
            }
            icon.setVisibility(View.VISIBLE);
            if (e.cardColor == 0) e.cardColor = cardColor(AppAdapter.vividColor(ic));
            card.setBackground(rounded(e.cardColor, radiusPx));
            if (label != null) label.setVisibility(showLabel ? View.VISIBLE : View.GONE);
            return;
        }
        icon.setImageDrawable(null);
        icon.setVisibility(View.GONE);
        if (label != null) label.setVisibility(View.GONE);
        card.setBackground((Drawable) card.getTag(R.id.tile_bg_orig));
        banner.setVisibility(View.VISIBLE);
        banner.setImageDrawable(art);
        if (m == CROP) {
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            banner.setBackground(null);
        } else {
            banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (e.edgeColor == 0) e.edgeColor = edgeColor(art);
            banner.setBackgroundColor(e.edgeColor);
        }
    }

    private static Drawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
}
