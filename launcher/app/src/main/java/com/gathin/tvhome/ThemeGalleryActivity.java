package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full TV-remote-controlled Appearance gallery; previews are decoded lazily, off the UI thread. */
public final class ThemeGalleryActivity extends Activity {
    private static final int INK = 0xFFF4F5F8;
    private final List<String> ids = ThemeCatalog.ids();
    // RGB_565, sampled previews: cap to 4 MB even if the remote scrolls through all seventeen.
    private final LruCache<String, Bitmap> previews = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RecyclerView list;
    private View busy;
    private boolean destroyed;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(48);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(26), pad, dp(18));
        root.setBackgroundColor(0xFF0C141C);
        setContentView(root);

        TextView heading = label(getString(R.string.theme_gallery_title), 30, INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);
        TextView hint = label(getString(R.string.theme_gallery_hint), 15, 0xFFBBC6D0);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(4), 0, dp(12));
        root.addView(hint, hintLp);

        list = new RecyclerView(this);
        list.setHasFixedSize(true);
        list.setClipToPadding(false);
        list.setClipChildren(false);
        list.setPadding(dp(6), dp(6), dp(6), dp(14));
        list.setLayoutManager(new GridLayoutManager(this, 4));
        list.setAdapter(new GalleryAdapter());
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        list.post(() -> {
            String current = ThemeCatalog.selected(this);
            int idx = Math.max(0, ids.indexOf(current));
            list.scrollToPosition(idx);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(idx);
            if (holder != null) holder.itemView.requestFocus();
            else list.post(() -> {
                RecyclerView.ViewHolder visible = list.findViewHolderForAdapterPosition(idx);
                if (visible != null) visible.itemView.requestFocus();
            });
        });
    }

    private void select(String id) {
        if (busy != null) return;
        busy = getCurrentFocus();
        Toast.makeText(this, getString(R.string.theme_gallery_applying, ThemeCatalog.label(id)), Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            String error = null;
            try { ThemeCatalog.apply(getApplicationContext(), id); }
            catch (IOException | RuntimeException e) { error = e.getMessage(); }
            String result = error;
            runOnUiThread(() -> {
                if (destroyed) return;
                busy = null;
                if (result != null) {
                    Toast.makeText(this, getString(R.string.theme_gallery_failed, result), Toast.LENGTH_LONG).show();
                    return;
                }
                setResult(RESULT_OK);
                Toast.makeText(this, getString(R.string.theme_gallery_selected, ThemeCatalog.label(id)), Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void requestPreview(String id, ImageView view) {
        view.setTag(id);
        Bitmap cached = previews.get(id);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        worker.execute(() -> {
            Bitmap image = null;
            try (InputStream in = getAssets().open(id + "/screenshot.jpg")) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                image = BitmapFactory.decodeStream(in, null, opts);
            } catch (IOException ignored) { /* the card still has a coloured placeholder */ }
            Bitmap decoded = image;
            runOnUiThread(() -> {
                if (destroyed || decoded == null) return;
                previews.put(id, decoded);
                if (id.equals(view.getTag())) view.setImageBitmap(decoded);
            });
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.Tile> {
        @NonNull @Override public Tile onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout card = new LinearLayout(ThemeGalleryActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(dp(5), dp(5), dp(5), dp(8));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            lp.setMargins(dp(7), dp(6), dp(7), dp(6));
            card.setLayoutParams(lp);
            FrameLayout art = new FrameLayout(ThemeGalleryActivity.this);
            art.setBackground(round(0xFF20384B, 16));
            art.setClipToOutline(true);
            art.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            card.addView(art, new LinearLayout.LayoutParams(-1, dp(115)));
            ImageView preview = new ImageView(ThemeGalleryActivity.this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.addView(preview, new FrameLayout.LayoutParams(-1, -1));
            TextView name = label("", 17, INK);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
            nlp.setMargins(dp(3), dp(6), 0, 0);
            card.addView(name, nlp);
            TextView status = label("", 12, 0xFF9FB4C6);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(18));
            slp.setMargins(dp(3), 0, 0, 0);
            card.addView(status, slp);
            card.setOnFocusChangeListener((v, focused) -> {
                v.animate().cancel();
                v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                        .translationY(focused ? -dp(3) : 0).setDuration(215).start();
                v.setElevation(focused ? dp(10) : 0);
                card.setBackground(focused ? round(0x556CA6C5, 19) : null);
            });
            return new Tile(card, preview, art, name, status);
        }

        @Override public void onBindViewHolder(@NonNull Tile tile, int position) {
            String id = ids.get(position);
            tile.name.setText(ThemeCatalog.label(id));
            tile.status.setText(id.equals(ThemeCatalog.selected(ThemeGalleryActivity.this))
                    ? R.string.theme_gallery_active : R.string.theme_gallery_press_ok);
            tile.card.setContentDescription(ThemeCatalog.label(id));
            tile.card.setOnClickListener(v -> select(id));
            if ("scenic-glass".equals(id)) {
                tile.art.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF375B78, 0xFF121E30, 0xFF5490A0}));
            } else {
                tile.art.setBackground(round(0xFF20384B, 16));
            }
            requestPreview(id, tile.preview);
        }

        @Override public int getItemCount() { return ids.size(); }

        final class Tile extends RecyclerView.ViewHolder {
            final LinearLayout card;
            final ImageView preview;
            final FrameLayout art;
            final TextView name, status;
            Tile(LinearLayout card, ImageView preview, FrameLayout art, TextView name, TextView status) {
                super(card);
                this.card = card;
                this.preview = preview;
                this.art = art;
                this.name = name;
                this.status = status;
            }
        }
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
}
