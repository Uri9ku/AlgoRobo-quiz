package com.algorobo.quiz;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import java.util.LinkedHashMap;
import java.util.Map;

public class BarChartView extends View {
    private Map<String, int[]> typeStats = new LinkedHashMap<>();
    private Paint barPaint, textPaint;
    private float progress = 0f;
    private ValueAnimator animator;

    public BarChartView(Context c) { super(c); init(); }
    public BarChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(getResources().getColor(R.color.chart_line));
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(R.color.chart_text));
        textPaint.setTextSize(28f);
    }

    public void setData(Map<String, int[]> stats) {
        this.typeStats = stats;
        startAnimation();
    }

    private void startAnimation() {
        if (animator != null) animator.cancel();
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (typeStats.isEmpty()) return;

        float w = getWidth(), h = getHeight();
        float padLeft = 40f, padRight = 20f, padTop = 30f, padBottom = 60f;
        float chartW = w - padLeft - padRight;
        float chartH = h - padTop - padBottom;

        int n = typeStats.size();
        float slot = chartW / n;
        float barW = slot * 0.5f;

        int i = 0;
        for (Map.Entry<String, int[]> e : typeStats.entrySet()) {
            String type = e.getKey();
            int total = e.getValue()[0];
            int right = e.getValue()[1];
            float ratio = total > 0 ? (float) right / total : 0f;

            float left = padLeft + slot * i + (slot - barW) / 2;
            float barH = chartH * ratio * progress;
            float top = padTop + chartH - barH;
            canvas.drawRect(left, top, left + barW, padTop + chartH, barPaint);

            // 百分比（动画完成后才显示）
            if (progress >= 0.999f) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(getResources().getColor(R.color.chart_percent_text));
                p.setTextSize(26f);
                String pct = (int) (ratio * 100) + "%";
                canvas.drawText(pct, left - 8, top - 10, p);
            }

            // 题型标签
            canvas.drawText(shortType(type), left - 20, h - 20, textPaint);
            i++;
        }
    }

    private String shortType(String t) {
        if (t.length() > 4) return t.substring(0, 4);
        return t;
    }
}