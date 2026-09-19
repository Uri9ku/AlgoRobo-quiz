package com.algorobo.quiz;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class LineChartView extends View {
    private int[] data = new int[7];
    private Paint linePaint, pointPaint, textPaint, axisPaint;
    private float progress = 0f;
    private ValueAnimator animator;

    public LineChartView(Context c) { super(c); init(); }
    public LineChartView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(getResources().getColor(R.color.chart_line));
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);
        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(getResources().getColor(R.color.chart_line));
        pointPaint.setStyle(Paint.Style.FILL);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(R.color.chart_text));
        textPaint.setTextSize(28f);
        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(getResources().getColor(R.color.chart_grid));
        axisPaint.setStrokeWidth(1f);
    }

    public void setData(int[] d) {
        this.data = d;
        startAnimation();
    }

    private void startAnimation() {
        if (animator != null) animator.cancel();
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
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
        float w = getWidth(), h = getHeight();
        float padLeft = 40f, padRight = 20f, padTop = 40f, padBottom = 60f;
        float chartW = w - padLeft - padRight;
        float chartH = h - padTop - padBottom;
        int max = 1;
        for (int v : data) if (v > max) max = v;

        // 背景网格线
        for (int i = 0; i <= 4; i++) {
            float y = padTop + chartH * i / 4;
            canvas.drawLine(padLeft, y, w - padRight, y, axisPaint);
        }
        if (max == 0) max = 1;
        float[] xs = new float[7];
        float[] ys = new float[7];
        for (int i = 0; i < 7; i++) {
            xs[i] = padLeft + chartW * i / 6;
            ys[i] = padTop + chartH - (chartH * data[i] / max);
        }

        // 折线：按 progress 从左向右渐显
        int visibleCount = Math.max(1, (int) Math.ceil(progress * 7));
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < visibleCount; i++) {
            if (i == 6) {
                path.lineTo(xs[i], ys[i]);
            } else if (i == visibleCount - 1) {
                // 最后一小段根据 progress 插值，实现平滑生长
                float segProgress = progress * 7 - (visibleCount - 1);
                float px = xs[i - 1] + (xs[i] - xs[i - 1]) * segProgress;
                float py = ys[i - 1] + (ys[i] - ys[i - 1]) * segProgress;
                path.lineTo(px, py);
            } else {
                path.lineTo(xs[i], ys[i]);
            }
        }
        canvas.drawPath(path, linePaint);

        // 数据点只绘制已完全出现的点
        for (int i = 0; i < visibleCount; i++) {
            if (i == visibleCount - 1 && visibleCount < 7 && progress < 1f) continue;
            canvas.drawCircle(xs[i], ys[i], 6f, pointPaint);
        }
        // x轴标签
        for (int i = 0; i < 7; i++) {
            String label = new String[]{"6天前","5天前","4天前","3天前","2天前","昨天","今天"}[i];
            canvas.drawText(label, xs[i] - 20, h - 20, textPaint);
        }
    }
}