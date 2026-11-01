package com.gathin.tvhome;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Back-stack of focus targets for Scenic Home's overlays (Listen, the Settings panel, a screen, Apps). Opening
 * a layer pushes what had focus; Back pops it and gives that exact target back. Generic so the rule is tested
 * without views.
 */
final class FocusMemory<T> {
    private final Deque<Object[]> stack = new ArrayDeque<>();

    void push(String layer, T focused) {
        stack.push(new Object[]{layer, focused});
    }

    /** The top layer's name, or null. */
    String top() {
        return stack.isEmpty() ? null : (String) stack.peek()[0];
    }

    boolean isOpen(String layer) {
        for (Object[] o : stack) if (layer.equals(o[0])) return true;
        return false;
    }

    /** Pops the top layer and returns what had focus before it opened (may be null). */
    @SuppressWarnings("unchecked")
    T pop() {
        return stack.isEmpty() ? null : (T) stack.pop()[1];
    }

    int depth() {
        return stack.size();
    }

    void clear() {
        stack.clear();
    }
}
