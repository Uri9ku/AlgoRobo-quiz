package com.algorobo.quiz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 草稿纸手绘区域：支持自由手绘，可清空画布，并支持笔画数据的导出/恢复（用于按题保存草稿）。
 */
public class DrawPadView extends View {

    /** 可序列化的笔画数据：路径坐标点 + 颜色 + 线宽。 */
    public static class StrokeData implements Serializable {
        private static final long serialVersionUID = 1L;
        public float[] points; // [x0,y0,x1,y1,...]
        public int color;
        public float strokeWidth;
    }

    private static class Stroke {
        final Path path = new Path();
        final ArrayList<Float> xs = new ArrayList<>();
        final ArrayList<Float> ys = new ArrayList<>();
        float strokeWidth;
        int color;
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke current;
    private Paint paint;

    public DrawPadView(Context context) {
        super(context);
        init();
    }

    public DrawPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawPadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        paint = new Paint();
        paint.setColor(getResources().getColor(R.color.ink_default));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4 * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setAntiAlias(true);
        paint.setDither(true);
    }

    /** 清空所有笔画。 */
    public void clear() {
        strokes.clear();
        current = null;
        invalidate();
    }

    /** 导出当前所有笔画为可序列化数据（包括尚未抬笔的 current）。 */
    public ArrayList<StrokeData> getStrokeData() {
        ArrayList<StrokeData> data = new ArrayList<>();
        List<Stroke> all = new ArrayList<>(strokes);
        if (current != null) all.add(current);
        for (Stroke s : all) {
            StrokeData d = new StrokeData();
            int n = s.xs.size();
            d.points = new float[n * 2];
            for (int i = 0; i < n; i++) {
                d.points[i * 2] = s.xs.get(i);
                d.points[i * 2 + 1] = s.ys.get(i);
            }
            d.color = s.color;
            d.strokeWidth = s.strokeWidth;
            data.add(d);
        }
        return data;
    }

    /** 根据导出的笔画数据恢复画布。 */
    public void restoreStrokes(List<StrokeData> data) {
        strokes.clear();
        current = null;
        if (data != null) {
            for (StrokeData d : data) {
                if (d.points == null || d.points.length < 2) continue;
                Stroke s = new Stroke();
                s.color = d.color;
                s.strokeWidth = d.strokeWidth;
                s.path.moveTo(d.points[0], d.points[1]);
                s.xs.add(d.points[0]);
                s.ys.add(d.points[1]);
                for (int i = 2; i + 1 < d.points.length; i += 2) {
                    s.path.lineTo(d.points[i], d.points[i + 1]);
                    s.xs.add(d.points[i]);
                    s.ys.add(d.points[i + 1]);
                }
                strokes.add(s);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Stroke s : strokes) {
            paint.setColor(s.color);
            paint.setStrokeWidth(s.strokeWidth);
            canvas.drawPath(s.path, paint);
        }
        if (current != null) {
            paint.setColor(current.color);
            paint.setStrokeWidth(current.strokeWidth);
            canvas.drawPath(current.path, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                current = new Stroke();
                current.color = paint.getColor();
                current.strokeWidth = paint.getStrokeWidth();
                current.path.moveTo(x, y);
                current.xs.add(x);
                current.ys.add(y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (current != null) {
                    current.path.lineTo(x, y);
                    current.xs.add(x);
                    current.ys.add(y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (current != null) {
                    current.path.lineTo(x, y);
                    current.xs.add(x);
                    current.ys.add(y);
                    strokes.add(current);
                    current = null;
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
