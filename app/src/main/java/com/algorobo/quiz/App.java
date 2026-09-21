package com.algorobo.quiz;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyStoredDarkMode();
        // 一次性清理旧版 uid 缺陷残留（同卷共用一个 key）
        DataStore.purgeLegacyPaperKeys(this);
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
