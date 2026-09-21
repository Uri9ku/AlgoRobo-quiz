package com.algorobo.quiz;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

/**
 * 页面标题统一：子页面顶栏标题与来源控件（入口卡片/按钮）的名称保持一致。
 * 入口处用 {@link #put(Intent, TextView)} 带上控件文字，子页面用 {@link #apply(Activity, int)} 应用。
 */
public final class PageTitle {

    /** Intent 中承载页面标题的 key。 */
    public static final String EXTRA = "pageTitle";

    private PageTitle() {
    }

    /** 用控件（TextView）当前文字作为页面标题写入 Intent。 */
    public static void put(Intent intent, TextView label) {
        if (label != null) {
            put(intent, label.getText());
        }
    }

    /** 用控件文字/描述作为页面标题写入 Intent。 */
    public static void put(Intent intent, CharSequence label) {
        if (intent != null && label != null && label.length() > 0) {
            intent.putExtra(EXTRA, label.toString());
        }
    }

    /** 构造一个已带上页面标题的 Intent。 */
    public static Intent intent(Context ctx, Class<?> target, TextView label) {
        Intent i = new Intent(ctx, target);
        put(i, label);
        return i;
    }

    /**
     * 在目标页面应用标题；未传入标题时保留布局中的默认文案。
     */
    public static void apply(Activity activity, int titleViewId) {
        if (activity == null) {
            return;
        }
        CharSequence title = activity.getIntent().getCharSequenceExtra(EXTRA);
        if (title == null || title.length() == 0) {
            return;
        }
        TextView tv = activity.findViewById(titleViewId);
        if (tv != null) {
            tv.setText(title);
        }
    }
}
