package com.algorobo.quiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataStore {
    public static final String PREFS = "algorobo_prefs";
    private static final Gson gson = new Gson();

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static Set<String> getWrongIds(Context c) {
        return new HashSet<>(sp(c).getStringSet("wrong_ids", new HashSet<>()));
    }
    public static void addWrong(Context c, String uid) {
        Set<String> s = getWrongIds(c); s.add(uid);
        sp(c).edit().putStringSet("wrong_ids", s).apply();
    }
    public static void removeWrong(Context c, String uid) {
        Set<String> s = getWrongIds(c); s.remove(uid);
        sp(c).edit().putStringSet("wrong_ids", s).apply();
    }
    public static void clearWrong(Context c) {
        // 清空错题本时，一并清空所有「做对次数」计数
        SharedPreferences p = sp(c);
        SharedPreferences.Editor ed = p.edit();
        ed.remove("wrong_ids");
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("correct_count_")) ed.remove(k);
        }
        ed.apply();
    }

    // ==================== 错题本：做对次数追踪 ====================

    // 做对次数阈值（错题做对 N 次后自动移除），默认 2
    public static int getWrongThreshold(Context c) {
        return sp(c).getInt("setting_wrong_threshold", 2);
    }
    public static void setWrongThreshold(Context c, int n) {
        sp(c).edit().putInt("setting_wrong_threshold", n).apply();
    }

    // 自动加入错题本开关，默认开启
    public static boolean isAutoWrong(Context c) {
        return sp(c).getBoolean("setting_auto_wrong", true);
    }
    public static void setAutoWrong(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_auto_wrong", on).apply();
    }

    private static String correctCountKey(String uid) { return "correct_count_" + uid; }
    // 获取某题的「做对次数」（最终判定为正确答案的累计次数）
    public static int getCorrectCount(Context c, String uid) {
        return sp(c).getInt(correctCountKey(uid), 0);
    }
    // 做对一次：计数 +1
    public static void incCorrectCount(Context c, String uid) {
        sp(c).edit().putInt(correctCountKey(uid), getCorrectCount(c, uid) + 1).apply();
    }
    // 清空某题的做对次数计数
    public static void resetCorrectCount(Context c, String uid) {
        sp(c).edit().remove(correctCountKey(uid)).apply();
    }
    // 存储每道题第一次作答的答案索引（-1 表示尚未作答过），用于展示"第一次写的答案"
    private static String firstKey(String uid) { return "first_answer_" + uid; }
    public static boolean hasFirstAnswer(Context c, String uid) { return sp(c).contains(firstKey(uid)); }
    public static int getFirstAnswer(Context c, String uid) { return sp(c).getInt(firstKey(uid), -1); }
    public static void setFirstAnswer(Context c, String uid, int answerIndex) {
        if (!sp(c).contains(firstKey(uid))) {
            sp(c).edit().putInt(firstKey(uid), answerIndex).apply();
        }
    }

    // ==================== 收藏 ====================

    public static Set<String> getFavoriteIds(Context c) {
        return new HashSet<>(sp(c).getStringSet("favorite_ids", new HashSet<>()));
    }
    public static boolean isFavorite(Context c, String uid) {
        return getFavoriteIds(c).contains(uid);
    }
    public static void addFavorite(Context c, String uid) {
        Set<String> s = getFavoriteIds(c); s.add(uid);
        sp(c).edit().putStringSet("favorite_ids", s).apply();
    }
    public static void removeFavorite(Context c, String uid) {
        Set<String> s = getFavoriteIds(c); s.remove(uid);
        sp(c).edit().putStringSet("favorite_ids", s).apply();
    }
    public static void toggleFavorite(Context c, String uid) {
        if (isFavorite(c, uid)) removeFavorite(c, uid); else addFavorite(c, uid);
    }
    // ==================== 字号设置（小/中/大） ====================
    public static int getFontScale(Context c) {
        // 0=小, 1=中, 2=大
        return sp(c).getInt("setting_font_scale", 1);
    }
    public static void setFontScale(Context c, int scale) {
        sp(c).edit().putInt("setting_font_scale", scale).apply();
    }
    // 真题题干字号（sp），默认 18
    public static float getQuestionFontSp(Context c) {
        return sp(c).getFloat("setting_question_font_sp", 18f);
    }
    public static void setQuestionFontSp(Context c, float sp) {
        sp(c).edit().putFloat("setting_question_font_sp", sp).apply();
    }
    // 试卷标题自动滚动（跑马灯），默认开启
    public static boolean isTitleMarquee(Context c) {
        return sp(c).getBoolean("setting_title_marquee", true);
    }
    public static void setTitleMarquee(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_title_marquee", on).apply();
    }

    public static Set<String> getCheckinDays(Context c) {
        return new HashSet<>(sp(c).getStringSet("checkin_days", new HashSet<>()));
    }
    public static void addCheckin(Context c, String date) {
        Set<String> s = getCheckinDays(c); s.add(date);
        sp(c).edit().putStringSet("checkin_days", s).apply();
    }

    public static void recordAnswer(Context c, String qid, boolean correct, String type) {
        SharedPreferences p = sp(c);
        int total = p.getInt("stat_total", 0);
        int right = p.getInt("stat_right", 0);
        p.edit().putInt("stat_total", total + 1).apply();
        if (correct) p.edit().putInt("stat_right", right + 1).apply();

        String today = today();
        String key = "day_" + today;
        int dayTotal = p.getInt(key + "_t", 0);
        int dayRight = p.getInt(key + "_r", 0);
        p.edit().putInt(key + "_t", dayTotal + 1).apply();
        if (correct) p.edit().putInt(key + "_r", dayRight + 1).apply();

        // 按题型累加统计（正确率柱状图用）
        if (type != null && !type.isEmpty()) {
            String tk = "type_" + type;
            int tTotal = p.getInt(tk + "_t", 0);
            int tRight = p.getInt(tk + "_r", 0);
            p.edit().putInt(tk + "_t", tTotal + 1).apply();
            if (correct) p.edit().putInt(tk + "_r", tRight + 1).apply();
        }
    }

    public static int getTotal(Context c) { return sp(c).getInt("stat_total", 0); }
    public static int getRight(Context c) { return sp(c).getInt("stat_right", 0); }

    // 返回各题型的 {总答题数, 答对数}，题型顺序与 QuestionBank 一致
    public static Map<String, int[]> getTypeStats(Context c) {
        java.util.Map<String, int[]> map = new java.util.LinkedHashMap<>();
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (Question q : QuestionBank.getAll()) {
            if (q.type != null && !q.type.isEmpty()) types.add(q.type);
        }
        SharedPreferences p = sp(c);
        for (String type : types) {
            String tk = "type_" + type;
            map.put(type, new int[]{ p.getInt(tk + "_t", 0), p.getInt(tk + "_r", 0) });
        }
        return map;
    }

    public static int[] getRecent7Days(Context c) {
        int[] arr = new int[7];
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            java.util.Calendar d = (java.util.Calendar) cal.clone();
            d.add(java.util.Calendar.DAY_OF_YEAR, -i);
            String k = "day_" + fmt.format(d.getTime()) + "_t";
            arr[6 - i] = sp(c).getInt(k, 0);
        }
        return arr;
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
    }

    // ==================== 设置项 ====================

    // Toast 显示时长（毫秒），默认 2000（LENGTH_SHORT）
    public static int getToastDuration(Context c) {
        return sp(c).getInt("setting_toast_duration", 2000);
    }
    public static void setToastDuration(Context c, int ms) {
        sp(c).edit().putInt("setting_toast_duration", ms).apply();
    }

    // 题目切换动画时长（毫秒），默认 250
    public static int getAnimDuration(Context c) {
        return sp(c).getInt("setting_anim_duration", 250);
    }
    public static void setAnimDuration(Context c, int ms) {
        sp(c).edit().putInt("setting_anim_duration", ms).apply();
    }

    // 深色模式：0=跟随系统，1=浅色，2=深色，默认 0
    public static int getDarkMode(Context c) {
        return sp(c).getInt("setting_dark_mode", 0);
    }
    public static void setDarkMode(Context c, int mode) {
        sp(c).edit().putInt("setting_dark_mode", mode).apply();
    }

    // 主题色由 ThemeManager 统一管理（见 ThemeManager.getThemeColor）
    // 自定义项：自动翻页（待完善）示例，0=关闭，1=开启
    public static boolean isAutoNext(Context c) {
        return sp(c).getBoolean("setting_auto_next", false);
    }
    public static void setAutoNext(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_auto_next", on).apply();
    }

    // ==================== 自定义题库 ====================

    private static final String KEY_CUSTOM_QUESTIONS = "custom_questions_json";

    public static List<Question> getCustomQuestions(Context c) {
        String json = sp(c).getString(KEY_CUSTOM_QUESTIONS, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<Question>>() {}.getType();
            List<Question> list = gson.fromJson(json, type);
            if (list == null) return new ArrayList<Question>();
            for (Question q : list) q.uid = Question.makeUid("custom", q.id);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void setCustomQuestions(Context c, List<Question> questions) {
        String json = gson.toJson(questions == null ? new ArrayList<Question>() : questions);
        sp(c).edit().putString(KEY_CUSTOM_QUESTIONS, json).apply();
    }

    public static void addCustomQuestions(Context c, List<Question> questions) {
        List<Question> all = getCustomQuestions(c);
        all.addAll(questions);
        setCustomQuestions(c, all);
    }

    public static void clearCustomQuestions(Context c) {
        sp(c).edit().remove(KEY_CUSTOM_QUESTIONS).apply();
    }
    // ==================== AI 模型配置 ====================
    public static String getAiModel(Context c) {
        return sp(c).getString("setting_ai_model", "operit");
    }
    public static void setAiModel(Context c, String model) {
        sp(c).edit().putString("setting_ai_model", model).apply();
    }
    public static String getAiApiKey(Context c) {
        return sp(c).getString("setting_ai_api_key", "");
    }
    public static void setAiApiKey(Context c, String key) {
        sp(c).edit().putString("setting_ai_api_key", key == null ? "" : key.trim()).apply();
    }
    public static String getAiBaseUrl(Context c) {
        return sp(c).getString("setting_ai_base_url", "");
    }
    public static void setAiBaseUrl(Context c, String url) {
        sp(c).edit().putString("setting_ai_base_url", url == null ? "" : url.trim()).apply();
    }

    // ==================== 多套 AI 配置管理 ====================

    private static final String KEY_AI_CONFIGS = "ai_configs_json";
    private static final String KEY_AI_CURRENT = "ai_current_config";

    /** 读取所有 AI 配置列表。 */
    public static List<AiApi.Config> getAiConfigs(Context c) {
        String json = sp(c).getString(KEY_AI_CONFIGS, null);
        if (json == null || json.isEmpty()) {
            List<AiApi.Config> list = new ArrayList<>();
            list.add(defaultConfig(c));
            return list;
        }
        try {
            Type type = new TypeToken<List<AiApi.Config>>() {}.getType();
            List<AiApi.Config> list = gson.fromJson(json, type);
            if (list == null || list.isEmpty()) {
                list = new ArrayList<>();
                list.add(defaultConfig(c));
            }
            return list;
        } catch (Exception e) {
            List<AiApi.Config> list = new ArrayList<>();
            list.add(defaultConfig(c));
            return list;
        }
    }

    /** 从旧的单配置字段迁移得到默认配置（保持兼容）。 */
    private static AiApi.Config defaultConfig(Context c) {
        String model = getAiModel(c);
        String provider = "custom";
        String endpoint = getAiBaseUrl(c);
        String key = getAiApiKey(c);
        String models = model;
        if ("operit".equals(model)) {
            provider = "Operit AI";
            endpoint = endpoint.isEmpty() ? "https://api.openai.com/v1/chat/completions" : endpoint;
            models = models.isEmpty() ? "" : models;
        } else if ("openai".equals(model)) {
            provider = "OpenAI";
            endpoint = endpoint.isEmpty() ? "https://api.openai.com/v1/chat/completions" : endpoint;
        }
        return new AiApi.Config("默认配置", provider, endpoint, key, models);
    }

    /** 持久化所有 AI 配置列表。 */
    public static void saveAiConfigs(Context c, List<AiApi.Config> configs) {
        String json = gson.toJson(configs == null ? new ArrayList<AiApi.Config>() : configs);
        sp(c).edit().putString(KEY_AI_CONFIGS, json).apply();
    }

    /** 当前选中的配置名。 */
    public static String getAiCurrentConfig(Context c) {
        String name = sp(c).getString(KEY_AI_CURRENT, "");
        if (name == null || name.isEmpty()) {
            return "默认配置";
        }
        return name;
    }

    public static void setAiCurrentConfig(Context c, String name) {
        sp(c).edit().putString(KEY_AI_CURRENT, name == null ? "默认配置" : name).apply();
    }

    /** 获取当前生效的配置（找不到则回退到第一套）。 */
    public static AiApi.Config getCurrentAiConfig(Context c) {
        List<AiApi.Config> list = getAiConfigs(c);
        String cur = getAiCurrentConfig(c);
        for (AiApi.Config cfg : list) {
            if (cfg.name != null && cfg.name.equals(cur)) return cfg;
        }
        return list.isEmpty() ? new AiApi.Config("默认配置", "", "", "", "") : list.get(0);
    }

    // ==================== 题库下载目录 ====================

    // 默认下载目录：应用外部专属目录下的 exam_downloads 子目录
    public static java.io.File defaultExamDir(Context c) {
        java.io.File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        return new java.io.File(base, "exam_downloads");
    }

    // 获取题库下载目录（绝对路径字符串）。未设置时返回默认目录。
    public static String getExamDir(Context c) {
        String s = sp(c).getString("setting_exam_dir", "");
        if (s == null || s.isEmpty()) {
            return defaultExamDir(c).getAbsolutePath();
        }
        return s;
    }

    public static void setExamDir(Context c, String path) {
        sp(c).edit().putString("setting_exam_dir", path == null ? "" : path).apply();
    }

    // 知识点缓存：key 为题目唯一标识，value 为识别出的知识点文本
    public static String getKnowledge(Context c, String qid) {
        return sp(c).getString("knowledge_" + qid, null);
    }
    public static void setKnowledge(Context c, String qid, String knowledge) {
        sp(c).edit().putString("knowledge_" + qid, knowledge == null ? "" : knowledge).apply();
    }
    public static boolean hasKnowledge(Context c, String qid) {
        return sp(c).contains("knowledge_" + qid);
    }
    public static void clearKnowledge(Context c) {
        SharedPreferences p = sp(c);
        java.util.Map<String, ?> all = p.getAll();
        SharedPreferences.Editor ed = p.edit();
        for (String k : all.keySet()) {
            if (k.startsWith("knowledge_")) ed.remove(k);
        }
        ed.apply();
    }

        // ==================== AI 解析缓存 ====================
    // key 为题目唯一标识，value 为 AI 生成的解析文本
    public static String getAiAnalysis(Context c, String uid) {
        return sp(c).getString("ai_analysis_" + uid, null);
    }
    public static void setAiAnalysis(Context c, String uid, String analysis) {
        sp(c).edit().putString("ai_analysis_" + uid, analysis == null ? "" : analysis).apply();
    }
    public static boolean hasAiAnalysis(Context c, String uid) {
        return sp(c).contains("ai_analysis_" + uid);
    }
    public static void clearAiAnalysis(Context c) {
        SharedPreferences p = sp(c);
        java.util.Map<String, ?> all = p.getAll();
        SharedPreferences.Editor ed = p.edit();
        for (String k : all.keySet()) {
            if (k.startsWith("ai_analysis_")) ed.remove(k);
        }
        ed.apply();
    }
    // AI 自动解析开关，默认关闭
    public static boolean getAutoAiAnalysis(Context c) {
        return sp(c).getBoolean("setting_auto_ai_analysis", false);
    }
    public static void setAutoAiAnalysis(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_auto_ai_analysis", on).apply();
    }
    // AI 自动解析模式："all"=对错都生成解析，"wrong"=只对错题解析
    public static String getAutoAiAnalysisMode(Context c) {
        return sp(c).getString("setting_auto_ai_analysis_mode", "all");
    }
    public static void setAutoAiAnalysisMode(Context c, String mode) {
        sp(c).edit().putString("setting_auto_ai_analysis_mode", mode == null ? "all" : mode).apply();
    }

// ==================== 统一打开考试下载目录 ====================
    /**
     * 打开题库下载目录。统一 FileProvider + 多 MIME 遍历 + content URI 兼容。
     * 供 ExamListActivity 与 SettingsActivity 共用。
     * 返回 true 表示成功拉起文件管理器。
     */
    public static boolean openExamDir(Context c, Activity act) {
        String dirPath = getExamDir(c);
        // 特判：若存储的是 content:// URI，改用 ACTION_OPEN_DOCUMENT_TREE 或提示用户
        if (dirPath != null && dirPath.startsWith("content://")) {
            try {
                Uri contentUri = Uri.parse(dirPath);
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                pick.setData(contentUri);
                pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                act.startActivity(pick);
                return true;
            } catch (Exception e) {
                // 继续尝试按文件路径处理
            }
        }
        if (dirPath == null || dirPath.isEmpty()) {
            dirPath = defaultExamDir(c).getAbsolutePath();
        }
        try {
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            if (!dir.exists()) {
                toast(c, "下载目录不存在：" + dirPath);
                return false;
            }
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", dir);
            } catch (IllegalArgumentException fe) {
                // FileProvider 无法映射该路径（如 /storage 其他卷），退回 file:// 会触发 FileUriExposed，
                // 这里直接给出明确提示。
                toast(c, "无法暴露该目录，请在设置中重新选择下载目录");
                android.util.Log.e("OpenExamDir", "FileProvider root not configured for: " + dirPath, fe);
                return false;
            }
            String[] mimes = new String[]{
                    "vnd.android.document/directory",
                    "resource/folder",
                    "inode/directory",
                    "application/vnd.android.document/directory"
            };
            for (String mime : mimes) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, mime);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                if (startActivitySafe(act, intent)) return true;
            }
            Intent fallback = new Intent(Intent.ACTION_VIEW);
            fallback.setData(uri);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (startActivitySafe(act, fallback)) return true;
            toast(c, "未找到可打开目录的应用");
            return false;
        } catch (Exception e) {
            android.util.Log.e("OpenExamDir", "open dir failed", e);
            toast(c, "打开目录失败：" + e.getMessage());
            return false;
        }
    }

    private static boolean startActivitySafe(Activity act, Intent intent) {
        try {
            act.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void toast(Context c, String msg) {
        try { Toast.makeText(c, msg, Toast.LENGTH_LONG).show(); } catch (Exception ignore) {}
    }

    // ==================== 首页进度概览便捷 Getter ====================
    // 今日答题数（总/对）
    public static int[] getTodayStats(Context c) {
        String t = today();
        SharedPreferences p = sp(c);
        return new int[]{ p.getInt("day_" + t + "_t", 0), p.getInt("day_" + t + "_r", 0) };
    }
    // 连续打卡天数（从今天（或昨天）向前连续）
    public static int getStreakDays(Context c) {
        Set<String> days = getCheckinDays(c);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        int streak = 0;
        // 若今天未打卡，从昨天开始算
        if (!days.contains(fmt.format(cal.getTime()))) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        }
        while (days.contains(fmt.format(cal.getTime()))) {
            streak++;
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }
    // 正确率（百分比 0-100）
    public static int getAccuracy(Context c) {
        int total = getTotal(c);
        if (total == 0) return 0;
        return Math.round(getRight(c) * 100f / total);
    }

    // ==================== 继续上次进度持久化 ====================
    // 保存进度：题目 uid 列表 + 当前索引 + source + random 标志
    public static void saveResume(Context c, List<String> uids, int index, String source, boolean random) {
        SharedPreferences.Editor ed = sp(c).edit();
        ed.putString("resume_uids", gson.toJson(uids == null ? new ArrayList<String>() : uids));
        ed.putInt("resume_index", index);
        ed.putString("resume_source", source == null ? "all" : source);
        ed.putBoolean("resume_random", random);
        ed.putLong("resume_time", System.currentTimeMillis());
        ed.apply();
    }
    public static void clearResume(Context c) {
        sp(c).edit().remove("resume_uids").remove("resume_index")
                .remove("resume_source").remove("resume_random").remove("resume_time").apply();
    }
    // 读取进度：返回 [uids, index, source, random]，无进度或题目列表快照过期则返回 null
    public static ResumeState getResume(Context c) {
        SharedPreferences p = sp(c);
        String json = p.getString("resume_uids", null);
        if (json == null || json.isEmpty()) return null;
        Type listType = new TypeToken<ArrayList<String>>(){}.getType();
        List<String> uids;
        try { uids = gson.fromJson(json, listType); } catch (Exception e) { return null; }
        if (uids == null || uids.isEmpty()) return null;
        int index = p.getInt("resume_index", 0);
        String source = p.getString("resume_source", "all");
        boolean random = p.getBoolean("resume_random", false);
        return new ResumeState(uids, index, source, random);
    }
    // 进度快照封装
    public static class ResumeState {
        public final List<String> uids;
        public final int index;
        public final String source;
        public final boolean random;
        public ResumeState(List<String> uids, int index, String source, boolean random) {
            this.uids = uids; this.index = index; this.source = source; this.random = random;
        }
    }

}