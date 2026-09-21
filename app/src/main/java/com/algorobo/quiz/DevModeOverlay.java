package com.algorobo.quiz;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 全局「开发者模式」悬浮控件。
 * <p>
 * - 每个页面（Activity）右上/右侧默认显示一个可拖动悬浮球「开发者模式」；
 * - 点击悬浮球进入开发者模式：点击页面任意控件即可选中，选中控件被红色阴影覆盖，
 *   并弹出可拖动的信息面板（控件ID、类名、文本、尺寸、内外边距、背景、文字样式、层级路径等）；
 * - 面板上的滑块可在「父控件 ←→ 子控件」之间切换当前选中的控件；面板带「复制信息」按钮；
 * - 再次点击悬浮球退出开发者模式。
 */
public final class DevModeOverlay {

    private DevModeOverlay() {
    }

    private static final String PREFS = "dev_overlay";

    /** 是否处于开发者模式（会话内保持，切换页面不丢失）。 */
    private static boolean active;
    /** 当前 resumd 的 Activity。 */
    private static Activity host;
    /** 依附在 android.R.id.content 上的浮层容器。 */
    private static FrameLayout overlay;
    /** 页面根布局：用于命中测试与手势转发。 */
    private static View contentRoot;

    private static SpyView spy;
    private static HighlightView highlight;
    private static View panel;
    private static TextView bubble;
    private static TextView infoView;
    private static TextView levelView;
    private static SeekBar levelBar;
    private static ViewTreeObserver.OnPreDrawListener preDraw;

    private static View selected;
    private static final List<View> chain = new ArrayList<>();
    private static boolean updatingBar;

    private static int bubbleX = Integer.MIN_VALUE, bubbleY = Integer.MIN_VALUE;
    private static int panelX = Integer.MIN_VALUE, panelY = Integer.MIN_VALUE;

    // ===================== 生命周期入口（由 App 注册的回调调用） =====================

    public static void onActivityResumed(Activity a) {
        host = a;
        attach(a);
    }

    public static void onActivityPaused(Activity a) {
        if (host == a) {
            detach();
            host = null;
        }
    }

    public static boolean isActive() {
        return active;
    }

    // ===================== 挂载 / 卸载浮层 =====================

    private static void attach(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        contentRoot = content.getChildAt(0);

        overlay = new FrameLayout(a);
        overlay.setClipChildren(false);
        content.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bubble = new TextView(a);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(a, 10), dp(a, 6), dp(a, 10), dp(a, 6));
        bubble.setAlpha(0.88f);
        // 悬浮球必须始终盖在信息面板之上（API21+ 绘制顺序看 elevation）
        bubble.setElevation(dp(a, 24));
        overlay.addView(bubble, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setupBubbleTouch(a);
        updateBubbleStyle();

        if (active) enableDevViews(a);
        overlay.post(() -> placeBubble(a, false));
    }

    private static void detach() {
        removePreDraw();
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        overlay = null;
        contentRoot = null;
        spy = null;
        highlight = null;
        panel = null;
        bubble = null;
        infoView = null;
        levelView = null;
        levelBar = null;
        selected = null;
        chain.clear();
    }

    // ===================== 悬浮球 =====================

    private static void setupBubbleTouch(final Activity a) {
        final int slop = ViewConfiguration.get(a).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float startRawX, startRawY, baseX, baseY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = e.getRawX();
                        startRawY = e.getRawY();
                        baseX = bubble.getX();
                        baseY = bubble.getY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startRawX;
                        float dy = e.getRawY() - startRawY;
                        if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true;
                        if (moved && overlay != null) {
                            bubbleX = (int) (baseX + dx);
                            bubbleY = (int) (baseY + dy);
                            clampBubble(overlay.getWidth(), overlay.getHeight());
                            bubble.setX(bubbleX);
                            bubble.setY(bubbleY);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (moved) {
                            prefs(a).edit().putInt("bx", bubbleX).putInt("by", bubbleY).apply();
                        } else {
                            v.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        bubble.setOnClickListener(v -> setActive(!active));
    }

    private static void placeBubble(Context c, boolean save) {
        if (bubble == null || overlay == null) return;
        int w = overlay.getWidth(), h = overlay.getHeight();
        if (w <= 0 || h <= 0) return;
        if (bubbleX == Integer.MIN_VALUE) {
            SharedPreferences sp = prefs(c);
            bubbleX = sp.getInt("bx", Integer.MIN_VALUE);
            bubbleY = sp.getInt("by", Integer.MIN_VALUE);
        }
        if (bubbleX == Integer.MIN_VALUE) {
            bubbleX = w - bubble.getWidth() - dp(c, 4);
            bubbleY = (int) (h * 0.68f);
        }
        clampBubble(w, h);
        bubble.setX(bubbleX);
        bubble.setY(bubbleY);
        if (save) prefs(c).edit().putInt("bx", bubbleX).putInt("by", bubbleY).apply();
    }

    private static void clampBubble(int w, int h) {
        if (bubble == null || w <= 0 || h <= 0) return;
        int bw = Math.max(1, bubble.getWidth());
        int bh = Math.max(1, bubble.getHeight());
        bubbleX = Math.max(0, Math.min(bubbleX, w - bw));
        bubbleY = Math.max(0, Math.min(bubbleY, h - bh));
    }

    private static void updateBubbleStyle() {
        if (bubble == null) return;
        bubble.setText(active ? "退出开发者模式" : "开发者模式");
        bubble.setBackgroundResource(active
                ? R.drawable.bg_dev_bubble : R.drawable.bg_dev_bubble_idle);
    }

    // ===================== 开发者模式开关 =====================

    public static void setActive(boolean on) {
        active = on;
        Activity a = host;
        if (a != null) {
            if (on) enableDevViews(a);
            else disableDevViews();
        }
        updateBubbleStyle();
        if (a != null) {
            Toast.makeText(a, on ? "开发者模式：已开启，点击控件查看信息" : "开发者模式：已关闭",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static void enableDevViews(Activity a) {
        if (overlay == null || spy != null) return;

        spy = new SpyView(a);
        overlay.addView(spy, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        highlight = new HighlightView(a);
        highlight.setVisibility(View.GONE);
        overlay.addView(highlight, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = LayoutInflater.from(a).inflate(R.layout.dev_panel, overlay, false);
        panel.setVisibility(View.GONE);
        panel.setElevation(dp(a, 8));
        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (bubble != null) bubble.bringToFront();
        bindPanel(a);

        preDraw = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (highlight != null && highlight.getVisibility() == View.VISIBLE) highlight.refresh();
                return true;
            }
        };
        overlay.getViewTreeObserver().addOnPreDrawListener(preDraw);
    }

    private static void disableDevViews() {
        removePreDraw();
        selected = null;
        chain.clear();
        for (View v : new View[]{spy, highlight, panel}) {
            if (v != null && v.getParent() instanceof ViewGroup) {
                ((ViewGroup) v.getParent()).removeView(v);
            }
        }
        spy = null;
        highlight = null;
        panel = null;
        infoView = null;
        levelView = null;
        levelBar = null;
    }

    private static void removePreDraw() {
        if (preDraw != null && overlay != null) {
            overlay.getViewTreeObserver().removeOnPreDrawListener(preDraw);
        }
        preDraw = null;
    }

    // ===================== 信息面板 =====================

    private static void bindPanel(final Activity a) {
        infoView = panel.findViewById(R.id.devInfo);
        levelView = panel.findViewById(R.id.devLevel);
        levelBar = panel.findViewById(R.id.devLevelBar);

        levelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || updatingBar) return;
                if (progress < 0 || progress >= chain.size()) return;
                View v = chain.get(progress);
                selected = v;
                if (highlight != null) highlight.setTarget(v);
                showInfo(v, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        panel.findViewById(R.id.devClose).setOnClickListener(v -> clearSelection());
        panel.findViewById(R.id.devClear).setOnClickListener(v -> clearSelection());
        panel.findViewById(R.id.devCopy).setOnClickListener(v -> copyInfo(a));

        // 拖动标题栏移动弹窗
        View header = panel.findViewById(R.id.devHeader);
        header.setOnTouchListener(new View.OnTouchListener() {
            float startRawX, startRawY, baseX, baseY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = e.getRawX();
                        startRawY = e.getRawY();
                        baseX = panel.getX();
                        baseY = panel.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float x = baseX + (e.getRawX() - startRawX);
                        float y = baseY + (e.getRawY() - startRawY);
                        int w = overlay.getWidth(), h = overlay.getHeight();
                        x = Math.max(0, Math.min(x, Math.max(0, w - panel.getWidth())));
                        y = Math.max(0, Math.min(y, Math.max(0, h - panel.getHeight())));
                        panel.setX(x);
                        panel.setY(y);
                        panelX = (int) x;
                        panelY = (int) y;
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        panelX = (int) panel.getX();
                        panelY = (int) panel.getY();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private static void placePanel() {
        if (panel == null || overlay == null) return;
        int w = overlay.getWidth(), h = overlay.getHeight();
        if (w <= 0 || h <= 0) return;
        if (panelX == Integer.MIN_VALUE) {
            panelX = dp(panel.getContext(), 8);
            panelY = dp(panel.getContext(), 110);
        }
        panel.post(() -> {
            int x = Math.max(0, Math.min(panelX, Math.max(0, w - panel.getWidth())));
            int y = Math.max(0, Math.min(panelY, Math.max(0, h - panel.getHeight())));
            panel.setX(x);
            panel.setY(y);
            panelX = x;
            panelY = y;
        });
    }

    private static void clearSelection() {
        selected = null;
        chain.clear();
        if (highlight != null) {
            highlight.setTarget(null);
            highlight.setVisibility(View.GONE);
        }
        if (panel != null) panel.setVisibility(View.GONE);
    }

    private static void copyInfo(Context c) {
        if (infoView == null) return;
        CharSequence txt = infoView.getText();
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("控件信息", txt == null ? "" : txt));
        Toast.makeText(c, "已复制控件信息", Toast.LENGTH_SHORT).show();
    }

    // ===================== 选中逻辑 =====================

    private static void selectAt(float localX, float localY) {
        if (contentRoot == null || spy == null) return;
        int[] loc = new int[2];
        spy.getLocationOnScreen(loc);
        View hit = findDeepest(contentRoot, loc[0] + localX, loc[1] + localY);
        if (hit == null) hit = contentRoot;
        showSelection(hit);
    }

    private static void showSelection(View v) {
        selected = v;
        chain.clear();
        chain.addAll(buildChain(v));
        if (highlight != null) {
            highlight.setTarget(v);
            highlight.setVisibility(View.VISIBLE);
        }
        int idx = chain.size() - 1;
        updatingBar = true;
        if (levelBar != null) {
            levelBar.setMax(Math.max(1, chain.size() - 1));
            levelBar.setProgress(idx);
        }
        updatingBar = false;
        showInfo(v, idx);
        if (panel != null && panel.getVisibility() != View.VISIBLE) {
            panel.setVisibility(View.VISIBLE);
            placePanel();
        }
    }

    private static void showInfo(View v, int index) {
        if (infoView != null) infoView.setText(buildInfo(v));
        if (levelView != null) {
            String tip = chain.size() > 1 ? "（拖动滑块切换父/子控件）" : "（无父级可切换）";
            levelView.setText("层级 " + (index + 1) + "/" + chain.size() + " · "
                    + v.getClass().getSimpleName() + " " + tip);
        }
    }

    /** 从点击处向下找最深处包含该屏幕坐标的可见控件。 */
    private static View findDeepest(View v, float sx, float sy) {
        if (v == null || v.getVisibility() != View.VISIBLE) return null;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (sx < loc[0] || sx >= loc[0] + v.getWidth()) return null;
        if (sy < loc[1] || sy >= loc[1] + v.getHeight()) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View r = findDeepest(g.getChildAt(i), sx, sy);
                if (r != null) return r;
            }
        }
        return v;
    }

    /** 由页面根到目标控件的层级链（0 = 最外层）。 */
    private static List<View> buildChain(View target) {
        List<View> out = new ArrayList<>();
        if (target == null) return out;
        out.add(target);
        View v = target;
        while (true) {
            ViewParent p = v.getParent();
            if (!(p instanceof View)) break;
            v = (View) p;
            out.add(0, v);
            if (v == contentRoot) break;
        }
        return out;
    }

    // ===================== 控件信息文本 =====================

    private static String buildInfo(View v) {
        Resources r = v.getResources();
        StringBuilder sb = new StringBuilder();

        sb.append("控件类：").append(v.getClass().getName()).append('\n');
        sb.append("控件ID：").append(idText(v)).append('\n');
        sb.append("控件名字：").append(nameText(v)).append('\n');
        sb.append("可见性：").append(visibilityText(v.getVisibility()))
                .append("   透明=").append(round(v.getAlpha())).append('\n');
        sb.append(String.format(Locale.US, "尺寸：%d x %d px （%s x %s dp）%s\n",
                v.getWidth(), v.getHeight(), dpText(r, v.getWidth()), dpText(r, v.getHeight()),
                measureText(v)));
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        sb.append("屏幕位置：x=").append(loc[0]).append("  y=").append(loc[1]).append('\n');
        sb.append(String.format(Locale.US, "内边距：left=%d top=%d right=%d bottom=%d\n",
                v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom()));

        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            sb.append(String.format(Locale.US, "外边距：left=%d top=%d right=%d bottom=%d\n",
                    m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin));
        }
        if (lp != null) {
            sb.append("布局参数：").append(lp.getClass().getSimpleName());
            if (lp instanceof FrameLayout.LayoutParams) {
                sb.append("  gravity=").append(gravityText(((FrameLayout.LayoutParams) lp).gravity));
            } else if (lp instanceof LinearLayout.LayoutParams) {
                sb.append("  weight=").append(((LinearLayout.LayoutParams) lp).weight);
            }
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) sb.append("  width=match_parent");
            else if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) sb.append("  width=wrap_content");
            if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) sb.append("  height=match_parent");
            else if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) sb.append("  height=wrap_content");
            sb.append('\n');
        }

        sb.append("背景：").append(backgroundText(v)).append('\n');
        if (v instanceof TextView) sb.append(styleText((TextView) v));

        sb.append("属性：clickable=").append(v.isClickable())
                .append("  enabled=").append(v.isEnabled())
                .append("  focusable=").append(v.isFocusable())
                .append("  selected=").append(v.isSelected()).append('\n');
        if (v.getContentDescription() != null && v.getContentDescription().length() > 0) {
            sb.append("contentDescription：").append(v.getContentDescription()).append('\n');
        }
        if (v.getTag() != null) sb.append("tag：").append(v.getTag()).append('\n');
        sb.append("elevation：").append(round(v.getElevation())).append(" px\n");
        sb.append("层级路径：").append(pathText(v)).append('\n');
        return sb.toString();
    }

    private static String idText(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "无（未设置 android:id）";
        String hex = "0x" + Integer.toHexString(id);
        String name = null;
        try {
            name = v.getResources().getResourceName(id);
        } catch (Exception ignore) {
        }
        return name == null ? hex : name + "  （" + hex + "）";
    }

    private static String nameText(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) return "\"" + t.toString().replace("\n", "\\n") + "\"";
        }
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.length() > 0) return "\"" + cd + "\"（contentDescription）";
        if (v instanceof ViewGroup) {
            return "(容器，共 " + ((ViewGroup) v).getChildCount() + " 个子控件)";
        }
        return "(无文本)";
    }

    private static String styleText(TextView t) {
        Resources r = t.getResources();
        float density = r.getDisplayMetrics().scaledDensity;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "文字样式：字号 %.1f sp （%.0f px）  颜色 #%08X",
                t.getTextSize() / density, t.getTextSize(), t.getCurrentTextColor())).append('\n');
        sb.append("字形：").append(typefaceText(t.getTypeface()))
                .append("  gravity=").append(gravityText(t.getGravity()));
        if (t.getMaxLines() > 0) sb.append("  maxLines=").append(t.getMaxLines());
        if (t.getEllipsize() != null) sb.append("  ellipsize=").append(t.getEllipsize());
        sb.append('\n');
        if (t.getHint() != null) sb.append("hint：").append(t.getHint()).append('\n');
        sb.append("行间距：").append(round(t.getLineSpacingExtra())).append(" px\n");
        return sb.toString();
    }

    private static String typefaceText(android.graphics.Typeface tf) {
        if (tf == null) return "默认";
        if (tf.isBold() && tf.isItalic()) return "粗体 + 斜体";
        if (tf.isBold()) return "粗体";
        if (tf.isItalic()) return "斜体";
        return "常规";
    }

    private static String backgroundText(View v) {
        Drawable bg = v.getBackground();
        if (bg == null) return "无";
        String res = null;
        try {
            Field f = View.class.getDeclaredField("mBackgroundResource");
            f.setAccessible(true);
            int rid = (int) f.get(v);
            if (rid != 0) res = shortRes(v.getResources().getResourceName(rid));
        } catch (Exception ignore) {
        }
        if (bg instanceof ColorDrawable) {
            String color = String.format(Locale.US, "#%08X", ((ColorDrawable) bg).getColor());
            return color + (res == null ? "" : "  （" + res + "）");
        }
        return (res == null ? bg.getClass().getName() : res) + "  （" + bg.getClass().getSimpleName() + "）";
    }

    private static String shortRes(String full) {
        if (full == null) return null;
        int slash = full.indexOf('/');
        int colon = full.indexOf(':');
        if (slash > 0 && colon > 0 && slash > colon) {
            return "@" + full.substring(colon + 1, slash) + "/" + full.substring(slash + 1);
        }
        return full;
    }

    private static String measureText(View v) {
        int w = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        try {
            v.measure(w, h);
            int mw = v.getMeasuredWidth(), mh = v.getMeasuredHeight();
            if (mw != v.getWidth() || mh != v.getHeight()) {
                return "  内容尺寸=" + mw + " x " + mh + " px";
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private static String pathText(View v) {
        List<View> c = buildChain(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.size(); i++) {
            if (i > 0) sb.append(" › ");
            View it = c.get(i);
            sb.append(it.getClass().getSimpleName());
            int id = it.getId();
            if (id != View.NO_ID) {
                try {
                    String n = it.getResources().getResourceName(id);
                    int slash = n.indexOf('/');
                    sb.append('#').append(slash >= 0 ? n.substring(slash + 1) : n);
                } catch (Exception ignore) {
                }
            }
        }
        return sb.toString();
    }

    private static String visibilityText(int vis) {
        switch (vis) {
            case View.VISIBLE:
                return "VISIBLE（可见）";
            case View.INVISIBLE:
                return "INVISIBLE（占位不可见）";
            case View.GONE:
                return "GONE（不占位）";
            default:
                return String.valueOf(vis);
        }
    }

    private static String gravityText(int g) {
        if (g == 0) return "无";
        StringBuilder sb = new StringBuilder();
        if ((g & Gravity.CENTER) == Gravity.CENTER) {
            sb.append("center");
        } else {
            if ((g & Gravity.LEFT) != 0) sb.append("left");
            else if ((g & Gravity.RIGHT) != 0) sb.append("right");
            else if ((g & Gravity.CENTER_HORIZONTAL) != 0) sb.append("center_horizontal");
            if ((g & Gravity.TOP) != 0) sb.append(sb.length() > 0 ? "|top" : "top");
            else if ((g & Gravity.BOTTOM) != 0) sb.append(sb.length() > 0 ? "|bottom" : "bottom");
            else if ((g & Gravity.CENTER_VERTICAL) != 0) {
                sb.append(sb.length() > 0 ? "|center_vertical" : "center_vertical");
            }
        }
        if (sb.length() == 0) sb.append("0x").append(Integer.toHexString(g));
        return sb.toString();
    }

    private static String dpText(Resources r, int px) {
        return String.format(Locale.US, "%.1f", px / r.getDisplayMetrics().density);
    }

    private static String round(float f) {
        return String.format(Locale.US, "%.2f", f);
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ===================== 触点拦截层 =====================

    /**
     * 覆盖全屏的透明层：单击 → 选中控件；滑动 → 把整个手势转发给页面根布局，
     * 保证开发者模式下依然可以滚动列表/页面。
     */
    private static class SpyView extends View {
        private final int slop;
        private float downX, downY;
        private boolean forwarding;
        private MotionEvent pendingDown;

        SpyView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setBackgroundColor(0x01000000);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    forwarding = false;
                    if (pendingDown != null) pendingDown.recycle();
                    pendingDown = MotionEvent.obtain(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!forwarding
                            && (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop)) {
                        forwarding = true;
                        if (pendingDown != null) {
                            forward(pendingDown);
                            pendingDown.recycle();
                            pendingDown = null;
                        }
                    }
                    if (forwarding) forward(e);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (forwarding) forward(e);
                    else selectAt(e.getX(), e.getY());
                    forwarding = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (forwarding) forward(e);
                    forwarding = false;
                    if (pendingDown != null) {
                        pendingDown.recycle();
                        pendingDown = null;
                    }
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }

        private void forward(MotionEvent e) {
            if (contentRoot == null) return;
            int[] a = new int[2];
            int[] b = new int[2];
            contentRoot.getLocationOnScreen(a);
            getLocationOnScreen(b);
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            if (dx != 0 || dy != 0) e.offsetLocation(-dx, -dy);
            contentRoot.dispatchTouchEvent(e);
            if (dx != 0 || dy != 0) e.offsetLocation(dx, dy);
        }
    }

    // ===================== 红色阴影 =====================

    private static class HighlightView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF last = new RectF();
        private final int[] targetLoc = new int[2];
        private final int[] myLoc = new int[2];
        private View target;

        HighlightView(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x3DFF3B30);
            fill.setShadowLayer(dp(c, 6), 0, 0, 0xB3FF3B30);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(c, 2));
            stroke.setColor(0xFFFF3B30);
            setClickable(false);
            setFocusable(false);
        }

        void setTarget(View v) {
            target = v;
            invalidate();
        }

        /** 目标滚动/移动后，矩形变化才重绘。 */
        void refresh() {
            if (target == null) return;
            last.set(rect);
            if (!compute()) return;
            if (!rect.equals(last)) invalidate();
        }

        private boolean compute() {
            if (target == null || !target.isAttachedToWindow()) return false;
            target.getLocationOnScreen(targetLoc);
            getLocationOnScreen(myLoc);
            float l = targetLoc[0] - myLoc[0];
            float t = targetLoc[1] - myLoc[1];
            rect.set(l, t, l + target.getWidth(), t + target.getHeight());
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (target == null || !compute()) return;
            float rad = dp(getContext(), 3);
            canvas.drawRoundRect(rect, rad, rad, fill);
            canvas.drawRoundRect(rect, rad, rad, stroke);
        }
    }
}
