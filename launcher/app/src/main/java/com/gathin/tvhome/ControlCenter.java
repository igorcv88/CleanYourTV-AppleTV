package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Control Center: a compact glass sheet that floats at the top right, anchored to the button that opens it,
 * over a Home that stays visible and unchanged around it.
 *
 * <pre>
 *  ┌──────────────────────────────────────────┐
 *  │ (wifi) Wi-Fi        │ (spk) Sound        │   status capsules: shortcuts, never fake toggles
 *  │        Connected    │       TV settings  │
 *  ├──────────────────────────────────────────┤
 *  │ [art] Title                 (⏮)(⏯)(⏭) │   media platter, only real session actions
 *  │       Artist                             │
 *  ├──────────────────────────────────────────┤
 *  │  (in)     (scn)     (wx)     (zz)        │   round shortcuts with captions
 *  │ Inputs   Scenery   Weather   Sleep       │
 *  ├──────────────────────────────────────────┤
 *  │ Thu 5 Nov · 24° São Paulo     (⚙ TV)     │   quiet info line + TV settings
 *  └──────────────────────────────────────────┘
 * </pre>
 *
 * Every control runs an existing, real action. Wi-Fi and sound are honest shortcuts to the TV's settings,
 * because an app cannot toggle Wi-Fi or switch audio outputs without privileged access. The platters inside
 * use {@link GlassTone.Kind#INSET}, so the sheet is one layer of sampled glass, not glass stacked on glass.
 */
final class ControlCenter {
    interface Actions {
        void network();
        void sound();
        boolean hasInputs();
        void inputs();
        void scenery();
        void weather();
        void sleep();
        void tvSettings();
        void listen();
        void musicAccess();
        void previous();
        void togglePlay();
        void next();
    }

    static final int WIDTH_DP = 392;
    private static final Interpolator EASE = new PathInterpolator(0.2f, 0.9f, 0.3f, 1f);
    private static final int TEXT = 0xFFF5F6F8, DIM = 0xB3F5F6F8, FAINT = 0x8CF5F6F8;

    private final Context ctx;
    private final Actions actions;
    private final float density;
    private final boolean reducedMotion;
    private final Typeface regular, medium;

    private final FrameLayout overlay;
    private final LinearLayout card;
    private ImageView netIcon;
    private TextView netTitle, netDetail;
    private ImageView art;
    private TextView mediaTitle, mediaArtist;
    private ImageView prev, toggle, next;
    private View inputsColumn;
    private TextView footer;
    private boolean open;

    ControlCenter(Context ctx, FrameLayout parent, Typeface regular, Typeface medium, boolean reducedMotion,
                  Actions actions) {
        this.ctx = ctx;
        this.actions = actions;
        this.regular = regular;
        this.medium = medium;
        this.reducedMotion = reducedMotion;
        density = ctx.getResources().getDisplayMetrics().density;

        overlay = new FrameLayout(ctx);
        overlay.setVisibility(View.GONE);
        // Above the dock's elevated cards; no scrim, Home stays as it was around the sheet.
        overlay.setElevation(dp(40));
        overlay.setClipChildren(false);
        parent.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        SceneGlassDrawable.apply(card, 34f, GlassTone.Kind.PANEL);
        card.setClipToOutline(true);
        card.setElevation(dp(18));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(88), dp(42), 0);
        overlay.addView(card, lp);

        buildStatusRow();
        buildMedia();
        buildShortcuts();
        buildFooter();
        setTrack(null, null, null, false, false, false, false);
    }

    // ---- structure -----------------------------------------------------------------------------

    private void buildStatusRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setClipChildren(false);
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        View network = capsule(row, R.drawable.ic_wifi, "Wi-Fi", "Network settings", false, actions::network);
        netIcon = network.findViewWithTag("icon");
        netTitle = network.findViewWithTag("title");
        netDetail = network.findViewWithTag("detail");
        capsule(row, R.drawable.ic_cc_sound, "Sound", "TV sound settings", true, actions::sound);
    }

    /** A status capsule: glyph in a small disc, a title and the state; OK opens the matching settings. */
    private View capsule(LinearLayout row, int icon, String title, String detail, boolean second, Runnable action) {
        LinearLayout c = new LinearLayout(ctx);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(12), 0, dp(12), 0);
        SceneGlassDrawable.apply(c, -1f, GlassTone.Kind.INSET);
        focusable(c, action);
        ImageView disc = new ImageView(ctx);
        disc.setTag("icon");
        disc.setImageResource(icon);
        disc.setImageTintList(ctx.getColorStateList(R.color.scenic_icon_tint));
        disc.setScaleType(ImageView.ScaleType.CENTER);
        disc.setDuplicateParentStateEnabled(true);
        SceneGlassDrawable.apply(disc, -1f, GlassTone.Kind.INSET);
        c.addView(disc, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.setMarginStart(dp(10));
        c.addView(col, colLp);
        TextView t = text(15, medium, TEXT);
        t.setTag("title");
        t.setText(title);
        col.addView(t);
        TextView d = text(12.5f, regular, DIM);
        d.setTag("detail");
        d.setText(detail);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(2);
        col.addView(d, dLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (second) lp.setMarginStart(dp(10));
        row.addView(c, lp);
        c.setContentDescription(title);
        return c;
    }

    private void buildMedia() {
        LinearLayout platter = new LinearLayout(ctx);
        platter.setGravity(Gravity.CENTER_VERTICAL);
        platter.setPadding(dp(8), dp(8), dp(12), dp(8));
        platter.setClipChildren(false);
        SceneGlassDrawable.apply(platter, 24f, GlassTone.Kind.INSET);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        lp.topMargin = dp(10);
        card.addView(platter, lp);

        // Artwork and titles together: one target that opens Listen (or Music access when nothing plays).
        LinearLayout info = new LinearLayout(ctx);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(6), dp(4), dp(10), dp(4));
        info.setBackgroundResource(R.drawable.scenic_row_bg);
        focusable(info, () -> {
            if (mediaPlaying != null) actions.listen(); else actions.musicAccess();
        });
        platter.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(rounded(12));
        art.setClipToOutline(true);
        info.addView(art, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.setMarginStart(dp(12));
        info.addView(col, colLp);
        mediaTitle = text(15, medium, TEXT);
        col.addView(mediaTitle);
        mediaArtist = text(12.5f, regular, DIM);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(3);
        col.addView(mediaArtist, aLp);

        prev = smallButton(platter, R.drawable.ic_skip_prev, "Previous", actions::previous);
        toggle = smallButton(platter, R.drawable.ic_pause, "Play or pause", actions::togglePlay);
        next = smallButton(platter, R.drawable.ic_skip_next, "Next", actions::next);
    }

    private ImageView smallButton(LinearLayout row, int icon, String description, Runnable action) {
        ImageView b = new ImageView(ctx);
        b.setImageResource(icon);
        b.setImageTintList(ctx.getColorStateList(R.color.scenic_icon_tint));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(description);
        SceneGlassDrawable.apply(b, -1f, GlassTone.Kind.INSET);
        focusable(b, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMarginStart(dp(8));
        row.addView(b, lp);
        return b;
    }

    private void buildShortcuts() {
        LinearLayout row = new LinearLayout(ctx);
        row.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        card.addView(row, lp);
        inputsColumn = shortcut(row, R.drawable.ic_input, "Inputs", actions::inputs);
        shortcut(row, R.drawable.ic_picture, "Scenery", actions::scenery);
        shortcut(row, R.drawable.ic_cc_weather, "Weather", actions::weather);
        shortcut(row, R.drawable.ic_cc_sleep, "Sleep", actions::sleep);
    }

    /** A round glass button with its caption underneath; the column, not the caption, is the layout unit. */
    private View shortcut(LinearLayout row, int icon, String caption, Runnable action) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        ImageView b = new ImageView(ctx);
        b.setImageResource(icon);
        b.setImageTintList(ctx.getColorStateList(R.color.scenic_icon_tint));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(caption);
        SceneGlassDrawable.apply(b, -1f, GlassTone.Kind.INSET);
        focusable(b, action);
        col.addView(b, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView t = text(12.5f, medium, DIM);
        t.setText(caption);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(7);
        col.addView(t, tLp);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return col;
    }

    private void buildFooter() {
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        lp.topMargin = dp(14);
        card.addView(row, lp);
        footer = text(12.5f, regular, FAINT);
        footer.setFontFeatureSettings("tnum");
        footer.setPadding(dp(8), 0, dp(8), 0);
        row.addView(footer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout settings = new LinearLayout(ctx);
        settings.setGravity(Gravity.CENTER_VERTICAL);
        settings.setPadding(dp(12), 0, dp(16), 0);
        SceneGlassDrawable.apply(settings, -1f, GlassTone.Kind.INSET);
        settings.setContentDescription("TV settings");
        focusable(settings, actions::tvSettings);
        ImageView gear = new ImageView(ctx);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setImageTintList(ctx.getColorStateList(R.color.scenic_icon_tint));
        gear.setDuplicateParentStateEnabled(true);
        settings.addView(gear, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = text(13, medium, TEXT);
        t.setText("TV settings");
        t.setTextColor(ctx.getColorStateList(R.color.scenic_row_text));
        t.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.setMarginStart(dp(8));
        settings.addView(t, tLp);
        row.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---- state ---------------------------------------------------------------------------------

    private String mediaPlaying;

    /** The active session, or nulls when nothing plays. Only the actions the session offers are shown. */
    void setTrack(String title, String artist, Bitmap artwork, boolean playing,
                  boolean canPrevious, boolean canToggle, boolean canNext) {
        mediaPlaying = title;
        if (title == null) {
            mediaTitle.setText("Not playing");
            mediaArtist.setText("Music access");
            art.setImageResource(R.drawable.ic_music);
            art.setScaleType(ImageView.ScaleType.CENTER);
            if (!(art.getBackground() instanceof SceneGlassDrawable)) {
                art.setBackground(new SceneGlassDrawable(art, 12f, GlassTone.Kind.INSET));
            }
            canPrevious = canToggle = canNext = false;
        } else {
            mediaTitle.setText(title);
            mediaArtist.setText(artist == null ? "" : artist);
            if (artwork != null) {
                art.setImageBitmap(artwork);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setBackground(null);
            } else {
                art.setImageResource(R.drawable.ic_music);
                art.setScaleType(ImageView.ScaleType.CENTER);
                if (!(art.getBackground() instanceof SceneGlassDrawable)) {
                    art.setBackground(new SceneGlassDrawable(art, 12f, GlassTone.Kind.INSET));
                }
            }
        }
        toggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        show(prev, canPrevious);
        show(toggle, canToggle);
        show(next, canNext);
    }

    void setNetwork(NetworkSummary s) {
        netIcon.setImageResource(s.kind == NetworkSummary.ETHERNET ? R.drawable.ic_ethernet : R.drawable.ic_wifi);
        netIcon.setAlpha(s.kind == NetworkSummary.OFFLINE ? 0.55f : 1f);
        netTitle.setText(s.title);
        netDetail.setText(s.detail);
        ((View) netIcon.getParent()).setContentDescription(s.title + ", " + s.detail);
    }

    void setFooter(String line) {
        footer.setText(line);
    }

    private void show(View v, boolean on) {
        if (!on && v.isFocused()) focusFirst();
        v.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    // ---- open / close --------------------------------------------------------------------------

    boolean isOpen() {
        return open;
    }

    View root() {
        return overlay;
    }

    /** Grows from the trigger's corner (top right): the same spatial path in and out. */
    void open() {
        if (open) return;
        open = true;
        overlay.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        inputsColumn.setVisibility(actions.hasInputs() ? View.VISIBLE : View.GONE);
        overlay.setVisibility(View.VISIBLE);
        card.animate().cancel();
        card.setPivotX(dp(WIDTH_DP));
        card.setPivotY(0f);
        if (card.getAlpha() >= 1f) card.setAlpha(0f);
        if (reducedMotion) {
            card.setScaleX(1f);
            card.setScaleY(1f);
            card.setTranslationY(0f);
            card.animate().alpha(1f).setDuration(120).start();
        } else {
            if (card.getScaleX() >= 1f) {
                card.setScaleX(0.94f);
                card.setScaleY(0.94f);
                card.setTranslationY(-dp(8));
            }
            // Starts from whatever is on screen, so reopening mid-close reverses smoothly.
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(320)
                    .setInterpolator(EASE).withLayer().start();
        }
        // Only if still open: a Back before this runs has already handed focus back to Home.
        card.post(() -> { if (open) focusFirst(); });
    }

    void close(boolean animated) {
        if (!open) return;
        open = false;
        // While it fades out, Home's D-pad must not land on a control of the vanishing sheet.
        overlay.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        card.animate().cancel();
        if (!animated) {
            overlay.setVisibility(View.GONE);
            card.setAlpha(1f);
            return;
        }
        if (reducedMotion) {
            card.animate().alpha(0f).setDuration(100)
                    .withEndAction(this::hideIfClosed).start();
        } else {
            card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).translationY(-dp(6)).setDuration(200)
                    .setInterpolator(EASE).withLayer().withEndAction(this::hideIfClosed).start();
        }
    }

    private void hideIfClosed() {
        if (open) return;
        overlay.setVisibility(View.GONE);
        card.setAlpha(1f);
        card.setScaleX(1f);
        card.setScaleY(1f);
        card.setTranslationY(0f);
    }

    boolean contains(View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == card) return true;
        }
        return false;
    }

    /** First control in reading order: the network capsule. */
    void focusFirst() {
        View first = (View) netIcon.getParent();
        if (first.getVisibility() == View.VISIBLE) first.requestFocus();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void focusable(View v, Runnable action) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(x -> action.run());
        v.setOnFocusChangeListener((x, focused) -> {
            // Reduced motion keeps the brighter glass and rim as feedback, without the lift.
            if (reducedMotion) return;
            float s = focused ? 1.05f : 1f;
            x.animate().scaleX(s).scaleY(s).setDuration(180).setInterpolator(EASE).start();
        });
    }

    private TextView text(float sp, Typeface face, int color) {
        TextView t = new TextView(ctx);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private ViewOutlineProvider rounded(float radiusDp) {
        final float r = radiusDp * density;
        return new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        };
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
