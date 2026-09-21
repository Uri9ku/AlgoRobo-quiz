package com.algorobo.quiz;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 数字/文本变化动画：新值从对侧滑入、旧值滑出（首页「错题本/收藏题」角标同款）。
 * 规则：数值变大 → 新值自上方滑入、旧值向下滑出；数值变小 → 新值自下方滑入、旧值向上滑出。
 * 要求容器为固定高度且 clipChildren=true，否则滑动过程会溢出。
 */
public final class CountSlideAnim {

    public static final int DEFAULT_DURATION = 260;

    private CountSlideAnim() {
    }

    /** 播放变化动画（文本相同或容器未布局时直接赋值）。 */
    public static void play(FrameLayout box, TextView tv, String oldText, String newText) {
        play(box, tv, oldText, newText, DEFAULT_DURATION);
    }

    public static void play(FrameLayout box, TextView tv, String oldText, String newText, int duration) {
        if (box == null || tv == null) return;
        // 清掉上次动画遗留的临时视图
        for (int i = box.getChildCount() - 1; i >= 0; i--) {
            View child = box.getChildAt(i);
            if (child != tv) box.removeView(child);
        }
        int h = box.getHeight();
        String oldS = oldText == null ? "" : oldText;
        String newS = newText == null ? "" : newText;
        if (h <= 0 || oldS.equals(newS)) {
            tv.setTranslationY(0f);
            tv.setText(newS);
            return;
        }
        boolean increase = number(newS) >= number(oldS);
        if (!oldS.isEmpty()) {
            TextView outgoing = clone(tv, oldS, h);
            box.addView(outgoing);
            outgoing.animate().translationY(increase ? h : -h)
                    .setDuration(duration)
                    .withEndAction(() -> box.removeView(outgoing))
                    .start();
        }
        tv.setText(newS);
        tv.setTranslationY(increase ? -h : h);
        tv.animate().translationY(0f).setDuration(duration).start();
    }

    /** 取字符串里的第一个整数用于判断大小方向；没有数字时按「变大」处理。 */
    private static int number(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignore) { }
        }
        return 0;
    }

    private static TextView clone(TextView src, String text, int heightPx) {
        TextView v = new TextView(src.getContext());
        v.setText(text);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, src.getTextSize());
        v.setTextColor(src.getCurrentTextColor());
        v.setTypeface(src.getTypeface());
        v.setGravity(src.getGravity());
        v.setIncludeFontPadding(src.getIncludeFontPadding());
        if (src.getBackground() != null) {
            v.setBackground(src.getBackground().getConstantState() == null
                    ? src.getBackground() : src.getBackground().getConstantState().newDrawable());
        }
        v.setPadding(src.getPaddingLeft(), src.getPaddingTop(), src.getPaddingRight(), src.getPaddingBottom());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, heightPx);
        lp.gravity = android.view.Gravity.CENTER;
        v.setLayoutParams(lp);
        return v;
    }
}
