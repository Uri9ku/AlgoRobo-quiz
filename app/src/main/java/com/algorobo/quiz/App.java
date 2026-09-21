package com.algorobo.quiz;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyStoredDarkMode();
        // 一次性清理旧版 uid 缺陷残留（同卷共用一个 key）
        DataStore.purgeLegacyPaperKeys(this);
        // 全局「开发者模式」悬浮控件：跟随每个页面的生命周期挂载/卸载
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                DevModeOverlay.onActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                DevModeOverlay.onActivityPaused(activity);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    public void applyStoredDarkMode() {
        int mode = DataStore.getDarkMode(this);
        int nightMode;
        switch (mode) {
            case 1: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
            case 2: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
