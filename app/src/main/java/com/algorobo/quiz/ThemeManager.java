package com.algorobo.quiz;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Window;

/**
 * 全局主题色管理器。
 * 统一预置色板、自定义色值持久化，以及各 Activity 顶栏/状态栏的全局染色。
 */
public class ThemeManager {

    /** 预置色板（索引 0 为默认蓝紫）。 */
    public static final String[] PRESET_COLORS = {
            "#5B5FEF", "#22C55E", "#F59E0B", "#EF4444", "#0EA5E9", "#EC4899", "#8B5CF6"
    };

    private static final String KEY = "setting_theme_color";
    private static final String KEY_CUSTOM = "setting_theme_color_custom";

    public static int getThemeColorIndex(Context c) {
        return c.getSharedPreferences(DataStore.PREFS, Context.MODE_PRIVATE).getInt(KEY, 0);
    }

    public static void setThemeColorIndex(Context c, int index) {
        c.getSharedPreferences(DataStore.PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, index).apply();
    }

    public static String getCustomColor(Context c) {
        return c.getSharedPreferences(DataStore.PREFS, Context.MODE_PRIVATE).getString(KEY_CUSTOM, "");
    }

    public static void setCustomColor(Context c, String hex) {
        c.getSharedPreferences(DataStore.PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CUSTOM, hex).apply();
    }

    public static boolean isCustom(Context c) {
        return getThemeColorIndex(c) < 0;
    }

    public static int getThemeColor(Context c) {
        String hex = getThemeColorValue(c);
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.parseColor(PRESET_COLORS[0]);
        }
    }

    public static String getThemeColorValue(Context c) {
        int idx = getThemeColorIndex(c);
        if (idx < 0) {
            String custom = getCustomColor(c);
            if (custom != null && !custom.isEmpty()) return custom;
        }
        if (idx >= 0 && idx < PRESET_COLORS.length) return PRESET_COLORS[idx];
        return PRESET_COLORS[0];
    }

    public static void applyPreset(Context c, int index) {
        setThemeColorIndex(c, index);
        setCustomColor(c, "");
    }

    public static void applyCustom(Context c, String hex) {
        setThemeColorIndex(c, -1);
        setCustomColor(c, hex);
    }

    public static String normalizeHex(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (!s.startsWith("#")) s = "#" + s;
        if (s.length() != 7 || !s.matches("#[0-9a-fA-F]{6}")) return null;
        return s.toUpperCase();
    }

    /** 当前是否处于深色主题（结合设置与系统）。 */
    public static boolean isDarkTheme(Context c) {
        int mode = DataStore.getDarkMode(c);
        if (mode == 2) return true;
        if (mode == 1) return false;
        int ui = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * 全局应用 edge-to-edge（M3 推荐）：
     * 系统栏透明 + 图标明暗跟随主题 + 内容根视图预留系统栏 Insets（避免被状态栏/导航栏遮挡）。
     */
    public static void applyStatusBar(Activity activity) {
        android.view.Window win = activity.getWindow();
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);
        androidx.core.view.WindowInsetsControllerCompat ctrl =
                androidx.core.view.WindowCompat.getInsetsController(win, win.getDecorView());
        boolean dark = isDarkTheme(activity);
        // 默认：状态栏/导航栏图标明暗跟随主题（首页顶部为页面底色）
        ctrl.setAppearanceLightStatusBars(!dark);
        ctrl.setAppearanceLightNavigationBars(!dark);
        // 刘海屏适配：内容延伸至凹口（shortEdges），API 28+
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = win.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            win.setAttributes(lp);
        }
        // 内容根视图：左右/底部 Insets 转为 padding，保证底部控件不被导航栏遮挡
        android.view.View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                androidx.core.graphics.Insets bars =
                        insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, v.getPaddingTop(), bars.right, bars.bottom);
                return insets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(content);
        }
    }

    /** 给指定的顶栏 View 动态染色为主题色，并让顶栏延伸到状态栏（edge-to-edge 顶部 Insets）。 */
    public static void applyTopBarColor(Activity activity, int... viewIds) {
        int color = getThemeColor(activity);
        for (int id : viewIds) {
            android.view.View v = activity.findViewById(id);
            if (v == null) continue;
            v.setBackgroundColor(color);
            applyTopInsetToView(v);
        }
        // 顶栏背景延伸至状态栏下方，状态栏图标固定为白色以保证对比度
        android.view.Window win = activity.getWindow();
        androidx.core.view.WindowInsetsControllerCompat ctrl =
                androidx.core.view.WindowCompat.getInsetsController(win, win.getDecorView());
        ctrl.setAppearanceLightStatusBars(false);
    }

    /** 顶栏适配状态栏 Insets：固定高度则增高，wrap_content 则加顶部 padding。 */
    private static void applyTopInsetToView(final android.view.View v) {
        final int baseTopPadding = v.getPaddingTop();
        final android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        final int baseHeight = lp == null ? 0 : lp.height;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top;
            if (baseHeight > 0) {
                android.view.ViewGroup.LayoutParams p = view.getLayoutParams();
                if (p != null && p.height != baseHeight + top) {
                    p.height = baseHeight + top;
                    view.setLayoutParams(p);
                }
            } else {
                view.setPadding(view.getPaddingLeft(), baseTopPadding + top,
                        view.getPaddingRight(), view.getPaddingBottom());
            }
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(v);
    }

    /** 获取状态栏高度（px），保留兼容。 */
    public static int getStatusBarHeight(Activity activity) {
        int result = 0;
        int resourceId = activity.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = activity.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /** 将顶栏内首个子 View 的顶部间距设为状态栏高度（edge-to-edge 下用 Insets 动态计算）。 */
    public static void applyTopInset(Activity activity, int viewId) {
        final android.view.View v = activity.findViewById(viewId);
        if (v == null) return;
        final android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (lp == null) return;
        final int baseTopMargin = lp.topMargin;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top;
            if (lp.topMargin != baseTopMargin + top) {
                lp.topMargin = baseTopMargin + top;
                view.setLayoutParams(lp);
            }
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(v);
    }
}
