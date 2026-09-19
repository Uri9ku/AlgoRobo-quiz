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

    /** 全局应用主题色到状态栏，并适配刘海屏（延伸内容至凹口区域）。 */
    public static void applyStatusBar(Activity activity) {
        int color = getThemeColor(activity);
        activity.getWindow().setStatusBarColor(color);
        // 刘海屏适配：内容延伸至凹口（shortEdges），API 28+
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(lp);
        }
    }

    /** 给指定的顶栏 View 动态染色为主题色。 */
    public static void applyTopBarColor(Activity activity, int... viewIds) {
        int color = getThemeColor(activity);
        for (int id : viewIds) {
            android.view.View v = activity.findViewById(id);
            if (v != null) v.setBackgroundColor(color);
        }
    }

    /** 获取状态栏高度（px），用于刘海屏下动态顶部间距。 */
    public static int getStatusBarHeight(Activity activity) {
        int result = 0;
        int resourceId = activity.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = activity.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /** 将顶栏内首个子 View 的顶部 margin 动态设为状态栏高度（刘海屏适配）。 */
    public static void applyTopInset(Activity activity, int viewId) {
        android.view.View v = activity.findViewById(viewId);
        if (v == null) return;
        int top = getStatusBarHeight(activity);
        android.view.ViewGroup.MarginLayoutParams lp =
                (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (lp != null && top > 0) {
            lp.topMargin = top;
            v.setLayoutParams(lp);
        }
    }
}
