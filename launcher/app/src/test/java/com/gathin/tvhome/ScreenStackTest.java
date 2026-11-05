package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Settings → Wallpapers → Back returns to Settings, not Home; deeper levels unwind one at a time. */
public class ScreenStackTest {
    @Test public void backFromTheFirstLevelEmptiesTheStackSoSettingsReturns() {
        ScreenStack<String> s = new ScreenStack<>();
        assertEquals(ScreenStack.Move.ROOT, s.enter("Wallpapers", "wallpapers"));
        assertEquals(1, s.depth());
        assertNull(s.back());           // the screen layer closes; ScenicHome shows the Settings list again
        assertEquals(0, s.depth());
    }

    @Test public void threeLevelsUnwindOneAtATimeAndKeepTheirPlace() {
        ScreenStack<String> s = new ScreenStack<>();
        s.enter("Wallpapers", "w");
        s.top().scrollY = 240;
        s.top().focusIndex = 7;
        assertEquals(ScreenStack.Move.PUSH, s.enter("Forests", "f"));
        s.top().scrollY = 30;
        s.top().focusIndex = 2;
        assertEquals(ScreenStack.Move.PUSH, s.enter("4K · Forests", "k"));
        assertEquals(3, s.depth());

        ScreenStack.Entry<String> back = s.back();
        assertEquals("Forests", back.key);
        assertEquals(30, back.scrollY);
        assertEquals(2, back.focusIndex);
        back = s.back();
        assertEquals("Wallpapers", back.key);
        assertEquals(240, back.scrollY);
        assertEquals(7, back.focusIndex);
        assertNull(s.back());
    }

    @Test public void reopeningTheTopScreenIsARefreshNotANewLevel() {
        ScreenStack<String> s = new ScreenStack<>();
        s.enter("Wallpapers", "w1");
        assertEquals(ScreenStack.Move.REFRESH, s.enter("Wallpapers", "w2"));
        assertEquals(1, s.depth());
        assertEquals("w2", s.top().reopen);
    }

    @Test public void aBackRowOrASaveUnwindsToItsScreenInsteadOfStacking() {
        ScreenStack<String> s = new ScreenStack<>();
        s.enter("My Space", "m");
        s.enter("Add a title", "a");
        assertEquals(ScreenStack.Move.POP_TO, s.enter("My Space", "m"));
        assertEquals(1, s.depth());
        assertNull(s.back());
    }

    @Test public void transientDownloadScreensAreSkippedByBack() {
        ScreenStack<String> s = new ScreenStack<>();
        s.enter("Wallpapers", "w");
        s.enter("Forests", "f");
        s.enter("Downloading 4K photo", null);
        assertEquals("Forests", s.back().key);
        // A failed download offered Retry (refresh of the same transient screen), then Back.
        s.enter("Downloading 4K photo", null);
        assertEquals(ScreenStack.Move.REFRESH, s.enter("Downloading 4K photo", null));
        assertEquals("Forests", s.back().key);
    }

    @Test public void aTransientScreenUnderneathIsNeverReopened() {
        ScreenStack<String> s = new ScreenStack<>();
        s.enter("Brazil · drone videos", "v");
        s.enter("Downloading Brazil aerial", null);
        s.enter("Video credits", "c");      // hypothetical deeper level above a transient one
        assertEquals("Brazil · drone videos", s.back().key);
    }

    @Test public void clearForgetsEverything() {
        ScreenStack<String> s = new ScreenStack<>();
        s.enter("A", "a");
        s.enter("B", "b");
        s.clear();
        assertEquals(0, s.depth());
        assertNull(s.top());
        assertEquals(ScreenStack.Move.ROOT, s.enter("B", "b"));
    }
}
