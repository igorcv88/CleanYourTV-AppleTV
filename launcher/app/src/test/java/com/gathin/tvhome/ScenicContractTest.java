package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards for what the JVM cannot run (Views need a TV or an emulator): read the Scenic sources and check the
 * contracts the redesign must keep. Media commands go to the existing session, overlays trap the D-pad, the
 * progress line stays straight, no Android 12+ API or per-frame allocation creeps into the drawing code.
 */
public class ScenicContractTest {
    private static String src(String name) {
        File dir = new File("src/main/java/com/gathin/tvhome");
        if (!dir.isDirectory()) dir = new File("app/src/main/java/com/gathin/tvhome");
        if (!dir.isDirectory()) dir = new File("launcher/app/src/main/java/com/gathin/tvhome");
        try {
            return new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + name + " from " + dir.getAbsolutePath(), e);
        }
    }

    /** The body of the first method whose declaration matches {@code signature}, braces balanced. */
    private static String body(String source, String signature) {
        int at = source.indexOf(signature);
        if (at < 0) fail("missing " + signature);
        int open = source.indexOf('{', at), depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced " + signature);
    }

    /** Whitespace-insensitive: indentation and line endings (CRLF checkouts) do not matter. */
    private static void assertContains(String what, String haystack, String needle) {
        assertTrue(what + ": expected `" + needle + "`", squash(haystack).contains(squash(needle)));
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", " ");
    }

    // ---- media commands stay on the existing session ----------------------------------------------

    @Test public void controlCenterTransportUsesTheSameSessionCommandsAsHome() {
        String home = src("ScenicHome.java");
        assertContains("Home prev", home, "() -> nowPlaying.previous()");
        assertContains("Home toggle", home, "() -> nowPlaying.togglePlay()");
        assertContains("Home next", home, "() -> nowPlaying.next()");
        assertContains("CC prev", home, "public void previous() { nowPlaying.previous(); }");
        assertContains("CC toggle", home, "public void togglePlay() { nowPlaying.togglePlay(); }");
        assertContains("CC next", home, "public void next() { nowPlaying.next(); }");
    }

    @Test public void noNewAudioPathInTheVisualLayer() {
        for (String f : new String[]{"ControlCenter.java", "GlassCurrentView.java", "SceneGlassDrawable.java",
                "RibbonSwells.java", "ArtPalette.java", "GlassTone.java", "ScreenStack.java"}) {
            String s = src(f);
            for (String banned : new String[]{"android.media.", "AudioRecord", "Visualizer", "MediaPlayer",
                    "AudioTrack", "MediaController"}) {
                assertFalse(f + " must not touch audio (" + banned + ")", s.contains(banned));
            }
        }
    }

    // ---- D-pad: overlays keep focus, Dock/All Apps untouched ---------------------------------------

    @Test public void everyOverlayTrapsTheDpad() {
        String keys = body(src("ScenicHome.java"), "boolean dispatchKey(KeyEvent ev)");
        assertContains("Control Center trap", keys, "return next == null || !controlCenter.contains(next);");
        assertContains("Control Center lost focus", keys, "controlCenter.focusFirst();");
        assertContains("panel/screen trap", keys, "if (next == null || !isInside(next, container)) return true;");
        assertContains("listen trap", keys, "if (listening) return listenKey(f, code);");
        assertContains("text field caret", keys, "f instanceof EditText");
    }

    @Test public void dockAndAllAppsNavigationIsUnchanged() {
        String keys = body(src("ScenicHome.java"), "boolean dispatchKey(KeyEvent ev)");
        assertContains("Down from the dock opens All Apps", keys,
                "case ROW_DOCK:\n                    showApps(true, true);");
        assertContains("Up from the grid's first row returns to the dock", keys,
                "if (code == KeyEvent.KEYCODE_DPAD_UP && a.focusedGridIsFirstRow()) {\n                showApps(false, false);");
        String back = body(src("ScenicHome.java"), "boolean onBack()");
        assertContains("Back from All Apps lands on All Apps", back, "focusDock(dockAdapter.getItemCount() - 1)");
    }

    // ---- Settings backstack --------------------------------------------------------------------

    @Test public void backInsideSettingsGoesOneLevelUp() {
        String home = src("ScenicHome.java");
        String back = body(home, "boolean onBack()");
        assertContains("Back pops a level", back, "backScreen();");
        String backScreen = body(home, "private void backScreen()");
        assertContains("parent rebuilt", backScreen, "parent.reopen.run();");
        String close = body(home, "private void closeScreen()");
        assertContains("first level returns to Settings", close, "if (screenFromPanel) returnToPanel();");
        // Every screen registers how to rebuild itself (or null for a transient one).
        Matcher m = Pattern.compile("openScreen\\(([^;]*)\\);").matcher(home);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue("openScreen without a reopen action: " + m.group(), m.group(1).contains(","));
        }
        assertTrue(n >= 15);
    }

    // ---- progress line -------------------------------------------------------------------------

    @Test public void progressLineIsAStraightHorizontalSegment() {
        String ribbon = src("GlassCurrentView.java");
        String build = body(ribbon, "private void buildPath(int w, int h)");
        assertContains("starts on the centre line", build, "path.moveTo(pathStart, cy);");
        assertContains("ends on the centre line", build, "path.lineTo(pathEnd, cy);");
        assertFalse("no curves on the seek path", Pattern.compile("\\bpath\\.(cubicTo|quadTo|arcTo|rLineTo)").matcher(ribbon).find());
        String focus = body(ribbon, "protected void onFocusChanged(");
        assertContains("the view is never stretched", focus, "setScaleY(1f);");
    }

    // ---- material, motion, API level ---------------------------------------------------------------

    @Test public void everyGlassSurfaceUsesTheSharedMaterialKinds() {
        for (String f : new String[]{"ScenicHome.java", "ControlCenter.java", "MainActivity.java"}) {
            Matcher m = Pattern.compile("new SceneGlassDrawable\\(([^;]*)\\)").matcher(src(f));
            while (m.find()) assertContains(f + " raw tint", m.group(1), "GlassTone.Kind.");
        }
        assertFalse("one material only", new File("src/main/java/com/gathin/tvhome/LiquidGlassDrawable.java").exists());
    }

    @Test public void drawingAllocatesNothingPerFrame() {
        String glass = body(src("SceneGlassDrawable.java"), "public void draw(@NonNull Canvas c)");
        assertFalse("SceneGlassDrawable.draw allocates", glass.contains("new "));
        String sample = body(src("SceneGlassDrawable.java"), "private boolean drawSample(");
        // The one allocation is guarded: a new shader only when the scene bitmap itself changes.
        assertContains("shader cached per bitmap", sample, "if (bmp != shaderBitmap) {");
        String swells = body(src("GlassCurrentView.java"), "private void drawSwells(");
        assertFalse("swells allocate", swells.contains("new "));
        String onDraw = body(src("GlassCurrentView.java"), "protected void onDraw(Canvas c)");
        assertFalse("ribbon onDraw allocates", onDraw.contains("new "));
    }

    @Test public void reducedMotionIsRespected() {
        String home = src("ScenicHome.java");
        assertContains("ribbon swells off", home, "ribbon.setMotionEnabled(!reducedMotion);");
        assertContains("Control Center told", home, "new ControlCenter(a, layer, regular, medium, reducedMotion,");
        String cc = src("ControlCenter.java");
        assertContains("no focus lift", body(cc, "private void focusable(View v, Runnable action)"), "if (reducedMotion) return;");
        assertContains("fade only", body(cc, "void open()"), "card.animate().alpha(1f).setDuration(120).start();");
        String ribbon = src("GlassCurrentView.java");
        assertContains("no swells without motion", body(ribbon, "private boolean updateLevel(long now)"),
                "playing && animateWave ? 1f : 0f");
    }

    @Test public void focusIsVisibleOnEveryMaterial() {
        String glass = src("SceneGlassDrawable.java");
        assertContains("stateful", glass, "@Override public boolean isStateful() { return true; }");
        assertContains("reads focus", glass, "android.R.attr.state_focused");
    }

    @Test public void noApiAbove30InTheRedesign() {
        for (String f : new String[]{"ControlCenter.java", "GlassCurrentView.java", "SceneGlassDrawable.java",
                "Scene.java", "ScreenStack.java", "GlassTone.java", "RibbonSwells.java", "ArtPalette.java",
                "NetworkSummary.java"}) {
            String s = src(f);
            for (String banned : new String[]{"RenderEffect", "BlendModeColorFilter", "setRenderEffect",
                    "VERSION_CODES.S", "PixelCopy", "WindowInsets.Type", "setBlurBehindRadius"}) {
                assertFalse(f + " uses " + banned, s.contains(banned));
            }
        }
    }
}
