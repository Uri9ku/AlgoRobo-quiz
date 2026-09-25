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

    /** 类目排序：保存 type_name → ordered_category_names 的映射 */
    public static void setCategoryOrder(Context c, String typeName, List<String> order) {
        sp(c).edit().putString("category_order_" + typeName, gson.toJson(order)).apply();
    }
    public static List<String> getCategoryOrder(Context c, String typeName) {
        String json = sp(c).getString("category_order_" + typeName, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>(){}.getType();
        return gson.fromJson(json, type);
    }
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

    /**
     * 清理历史缺陷残留：旧版按 id 生成 uid（docx 解析出的 id 恒为 0），
     * 导致同一套卷子所有题共用 "<卷key>#0"。修复后这类 key 不可能再匹配到题目，
     * 若不清掉会让错题本/收藏角标虚高。内置题与自定义题的前缀不受影响。
     */
    public static void purgeLegacyPaperKeys(Context c) {
        purgeLegacyKeySet(c, "wrong_ids");
        purgeLegacyKeySet(c, "favorite_ids");
    }

    private static void purgeLegacyKeySet(Context c, String prefKey) {
        SharedPreferences p = sp(c);
        Set<String> old = p.getStringSet(prefKey, null);
        if (old == null || old.isEmpty()) return;
        Set<String> kept = new HashSet<>();
        boolean changed = false;
        for (String uid : old) {
            if (isLegacyPaperKey(uid)) changed = true;
            else kept.add(uid);
        }
        if (changed) p.edit().putStringSet(prefKey, kept).apply();
    }

    private static boolean isLegacyPaperKey(String uid) {
        if (uid == null || !uid.endsWith("#0")) return false;
        return !uid.startsWith("custom#") && !uid.startsWith("builtin#");
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
    // 草稿纸底色不透明度（0~100，默认 80）
    public static int getDraftAlpha(Context c) {
        return sp(c).getInt("setting_draft_alpha", 80);
    }
    public static void setDraftAlpha(Context c, int alpha) {
        sp(c).edit().putInt("setting_draft_alpha", alpha).apply();
    }

    public static Set<String> getCheckinDays(Context c) {
        return new HashSet<>(sp(c).getStringSet("checkin_days", new HashSet<>()));
    }
    public static void addCheckin(Context c, String date) {
        Set<String> s = getCheckinDays(c); s.add(date);
        sp(c).edit().putStringSet("checkin_days", s).apply();
    }

    /**
     * 记录一次作答。
     * 全局/按日/按题型统计只在「首次作答」时累加，复刷仅累计复刷次数，避免重复刷题污染全局正确率。
     * 单题维度(qstat_{qid})仍保留全部尝试次数（用于展示"已做 N 次"）。
     * 返回 true 表示这是该题的首次作答（已计入统计）。
     */
    public static boolean recordAnswer(Context c, String qid, boolean correct, String type) {
        SharedPreferences p = sp(c);
        boolean firstTime = (qid == null || qid.isEmpty()) || !p.contains(firstKey(qid));
        if (firstTime) {
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
        } else {
            // 复刷：只累计复刷计数
            p.edit().putInt("stat_repeat", p.getInt("stat_repeat", 0) + 1).apply();
        }
        // 按题统计（qid 维度）：始终累加（含复刷）
        if (qid != null && !qid.isEmpty()) {
            String qk = "qstat_" + qid;
            int qTotal = p.getInt(qk + "_t", 0);
            int qRight = p.getInt(qk + "_r", 0);
            p.edit().putInt(qk + "_t", qTotal + 1).apply();
            if (correct) p.edit().putInt(qk + "_r", qRight + 1).apply();
        }
        if (firstTime) maybeAutoCheckin(c);
        return firstTime;
    }

    /** 复刷累计次数（不计入正确率的重复作答）。 */
    public static int getRepeatCount(Context c) { return sp(c).getInt("stat_repeat", 0); }

    /** 一次性迁移：把历史明文的 API Key（单配置字段 + 多配置 JSON）加密为密文存放。 */
    public static void migrateLegacySecrets(Context c) {
        try {
            String legacy = sp(c).getString("setting_ai_api_key", "");
            if (legacy != null && !legacy.isEmpty() && !SecretStore.isEncrypted(legacy)) {
                sp(c).edit().putString("setting_ai_api_key", SecretStore.encrypt(legacy)).apply();
            }
            // 触发多配置的明文→密文迁移（getAiConfigs 内部在检测到明文时会回写）
            getAiConfigs(c);
        } catch (Exception e) {
            android.util.Log.e("DataStore", "migrateLegacySecrets failed", e);
        }
    }

    // ==================== 打卡判定 ====================
    // 每日打卡自动判定阈值：当天首次作答达到 N 题自动打卡，范围 10~100，默认 20
    public static int getCheckinThreshold(Context c) {
        int v = sp(c).getInt("setting_checkin_threshold", 20);
        if (v < 10) v = 10;
        if (v > 100) v = 100;
        return v;
    }
    public static void setCheckinThreshold(Context c, int n) {
        if (n < 10) n = 10;
        if (n > 100) n = 100;
        sp(c).edit().putInt("setting_checkin_threshold", n).apply();
    }

    // ==================== 错题本：错误次数/最近做错（用于排序） ====================
    /** 错题重做排序方式：default=默认顺序，errcount=错误次数多优先，recent=最近做错优先。 */
    public static String getWrongSort(Context c) {
        return sp(c).getString("setting_wrong_sort", "default");
    }
    public static void setWrongSort(Context c, String order) {
        sp(c).edit().putString("setting_wrong_sort", order == null ? "default" : order).apply();
    }

    private static String wrongCountKey(String uid) { return "wrongcount_" + uid; }
    private static String wrongTimeKey(String uid) { return "wrongtime_" + uid; }

    /** 记录一次「答错」（无论该题是否第一次进错题本）：累计错误次数、刷新最近做错时间。 */
    public static void noteWrong(Context c, String uid) {
        if (uid == null || uid.isEmpty()) return;
        SharedPreferences p = sp(c);
        p.edit()
                .putInt(wrongCountKey(uid), p.getInt(wrongCountKey(uid), 0) + 1)
                .putLong(wrongTimeKey(uid), System.currentTimeMillis())
                .apply();
    }

    /** 该题累计答错次数（含已移出错题本的题）。 */
    public static int getWrongCount(Context c, String uid) {
        return sp(c).getInt(wrongCountKey(uid), 0);
    }

    /** 该题最近一次答错的时间戳（毫秒，0 表示从未记录）。 */
    public static long getWrongTime(Context c, String uid) {
        return sp(c).getLong(wrongTimeKey(uid), 0L);
    }

    /** 打卡判定：当天「首次作答」达到阈值 N 时自动记为打卡（手动打卡仍然可用）。返回 true 表示本次自动打卡成功。 */
    public static boolean maybeAutoCheckin(Context c) {
        int threshold = getCheckinThreshold(c);
        String today = today();
        Set<String> days = getCheckinDays(c);
        if (days.contains(today)) return false;
        int dayFirst = sp(c).getInt("day_" + today + "_t", 0);
        if (dayFirst < threshold) return false;
        addCheckin(c, today);
        return true;
    }

    public static int[] getQuestionStat(Context c, String qid) {
        SharedPreferences p = sp(c);
        String qk = "qstat_" + qid;
        return new int[]{ p.getInt(qk + "_t", 0), p.getInt(qk + "_r", 0) };
    }

    public static int getTotal(Context c) { return sp(c).getInt("stat_total", 0); }
    public static int getRight(Context c) { return sp(c).getInt("stat_right", 0); }

    // 返回各题型的 {总答题数, 答对数}。
    // 题型来源：内置题库的题型 + 已落库统计过的所有 type_* 键（含导入真题/docx 解析出的自定义题型）。
    public static Map<String, int[]> getTypeStats(Context c) {
        java.util.Map<String, int[]> map = new java.util.LinkedHashMap<>();
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (Question q : QuestionBank.getAll()) {
            if (q.type != null && !q.type.isEmpty()) types.add(q.type);
        }
        SharedPreferences p = sp(c);
        // 扫描实际统计过（写过 type_ 前缀键）的题型：含导入真题的题型
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("type_") && k.endsWith("_t")) {
                types.add(k.substring("type_".length(), k.length() - 2));
            }
        }
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
        String stored = sp(c).getString("setting_ai_api_key", "");
        // 历史明文迁移：读到未加密的旧值时立即加密写回
        if (stored != null && !stored.isEmpty() && !SecretStore.isEncrypted(stored)) {
            sp(c).edit().putString("setting_ai_api_key", SecretStore.encrypt(stored)).apply();
        }
        return SecretStore.decrypt(stored);
    }
    public static void setAiApiKey(Context c, String key) {
        sp(c).edit().putString("setting_ai_api_key", SecretStore.encrypt(key == null ? "" : key.trim())).apply();
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

    /** 读取所有 AI 配置列表（apiKey 已解密，密文持久化；历史明文会在读取时自动迁移加密）。 */
    public static List<AiApi.Config> getAiConfigs(Context c) {
        List<AiApi.Config> list = loadAiConfigs(c);
        boolean migrate = false;
        for (AiApi.Config cfg : list) {
            String stored = cfg.apiKey == null ? "" : cfg.apiKey;
            if (stored.isEmpty()) continue;
            cfg.apiKey = SecretStore.decrypt(stored);   // 解密为明文
            if (!SecretStore.isEncrypted(stored)) migrate = true; // 历史明文 → 需回写加密
        }
        if (migrate) saveAiConfigs(c, list); // saveAiConfigs 内部统一加密
        return list;
    }

    private static List<AiApi.Config> loadAiConfigs(Context c) {
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

    /** 从旧的单配置字段迁移得到默认配置（保持兼容）。apiKey 由调用方负责解密（defaultConfig 内 key 为明文迁移源）。 */
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

    /** 持久化所有 AI 配置列表（apiKey 加密后存储）。 */
    public static void saveAiConfigs(Context c, List<AiApi.Config> configs) {
        List<AiApi.Config> encrypted = new ArrayList<>();
        if (configs != null) {
            for (AiApi.Config src : configs) {
                AiApi.Config copy = new AiApi.Config(src.name, src.provider, src.endpoint,
                        SecretStore.encrypt(src.apiKey == null ? "" : src.apiKey.trim()),
                        src.models, src.vision);
                encrypted.add(copy);
            }
        }
        String json = gson.toJson(encrypted);
        sp(c).edit().putString(KEY_AI_CONFIGS, json).apply();
    }

    /** 当前选中的配置名。 */
    public static String getAiCurrentConfig(Context c) {        String name = sp(c).getString(KEY_AI_CURRENT, "");
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

    /** AI 角色设定（解析风格）：空表示使用内置默认老师口吻。 */
    public static String getAiRole(Context c) {
        return sp(c).getString("setting_ai_role", "");
    }

    public static void setAiRole(Context c, String role) {
        sp(c).edit().putString("setting_ai_role", role == null ? "" : role).apply();
    }

    /** 在线导入时并发下载/解析的真题套数，默认 3，范围 1~10。 */
    public static int getImportConcurrency(Context c) {
        int v = sp(c).getInt("setting_import_concurrency", 3);
        if (v < 1) v = 1;
        if (v > 10) v = 10;
        return v;
    }

    public static void setImportConcurrency(Context c, int v) {
        if (v < 1) v = 1;
        if (v > 10) v = 10;
        sp(c).edit().putInt("setting_import_concurrency", v).apply();
    }

    // ==================== 题库下载目录 ====================

    /** 默认下载目录：系统公共 Download 目录（便于用文件管理器/WPS 找到原卷）。 */
    public static java.io.File defaultExamDir(Context c) {
        java.io.File pub = null;
        try {
            pub = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
        } catch (Exception ignore) {
        }
        if (pub != null) return pub;
        return fallbackExamDir(c);
    }

    /** 无存储权限时的回退目录：应用专属外部目录，读写无需任何权限。 */
    public static java.io.File fallbackExamDir(Context c) {
        java.io.File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        return new java.io.File(base, "exam_downloads");
    }

    /** 是否已获得写入公共目录的权限（Android 11+ 为「所有文件访问权限」，10 及以下为存储读写权限）。 */
    public static boolean hasStorageAccess(Context c) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                return android.os.Environment.isExternalStorageManager();
            }
            return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 申请存储权限：Android 11+ 跳系统「所有文件访问权限」页，10 及以下走运行时授权。 */
    public static void requestStorageAccess(Activity act) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + act.getPackageName()));
                act.startActivity(i);
            } else {
                act.requestPermissions(
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 9101);
            }
        } catch (Exception e) {
            toast(act, "无法打开权限设置，请在系统设置中授予存储权限");
        }
    }

    // 获取题库下载目录（绝对路径字符串）。未设置时返回默认目录；无权限时回退到应用专属目录。
    public static String getExamDir(Context c) {
        String s = sp(c).getString("setting_exam_dir", "");
        if (s != null && !s.isEmpty()) {
            return s;
        }
        if (!hasStorageAccess(c)) {
            return fallbackExamDir(c).getAbsolutePath();
        }
        return defaultExamDir(c).getAbsolutePath();
    }

    public static void setExamDir(Context c, String path) {
        sp(c).edit().putString("setting_exam_dir", path == null ? "" : path).apply();
    }

    /** 在线导入时是否自动下载原题 docx（关闭则只解析入库、不保存原文件），默认开启。 */
    public static boolean isAutoDownloadDocx(Context c) {
        return sp(c).getBoolean("setting_auto_download_docx", true);
    }

    public static void setAutoDownloadDocx(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_auto_download_docx", on).apply();
    }

    /** 首页是否显示数量角标（错题本 / 收藏题），默认显示。 */
    public static boolean isBadgeVisible(Context c) {
        return sp(c).getBoolean("setting_badge_visible", true);
    }
    public static void setBadgeVisible(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_badge_visible", on).apply();
    }

    /** 是否开启「开发者模式」悬浮球（关闭后每个页面都不显示），默认开启。 */
    public static boolean isDevModeEnabled(Context c) {
        return sp(c).getBoolean("setting_dev_mode", true);
    }
    public static void setDevModeEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_dev_mode", on).apply();
    }

    /** 角标数字变化动画时长（毫秒），越小越快，默认 300。 */
    public static int getBadgeAnimDuration(Context c) {
        return sp(c).getInt("setting_badge_anim_ms", 300);
    }
    public static void setBadgeAnimDuration(Context c, int ms) {
        sp(c).edit().putInt("setting_badge_anim_ms", ms).apply();
    }

    /** 刷题页是否显示「知识点」标签（分值右侧），默认显示。 */
    public static boolean isKnowledgeTagVisible(Context c) {
        return sp(c).getBoolean("setting_knowledge_tag_visible", true);
    }

    public static void setKnowledgeTagVisible(Context c, boolean on) {
        sp(c).edit().putBoolean("setting_knowledge_tag_visible", on).apply();
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
        if (dirPath == null || dirPath.isEmpty()) {
            dirPath = defaultExamDir(c).getAbsolutePath();
        }
        // 1) 用户选的是 SAF 目录（content://）：直接按文档 URI 打开该目录
        if (dirPath != null && dirPath.startsWith("content://")) {
            if (viewDocumentUri(act, Uri.parse(dirPath))) return true;
        }
        try {
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            if (!dir.exists()) {
                toast(c, "下载目录不存在且无法创建：" + dirPath);
                return false;
            }
            // 2) 优先用系统「文档提供者」的目录 URI：
            //    系统文件管理器（Files/DocumentsUI）只认识 externalstorage 这类 authority，
            //    用我们自己的 FileProvider URI 会解析失败并退回其默认位置（表现为总是打开 Download）。
            Uri docUri = toExternalStorageDocUri(dirPath);
            android.util.Log.i("OpenExamDir", "dir=" + dirPath + " docUri=" + docUri);
            if (docUri != null && viewDocumentUri(act, docUri)) return true;
            // 3) 回退：FileProvider URI + 多种 MIME
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", dir);
            } catch (IllegalArgumentException fe) {
                toast(c, "无法打开该目录，请在设置中重新选择下载目录");
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
            toast(c, "未找到可打开目录的应用。目录路径：" + dirPath);
            return false;
        } catch (Exception e) {
            android.util.Log.e("OpenExamDir", "open dir failed", e);
            toast(c, "打开目录失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 用系统「文档」界面打开一个目录 URI。
     * 关键点：部分 ROM（ColorOS 等）会因包可见性/AppFilter 丢弃「隐式」目录意图
     * （startActivity 不抛异常但什么也不发生，表现为点「打开目录」没反应/打开错位置），
     * 因此这里先按显式组件启动能处理该意图的应用，再退回隐式启动。
     */
    private static boolean viewDocumentUri(final Activity act, final Uri uri) {
        if (act == null || uri == null) return false;
        Intent base = new Intent(Intent.ACTION_VIEW);
        base.setDataAndType(uri, "vnd.android.document/directory");
        base.putExtra("android.provider.extra.INITIAL_URI", uri);
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            java.util.List<android.content.pm.ResolveInfo> list =
                    act.getPackageManager().queryIntentActivities(base, 0);
            for (android.content.pm.ResolveInfo ri : list) {
                Intent explicit = new Intent(base);
                explicit.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (startActivitySafe(act, explicit)) return true;
            }
        } catch (Exception e) {
            android.util.Log.w("OpenExamDir", "resolve doc-dir handler failed", e);
        }
        Intent implicit = new Intent(base);
        implicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (startActivitySafe(act, implicit)) return true;
        return false;
    }

    /**
     * 将主外部存储下的绝对路径转换为系统文档提供者的目录 URI：
     * /storage/emulated/0/无人机 → content://com.android.externalstorage.documents/document/primary%3A无人机
     */
    private static Uri toExternalStorageDocUri(String path) {
        try {
            if (path == null) return null;
            String primary = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
            if (!path.startsWith(primary)) {
                android.util.Log.i("OpenExamDir", "not under primary: " + path + " vs " + primary);
                return null;
            }
            String rel = path.substring(primary.length());
            while (rel.startsWith("/")) rel = rel.substring(1);
            String docId = "primary:" + rel;
            return Uri.parse("content://com.android.externalstorage.documents/document/"
                    + Uri.encode(docId));
        } catch (Exception e) {
            android.util.Log.w("OpenExamDir", "toExternalStorageDocUri failed", e);
            return null;
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