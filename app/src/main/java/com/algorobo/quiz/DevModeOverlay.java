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
import android.graphics.Typeface;
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
import android.widget.ScrollView;
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
 * - 由设置页开关控制是否显示（关闭后不挂载浮层）；
 * - 点击悬浮球进入开发者模式：点页面任意控件即可选中，选中控件被红色阴影覆盖，
 *   弹出可拖动、可缩放高度的信息面板：左列=原始信息，右列=对应中文说明（过长可横向滑动），
 *   每行右侧有单独「复制」按钮；底部滑块可在父/子控件之间切换；再次点击悬浮球退出。
 */
public final class DevModeOverlay {

    private DevModeOverlay() {
    }

    private static final String PREFS = "dev_overlay";

    private static boolean active;
    private static Activity host;
    private static FrameLayout overlay;
    private static View contentRoot;

    private static SpyView spy;
    private static HighlightView highlight;
    private static LinearLayout panel;
    private static ScrollView panelScroll;
    private static LinearLayout rowsContainer;
    private static TextView bubble;
    private static TextView levelView;
    private static SeekBar levelBar;
    private static ViewTreeObserver.OnPreDrawListener preDraw;

    private static View selected;
    private static final List<View> chain = new ArrayList<>();
    private static boolean updatingBar;
    private static String allRowsText = "";

    private static int bubbleX = Integer.MIN_VALUE, bubbleY = Integer.MIN_VALUE;
    private static int panelX = Integer.MIN_VALUE, panelY = Integer.MIN_VALUE;
    /** 信息区高度（px），跨页面保留。 */
    private static int listHeight = -1;
    /** 父子滑动条量程（会话内见过的最大层级深度）。 */
    private static int sliderMaxDepth = 1;

    // ===================== 生命周期入口 =====================

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

    /** 设置页开关变化时调用。 */
    public static void onSettingChanged(Activity a) {
        host = a;
        if (!DataStore.isDevModeEnabled(a)) {
            if (active) setActive(false);
            detach();
        } else if (overlay == null) {
            attach(a);
        }
    }

    public static boolean isActive() {
        return active;
    }

    // ===================== 挂载 / 卸载 =====================

    private static void attach(Activity a) {
        if (overlay != null) return;
        if (!DataStore.isDevModeEnabled(a)) return;
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        contentRoot = content.getChildAt(0);

        overlay = new FrameLayout(a);
        overlay.setClipChildren(false);
        content.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bubble = new TextView(a);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        bubble.setTextColor(a.getColor(R.color.dev_bubble_text));
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(a, 10), dp(a, 6), dp(a, 10), dp(a, 6));
        bubble.setAlpha(0.88f);
        bubble.setElevation(dp(a, 24));
        overlay.addView(bubble, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setupBubbleTouch(a);
        updateBubbleStyle();

        if (active) enableDevViews(a);
        overlay.post(() -> placeBubble(a, false));
        // 首次布局完成后再校正一次并写回，避免异常位置被反复读取
        overlay.postDelayed(() -> placeBubble(a, true), 400);
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
        panelScroll = null;
        rowsContainer = null;
        bubble = null;
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
                            clampBubble(a, overlay.getWidth(), overlay.getHeight());
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
            bubbleX = w / 2 - dp(c, 45);
            bubbleY = h / 2 - dp(c, 30);
        }
        // 历史保存的位置可能落在窗口外（那样点击会被系统手势区吃掉），这里做合法性校正
        int maxY = Math.max(0, h - dp(c, 60));
        if (bubbleY < 0 || bubbleY > maxY) bubbleY = h / 2 - dp(c, 30);
        if (bubbleX < 0 || bubbleX > Math.max(0, w - dp(c, 40))) bubbleX = w / 2 - dp(c, 45);
        clampBubble(c, w, h);
        bubble.setX(bubbleX);
        bubble.setY(bubbleY);
        if (save) prefs(c).edit().putInt("bx", bubbleX).putInt("by", bubbleY).apply();
        // 布局完成后再夹一次：避免测量前 getWidth()=0 导致位置落到窗口外（那样点击会被系统吃掉）
        overlay.post(() -> {
            if (bubble == null || overlay == null) return;
            clampBubble(c, overlay.getWidth(), overlay.getHeight());
            bubble.setX(bubbleX);
            bubble.setY(bubbleY);
        });
    }

    /** 把悬浮球限制在窗口内（底部留出系统手势区），保证它一定能被点到。 */
    private static void clampBubble(Context c, int w, int h) {
        if (bubble == null || w <= 0 || h <= 0) return;
        int bw = bubble.getWidth() > 0 ? bubble.getWidth() : dp(c, 90);
        int bh = bubble.getHeight() > 0 ? bubble.getHeight() : dp(c, 26);
        int bottomInset = dp(c, 8);
        bubbleX = Math.max(0, Math.min(bubbleX, Math.max(0, w - bw)));
        bubbleY = Math.max(0, Math.min(bubbleY, Math.max(0, h - bh - bottomInset)));
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

        panel = (LinearLayout) LayoutInflater.from(a).inflate(R.layout.dev_panel, overlay, false);
        panel.setVisibility(View.GONE);
        panel.setElevation(dp(a, 8));
        overlay.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (bubble != null) bubble.bringToFront();
        bindPanel(a);
        overlay.post(() -> {
            if (panel == null) return;
            ViewGroup.LayoutParams lp = panel.getLayoutParams();
            lp.width = Math.min(Math.max(0, overlay.getWidth() - dp(a, 16)), dp(a, 460));
            panel.setLayoutParams(lp);
            applyPanelHeight();
            placePanel();
        });

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
        panelScroll = null;
        rowsContainer = null;
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
        panelScroll = panel.findViewById(R.id.devInfoScroll);
        rowsContainer = panel.findViewById(R.id.devInfoRows);
        levelView = panel.findViewById(R.id.devLevel);
        levelBar = panel.findViewById(R.id.devLevelBar);

        levelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || updatingBar) return;
                if (chain.isEmpty()) return;
                int p = progress;
                if (p >= chain.size()) p = chain.size() - 1;
                if (p < 0) p = 0;
                View v = chain.get(p);
                selected = v;
                if (highlight != null) highlight.setTarget(v);
                showInfo(v, p);
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
        panel.findViewById(R.id.devCopy).setOnClickListener(v ->
                copyToClipboard(a, "已复制全部信息", allRowsText));
        panel.findViewById(R.id.devCopyLocator).setOnClickListener(v -> {
            if (selected == null) return;
            StringBuilder sb = new StringBuilder();
            for (String[] r : locatorRows(selected)) {
                sb.append(r[0]).append("：").append(r[1]).append('\n');
            }
            copyToClipboard(a, "已复制定位符", sb.toString());
        });

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

        setupResize(a);
    }

    /** 拉动面板最上/最下边调整信息区高度。 */
    private static void setupResize(final Activity a) {
        final int minH = dp(a, 56);
        View top = panel.findViewById(R.id.devResizeTop);
        View bottom = panel.findViewById(R.id.devResizeBottom);

        top.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            int startH;
            float startPanelY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = e.getRawY();
                        startH = Math.max(minH, listHeight);
                        startPanelY = panel.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dy = (int) (e.getRawY() - startY);
                        int newH = clampListHeight(startH - dy, minH);
                        int realDy = startH - newH;
                        listHeight = newH;
                        applyPanelHeight();
                        float y = Math.max(0, Math.min(startPanelY + realDy,
                                Math.max(0, overlay.getHeight() - panel.getHeight())));
                        panel.setY(y);
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

        bottom.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            int startH;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = e.getRawY();
                        startH = Math.max(minH, listHeight);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dy = (int) (e.getRawY() - startY);
                        listHeight = clampListHeight(startH + dy, minH);
                        applyPanelHeight();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        panelY = (int) panel.getY();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private static int clampListHeight(int h, int minH) {
        int ctxPad = overlay == null ? 0 : dp(overlay.getContext(), 260);
        int maxH = Math.max(minH, (overlay == null ? minH : overlay.getHeight()) - ctxPad);
        return Math.max(minH, Math.min(h, maxH));
    }

    private static void applyPanelHeight() {
        if (panelScroll == null) return;
        if (listHeight <= 0) listHeight = dp(panelScroll.getContext(), 200);
        ViewGroup.LayoutParams lp = panelScroll.getLayoutParams();
        lp.height = listHeight;
        panelScroll.setLayoutParams(lp);
        clampPanelPos();
    }

    /** 面板高度变化后重新贴边，保证最下面的拖动条不会被挤出屏幕。 */
    private static void clampPanelPos() {
        if (panel == null || overlay == null) return;
        panel.post(() -> {
            if (panel == null || overlay == null) return;
            float y = Math.max(0, Math.min(panel.getY(),
                    Math.max(0, overlay.getHeight() - panel.getHeight())));
            float x = Math.max(0, Math.min(panel.getX(),
                    Math.max(0, overlay.getWidth() - panel.getWidth())));
            panel.setY(y);
            panel.setX(x);
            panelY = (int) y;
            panelX = (int) x;
        });
    }

    private static void placePanel() {
        if (panel == null || overlay == null) return;
        int w = overlay.getWidth(), h = overlay.getHeight();
        if (w <= 0 || h <= 0) return;
        if (panelX == Integer.MIN_VALUE) {
            panelX = w / 2 - dp(panel.getContext(), 200);
            panelY = dp(panel.getContext(), 16);
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

    private static void copyToClipboard(Context c, String toast, CharSequence txt) {
        if (c == null) return;
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("控件信息", txt == null ? "" : txt));
        Toast.makeText(c, toast, Toast.LENGTH_SHORT).show();
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
        int depth = chain.size() - 1;
        if (depth > sliderMaxDepth) sliderMaxDepth = depth;
        updatingBar = true;
        if (levelBar != null) {
            // 滑块量程取会话内见过的最大层级，位置=当前控件的层级，切换控件时位置会实时变化
            levelBar.setMax(Math.max(1, sliderMaxDepth));
            levelBar.setProgress(depth);
        }
        updatingBar = false;
        showInfo(v, depth);
        if (panel != null && panel.getVisibility() != View.VISIBLE) {
            panel.setVisibility(View.VISIBLE);
            applyPanelHeight();
            placePanel();
        }
    }

    private static void showInfo(View v, int index) {
        if (levelView != null) {
            String tip = chain.size() > 1 ? "（拖动滑块切换父/子控件）" : "（无父级可切换）";
            levelView.setText("层级 " + (index + 1) + "/" + chain.size() + " · "
                    + v.getClass().getSimpleName() + " " + tip);
        }
        renderRows(v);
    }

    /** 渲染两列信息：左列原信息、右列中文说明，每行带单独复制按钮。 */
    private static void renderRows(View v) {
        if (rowsContainer == null) return;
        Context c = host != null ? host : v.getContext();
        List<String[]> rows = new ArrayList<>();
        rows.addAll(locatorRows(v));
        rows.addAll(infoRows(v));

        rowsContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(c);
        StringBuilder all = new StringBuilder();
        for (String[] r : rows) {
            View rowView = inf.inflate(R.layout.dev_row, rowsContainer, false);
            TextView left = rowView.findViewById(R.id.devRowLeft);
            TextView right = rowView.findViewById(R.id.devRowRight);
            // 两列互换：左列=中文说明，右列=原始数值/表达式；复制按钮固定在右侧列之后，复制原始数值
            left.setText(r[0] + "：" + r[2]);
            right.setText(r[1]);
            final String copyText = r[1];
            rowView.findViewById(R.id.devRowCopy).setOnClickListener(x ->
                    copyToClipboard(c, "已复制：" + r[0], copyText));
            rowsContainer.addView(rowView);
            all.append(r[0]).append("：").append(r[1]).append('\n')
                    .append("    说明：").append(r[2]).append('\n');
        }
        allRowsText = all.toString();
    }

    private static String[] row(String label, String raw, String cn) {
        return new String[]{label, raw, cn};
    }

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

    // ===================== 定位符（左列=表达式，右列=中文说明） =====================

    private static List<String[]> locatorRows(View v) {
        List<String[]> rows = new ArrayList<>();
        String text = textOf(v);
        String desc = descOf(v);
        String id = resourceId(v);
        String xpath = xpathOf(v);
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);

        if (id != null) {
            rows.add(row("推荐定位", "id=" + id, "首选：按资源ID定位，最稳定（自动化/埋点/回捞都用它）"));
        } else if (text != null) {
            rows.add(row("推荐定位", "text=\"" + oneLine(text) + "\"", "首选：按控件文本定位（同页文本通常唯一）"));
        } else if (desc != null) {
            rows.add(row("推荐定位", "content-desc=\"" + desc + "\"", "首选：按无障碍描述定位"));
        } else {
            rows.add(row("推荐定位", "xpath=" + xpath, "既无 ID 也无文本，只能用结构路径定位"));
        }
        if (text != null) {
            rows.add(row("定位-text", "text=\"" + oneLine(text) + "\"", "按文本精确匹配（换行已转义为 \\n）"));
            if (text.length() > 16) {
                rows.add(row("定位-textContains", "textContains=\"" + oneLine(text.substring(0, 16)) + "\"",
                        "文本过长时只匹配前 16 个字（Automator textContains）"));
            }
        }
        if (desc != null) {
            rows.add(row("定位-description", "content-desc=\"" + desc + "\"", "按 contentDescription 精确匹配"));
        }
        if (id != null) {
            rows.add(row("定位-id", "id=" + id, "按资源ID匹配"));
            rows.add(row("定位-xpath(id)", "//" + v.getClass().getName()
                    + "[@resource-id='" + id + "']", "用 ID 写的 XPath，最短最稳"));
        }
        rows.add(row("定位-class", v.getClass().getName(), "控件类名（uiautomator 中的 android.widget.xxx 形式）"));
        rows.add(row("定位-xpath", xpath, "结构路径：从最近带 id 的祖先起算，同类兄弟按下标"));
        rows.add(row("定位-bounds", String.format(Locale.US, "[%d,%d][%d,%d]",
                        loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight()),
                "屏幕绝对像素范围，可配合坐标点击"));
        rows.add(row("复现点击", "adb shell input tap " + (loc[0] + v.getWidth() / 2)
                + " " + (loc[1] + v.getHeight() / 2), "用命令复现一次点击"));
        return rows;
    }

    // ===================== 控件信息（左列=原信息，右列=中文说明） =====================

    private static List<String[]> infoRows(View v) {
        Resources r = v.getResources();
        float density = r.getDisplayMetrics().density;
        List<String[]> rows = new ArrayList<>();

        rows.add(row("控件类", v.getClass().getName(), widgetCn(v.getClass().getName())));

        int id = v.getId();
        if (id == View.NO_ID) {
            rows.add(row("控件ID", "无（未设置 android:id）", "没写 android:id，无法用资源ID定位，可用文本或 XPath"));
        } else {
            String name = null;
            try {
                name = r.getResourceName(id);
            } catch (Exception ignore) {
            }
            rows.add(row("控件ID", name == null ? ("0x" + Integer.toHexString(id)) : name,
                    "资源ID，findViewById 与自动化定位都用它"));
        }

        rows.add(row("控件名字", nameRaw(v), nameCn(v)));
        rows.add(row("可见性", visibilityRaw(v.getVisibility()), visibilityCn(v.getVisibility())));
        rows.add(row("透明度", fmt(v.getAlpha()),
                v.getAlpha() >= 1f ? "完全不透明" : (v.getAlpha() <= 0f ? "完全透明（看不见）" : "半透明")));

        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        rows.add(row("尺寸", v.getWidth() + " x " + v.getHeight() + " px",
                String.format(Locale.US, "等于 %.1f x %.1f dp（本机密度 %.1f）%s",
                        v.getWidth() / density, v.getHeight() / density, density, measureCn(v))));
        rows.add(row("屏幕位置", "x=" + loc[0] + " y=" + loc[1], "相对屏幕左上角的像素坐标（含状态栏）"));
        rows.add(row("内边距", String.format(Locale.US, "%d,%d,%d,%d",
                        v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom()),
                "内容与控件边界之间的距离（左,上,右,下）"));

        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            rows.add(row("外边距", String.format(Locale.US, "%s,%s,%s,%s",
                            marginText(m.leftMargin), marginText(m.topMargin),
                            marginText(m.rightMargin), marginText(m.bottomMargin)),
                    "与父容器或兄弟控件之间的距离（左,上,右,下）"));
        }
        if (lp != null) {
            rows.add(row("布局参数", lpRaw(lp), lpCn(lp)));
        }

        rows.add(row("背景", bgRaw(v), bgCn(v)));

        if (v instanceof TextView) {
            TextView t = (TextView) v;
            float sp = t.getTextSize() / r.getDisplayMetrics().scaledDensity;
            rows.add(row("字号", String.format(Locale.US, "%.1f sp（%.0f px）", sp, t.getTextSize()),
                    "sp 会跟随系统字体缩放，px 为实际渲染像素"));
            rows.add(row("文字颜色", colorText(t.getCurrentTextColor()), "ARGB 十六进制，前两位是透明度（FF=不透明）"));
            rows.add(row("字形", typefaceRaw(t.getTypeface()), "文字粗细与斜体样式"));
            rows.add(row("文本对齐", gravityRaw(t.getGravity()), "文本在控件内的对齐方式：" + gravityCn(t.getGravity())));
            rows.add(row("行间距", ((int) t.getLineSpacingExtra()) + " px", "行与行之间额外的像素间距"));
            if (t.getMaxLines() > 0) {
                rows.add(row("最大行数", String.valueOf(t.getMaxLines()), "超过后按省略号截断"));
            }
            if (t.getEllipsize() != null) {
                rows.add(row("省略方式", t.getEllipsize().name(), "文本超长时的截断方式"));
            }
            if (t.getHint() != null) {
                rows.add(row("提示 hint", String.valueOf(t.getHint()), "输入框为空时显示的灰色提示文字"));
            }
        }

        rows.add(row("属性", attrRaw(v), attrCn(v)));
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.length() > 0) {
            rows.add(row("无障碍描述", String.valueOf(cd), "contentDescription：读屏与自动化识别控件用"));
        }
        if (v.getTag() != null) {
            rows.add(row("标签 tag", String.valueOf(v.getTag()), "开发时挂在控件上的自定义数据"));
        }
        rows.add(row("阴影高度", ((int) v.getElevation()) + " px",
                "elevation：值越大越靠上绘制（同容器内比较）"));
        rows.add(row("层级路径", pathText(v), "从页面根布局到当前控件的层级链（父 › 子）"));
        return rows;
    }

    private static String nameRaw(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) return "\"" + oneLine(t.toString()) + "\"";
        }
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.length() > 0) return "\"" + cd + "\"";
        if (v instanceof ViewGroup) return "(容器，" + ((ViewGroup) v).getChildCount() + " 个子控件)";
        return "(无文本)";
    }

    private static String nameCn(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) return "控件显示的文本内容";
        }
        CharSequence cd = v.getContentDescription();
        if (cd != null && cd.length() > 0) return "取自无障碍描述（没有可见文本）";
        if (v instanceof ViewGroup) return "容器类控件，本身没有文本";
        return "该控件没有文本内容";
    }

    private static String visibilityRaw(int vis) {
        switch (vis) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return String.valueOf(vis);
        }
    }

    private static String visibilityCn(int vis) {
        switch (vis) {
            case View.VISIBLE:
                return "可见：正常显示且占位";
            case View.INVISIBLE:
                return "不可见：仍然占位（保留布局空间）";
            case View.GONE:
                return "已隐藏：不占位（布局中相当于不存在）";
            default:
                return "未知可见性";
        }
    }

    private static String measureCn(View v) {        try {
            int w = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            v.measure(w, h);
            int mw = v.getMeasuredWidth(), mh = v.getMeasuredHeight();
            if (mw != v.getWidth() || mh != v.getHeight()) {
                return "；内容实际需要 " + mw + " x " + mh + " px";
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    /** 未设置的外边距在某些布局里是 Integer.MIN_VALUE，这里按 0 展示。 */
    private static String marginText(int v) {
        if (v == Integer.MIN_VALUE || v < -100000) return "0";
        return String.valueOf(v);
    }

    private static String lpRaw(ViewGroup.LayoutParams lp) {
        StringBuilder sb = new StringBuilder(lp.getClass().getSimpleName());
        if (lp instanceof FrameLayout.LayoutParams) {
            sb.append(" gravity=").append(gravityRaw(((FrameLayout.LayoutParams) lp).gravity));
        } else if (lp instanceof LinearLayout.LayoutParams) {
            sb.append(" weight=").append(((LinearLayout.LayoutParams) lp).weight);
        }
        sb.append(sizeText(" width=", lp.width)).append(sizeText(" height=", lp.height));
        return sb.toString();
    }

    private static String sizeText(String prefix, int value) {
        if (value == ViewGroup.LayoutParams.MATCH_PARENT) return prefix + "match_parent";
        if (value == ViewGroup.LayoutParams.WRAP_CONTENT) return prefix + "wrap_content";
        return prefix + value + "px";
    }

    private static String lpCn(ViewGroup.LayoutParams lp) {
        StringBuilder sb = new StringBuilder();
        if (lp instanceof FrameLayout.LayoutParams) {
            int g = ((FrameLayout.LayoutParams) lp).gravity;
            if (g != 0) sb.append("在父容器中的对齐：").append(gravityCn(g)).append("；");
        } else if (lp instanceof LinearLayout.LayoutParams) {
            float w = ((LinearLayout.LayoutParams) lp).weight;
            if (w > 0) sb.append("按权重 ").append(w).append(" 分配剩余空间；");
        }
        if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) sb.append("宽度撑满父容器；");
        else if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) sb.append("宽度由内容决定；");
        else sb.append("宽度固定 ").append(lp.width).append(" px；");
        if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) sb.append("高度撑满父容器");
        else if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) sb.append("高度由内容决定");
        else sb.append("高度固定 ").append(lp.height).append(" px");
        return sb.toString();
    }

    private static String bgRaw(View v) {
        Drawable bg = v.getBackground();
        if (bg == null) return "无";
        String res = backgroundRes(v);
        if (bg instanceof ColorDrawable) {
            String color = colorText(((ColorDrawable) bg).getColor());
            return res == null ? color : color + "  （" + res + "）";
        }
        return (res == null ? bg.getClass().getName() : res);
    }

    private static String bgCn(View v) {
        Drawable bg = v.getBackground();
        if (bg == null) return "没有设置背景";
        if (bg instanceof ColorDrawable) return "纯色背景（背景色）";
        return "用图形/形状资源绘制背景（圆角、描边等多来自这里）";
    }

    private static String backgroundRes(View v) {
        try {
            Field f = View.class.getDeclaredField("mBackgroundResource");
            f.setAccessible(true);
            int rid = (int) f.get(v);
            if (rid != 0) return shortRes(v.getResources().getResourceName(rid));
        } catch (Exception ignore) {
        }
        return null;
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

    private static String attrRaw(View v) {
        return "clickable=" + v.isClickable() + " enabled=" + v.isEnabled()
                + " focusable=" + v.isFocusable() + " selected=" + v.isSelected();
    }

    private static String attrCn(View v) {
        return (v.isClickable() ? "可点击" : "不可点击") + "、"
                + (v.isEnabled() ? "已启用" : "已禁用") + "、"
                + (v.isFocusable() ? "可获得焦点" : "不可获焦") + "、"
                + (v.isSelected() ? "处于选中态" : "未选中");
    }

    private static String typefaceRaw(Typeface tf) {
        if (tf == null) return "默认";
        if (tf.isBold() && tf.isItalic()) return "粗体 + 斜体";
        if (tf.isBold()) return "粗体";
        if (tf.isItalic()) return "斜体";
        return "常规";
    }

    private static String gravityRaw(int g) {
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
        return sb.length() == 0 ? ("0x" + Integer.toHexString(g)) : sb.toString();
    }

    private static String gravityCn(int g) {
        if (g == 0) return "未设置（默认）";
        if ((g & Gravity.CENTER) == Gravity.CENTER) return "水平垂直都居中";
        StringBuilder sb = new StringBuilder();
        if ((g & Gravity.LEFT) != 0) sb.append("靠左");
        else if ((g & Gravity.RIGHT) != 0) sb.append("靠右");
        else if ((g & Gravity.CENTER_HORIZONTAL) != 0) sb.append("水平居中");
        if ((g & Gravity.TOP) != 0) sb.append(sb.length() > 0 ? "、" : "").append("靠上");
        else if ((g & Gravity.BOTTOM) != 0) sb.append(sb.length() > 0 ? "、" : "").append("靠下");
        else if ((g & Gravity.CENTER_VERTICAL) != 0) sb.append(sb.length() > 0 ? "、" : "").append("垂直居中");
        return sb.length() == 0 ? ("0x" + Integer.toHexString(g)) : sb.toString();
    }

    private static String colorText(int color) {
        return String.format(Locale.US, "#%08X", color);
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

    private static String textOf(View v) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) return t.toString();
        }
        return null;
    }

    private static String descOf(View v) {
        CharSequence cd = v.getContentDescription();
        return (cd != null && cd.length() > 0) ? cd.toString() : null;
    }

    /** 有 android:id 时返回完整资源名，否则 null。 */
    private static String resourceId(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return null;
        try {
            return v.getResources().getResourceName(id);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从「最近的带 id 祖先」开始的 uiautomator XPath。 */
    private static String xpathOf(View v) {
        List<View> c = buildChain(v);
        int start = 0;
        for (int i = c.size() - 1; i >= 0; i--) {
            if (resourceId(c.get(i)) != null) {
                start = i;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < c.size(); i++) {
            View it = c.get(i);
            sb.append("//").append(it.getClass().getName());
            String rid = resourceId(it);
            if (rid != null) {
                sb.append("[@resource-id='").append(rid).append("']");
            } else {
                ViewParent p = it.getParent();
                if (p instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) p;
                    int idx = 1;
                    for (int j = 0; j < g.getChildCount(); j++) {
                        View sib = g.getChildAt(j);
                        if (sib == it) break;
                        if (sib.getClass() == it.getClass()) idx++;
                    }
                    if (idx > 1) sb.append('[').append(idx).append(']');
                }
            }
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        return s.replace("\n", "\\n").replace("\"", "'");
    }

    private static String fmt(float f) {
        return String.format(Locale.US, "%.2f", f);
    }

    /** 控件类名 → 中文含义。 */
    private static String widgetCn(String fullClass) {
        String n = fullClass;
        int dot = n.lastIndexOf('.');
        if (dot >= 0) n = n.substring(dot + 1);
        if (n.startsWith("AppCompat")) n = n.substring("AppCompat".length());
        if (n.startsWith("Material")) n = n.substring("Material".length());
        switch (n) {
            case "LinearLayout":
                return "线性布局容器（子控件按横向或纵向依次排列）";
            case "FrameLayout":
                return "帧布局容器（子控件层叠，默认左上对齐）";
            case "RelativeLayout":
                return "相对布局容器（子控件按相互位置关系排列）";
            case "GridLayout":
                return "网格布局容器（按行列网格排列）";
            case "TableLayout":
                return "表格布局容器";
            case "ConstraintLayout":
                return "约束布局容器（按约束关系定位子控件）";
            case "RecyclerView":
                return "可复用列表控件（滚动显示大量条目）";
            case "ListView":
                return "列表控件（纵向滚动显示条目）";
            case "GridView":
                return "网格列表控件（按网格排列条目）";
            case "ScrollView":
                return "竖向滚动容器";
            case "HorizontalScrollView":
                return "横向滚动容器";
            case "NestedScrollView":
                return "可嵌套的竖向滚动容器";
            case "ViewPager":
            case "ViewPager2":
                return "左右翻页容器";
            case "DrawerLayout":
                return "侧滑菜单容器";
            case "CardView":
                return "卡片容器（圆角 + 阴影）";
            case "TextView":
                return "文本显示控件";
            case "Button":
                return "按钮控件";
            case "ImageButton":
                return "图片按钮";
            case "ImageView":
                return "图片显示控件";
            case "EditText":
                return "文本输入框";
            case "CheckBox":
                return "复选框";
            case "RadioButton":
                return "单选框";
            case "RadioGroup":
                return "单选组容器";
            case "Switch":
                return "开关控件";
            case "SwitchCompat":
                return "开关控件";
            case "ToggleButton":
                return "开关按钮";
            case "SeekBar":
                return "拖动条（滑块）";
            case "ProgressBar":
                return "进度条";
            case "RatingBar":
                return "评分条";
            case "Spinner":
                return "下拉选择框";
            case "Toolbar":
                return "顶部工具栏";
            case "WebView":
                return "网页控件";
            case "Space":
                return "占位空白控件";
            case "View":
                return "基础视图（常用于分隔线/色块）";
            case "ViewGroup":
                return "容器基类";
            case "ViewStub":
                return "懒加载占位控件";
            case "SurfaceView":
                return "独立绘制表面控件";
            case "TextureView":
                return "纹理视图（可参与动画变换）";
            case "VideoView":
                return "视频播放控件";
            case "TabLayout":
                return "标签栏";
            case "Chip":
                return "标签按钮";
            case "FloatingActionButton":
                return "悬浮操作按钮（右下角圆形按钮）";
            default:
                return "（未收录的控件类型：" + n + "）";
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ===================== 触点拦截层 =====================

    private static class SpyView extends View {
        private final int slop;
        private float downX, downY;
        private boolean forwarding;
        private MotionEvent pendingDown;

        SpyView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setBackgroundColor(c.getColor(R.color.dev_bubble_bg));
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
            fill.setColor(c.getColor(R.color.dev_highlight_fill));
            fill.setShadowLayer(dp(c, 6), 0, 0, c.getColor(R.color.dev_highlight_shadow));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(c, 2));
            stroke.setColor(c.getColor(R.color.dev_highlight_stroke));
            setClickable(false);
            setFocusable(false);
        }

        void setTarget(View v) {
            target = v;
            invalidate();
        }

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
