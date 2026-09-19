package com.algorobo.quiz;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RobotExamBank {
    private static final String TAG = "RobotExamBank";

    public static class Paper {
        public String key;
        public String title;
        public String period;
        public int level;
        public String subject;
        public List<Question> questions = new ArrayList<>();
        public String sourceUrl;
        public String sourceName;
    }

    private static List<Paper> cachedPapers;

    private static final String PREFS_CUSTOM_TITLE = "exam_custom_titles";

    public static String getCustomTitle(Context ctx, String key) {
        return ctx.getSharedPreferences(PREFS_CUSTOM_TITLE, Context.MODE_PRIVATE)
                .getString(key, null);
    }

    public static void setCustomTitle(Context ctx, String key, String customTitle) {
        ctx.getSharedPreferences(PREFS_CUSTOM_TITLE, Context.MODE_PRIVATE)
                .edit().putString(key, customTitle).apply();
    }

    public static void resetCustomTitle(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS_CUSTOM_TITLE, Context.MODE_PRIVATE)
                .edit().remove(key).apply();
    }

    /**
     * 优先读取本地缓存（filesDir/robot_questions_cached.json）；
     * 缓存缺失时返回空列表。
     */
    public static synchronized List<Paper> getPapers(Context ctx) {
        if (cachedPapers != null) return cachedPapers;
        List<Paper> papers = new ArrayList<>();
        Map<String, Paper> map = RobotExamUpdater.loadCache(ctx);
        if (map == null) return papers;
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            Paper p = map.get(k);
            if (p == null) continue;
            p.key = k;
            papers.add(p);
        }
        cachedPapers = papers;
        return cachedPapers;
    }

    /**
     * 下载某套真题对应的原始 docx 文件，返回本地保存路径（失败返回 null）。
     * 在后台线程调用。使用 DownloadManager 写入公共 Download 目录。
     */
    public static String downloadSourceDocx(Context ctx, Paper paper) {
        if (paper == null || paper.sourceUrl == null || paper.sourceUrl.isEmpty()) {
            return null;
        }
        try {
            return RobotExamUpdater.downloadDocxToPublic(ctx, paper.sourceUrl, paper.sourceName);
        } catch (Exception e) {
            Log.w(TAG, "下载原始 docx 失败", e);
            return null;
        }
    }

    public static List<Question> getQuestions(Context ctx, String paperKey) {
        for (Paper p : getPapers(ctx)) {
            if (p.key.equals(paperKey)) return new ArrayList<>(p.questions);
        }
        return new ArrayList<>();
    }

    /** 从缓存读取指定 key 的题目列表（不触发联网）。缓存缺失返回 null。 */
    public static List<Question> getQuestionsFromCache(Context ctx, String paperKey) {
        Map<String, Paper> map = RobotExamUpdater.loadCache(ctx);
        if (map == null) return null;
        Paper p = map.get(paperKey);
        if (p == null) return null;
        return new ArrayList<>(p.questions);
    }

    /** 从缓存读取指定 key 的 Paper（不触发联网）。缓存缺失返回 null。 */
    public static Paper getPaper(Context ctx, String paperKey) {
        Map<String, Paper> map = RobotExamUpdater.loadCache(ctx);
        if (map == null) return null;
        Paper p = map.get(paperKey);
        if (p != null) p.key = paperKey;
        return p;
    }

    /** 读取缓存中所有 Paper（按键排序，不触发联网）。缓存缺失返回空列表。 */
    public static List<Paper> getCachedPapersList(Context ctx) {
        List<Paper> papers = new ArrayList<>();
        Map<String, Paper> map = RobotExamUpdater.loadCache(ctx);
        if (map == null) return papers;
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            Paper p = map.get(k);
            if (p == null) continue;
            p.key = k;
            papers.add(p);
        }
        return papers;
    }

    /**
     * 清除所有真题缓存：删除缓存 JSON、图片目录，并清空内存缓存。
     * 用于「历年真题页」的「清除真题」功能。
     */
    public static synchronized void clearCache(Context ctx) {
        // 先收集缓存 JSON 中引用的图片文件名，用于连同散图一起清理。
        java.util.Set<String> mediaNames = new java.util.HashSet<>();
        Map<String, Paper> before = RobotExamUpdater.loadCache(ctx);
        if (before != null) {
            for (Paper p : before.values()) {
                if (p == null || p.questions == null) continue;
                for (Question q : p.questions) {
                    if (q == null) continue;
                    if (q.stemImg != null && !q.stemImg.isEmpty()) mediaNames.add(q.stemImg);
                    if (q.optionImgs != null) {
                        for (String s : q.optionImgs) {
                            if (s != null && !s.isEmpty()) mediaNames.add(s);
                        }
                    }
                }
            }
        }
        cachedPapers = null;
        File dir = ctx.getFilesDir();
        File cacheJson = new File(dir, "robot_questions_cached.json");
        if (cacheJson.exists()) cacheJson.delete();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && f.getName().startsWith("robot_media")) {
                    deleteRecursive(f);
                } else if (f.isFile() && mediaNames.contains(f.getName())) {
                    f.delete();
                }
            }
        }
        Log.i(TAG, "已清除所有真题缓存");
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    /**
     * 按 key 批量移除指定真题的缓存（从缓存 JSON 中删除，并清理对应自定义标题）。
     * 用于「历年真题页」的「移除真题库」功能。
     */
    public static synchronized void removePapersByKeys(Context ctx, java.util.Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        Map<String, Paper> map = RobotExamUpdater.loadCache(ctx);
        if (map == null) return;
        boolean changed = false;
        for (String k : keys) {
            if (k == null) continue;
            if (map.remove(k) != null) {
                changed = true;
                resetCustomTitle(ctx, k);
            }
        }
        if (changed) {
            try {
                RobotExamUpdater.saveCachePublic(ctx, map);
                cachedPapers = null;
                Log.i(TAG, "已移除 " + keys.size() + " 条真题缓存");
            } catch (Exception e) {
                Log.w(TAG, "移除真题缓存失败", e);
            }
        }
    }

    /**
     * 返回媒体文件的绝对路径（图片已由 RobotExamUpdater 提取到 filesDir 缓存目录）。
     * 若传入的已是绝对路径则原样返回；否则视为缓存目录下的文件名。
     */
    public static String mediaPath(Context ctx, String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        File f = new File(fileName);
        if (f.isAbsolute()) return fileName;
        return new File(ctx.getFilesDir(), fileName).getAbsolutePath();
    }
}
