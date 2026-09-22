package com.algorobo.quiz;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 学习数据备份：把本地 SharedPreferences（题目/错题/收藏/统计/设置/知识点等）
 * 导出为 JSON 文件（默认建议保存到系统 Download），或从文件导入恢复。
 *
 * 注意：AI 的 API Key 由 AndroidKeyStore 设备级密钥加密，跨设备无法解密，
 * 因此导出内容不包含 API Key 相关配置（ai_configs_json / setting_ai_api_key）。
 */
public final class BackupUtil {
    private static final String[] EXCLUDED_KEYS = {
            "ai_configs_json", "setting_ai_api_key"
    };
    private static final String[] INCLUDED_PREFS_FILES = {
            DataStore.PREFS, "exam_custom_titles", "robot_catalog"
    };
    /** 各 prefs 文件内部实际存储的类型（写回时用，避免 Integer/Float 混淆）。 */
    private static final String META_TYPES = "_pref_types";

    private BackupUtil() {}

    public static String suggestedFileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "algorobo_backup_" + stamp + ".json";
    }

    /** 导出全部（不含敏感项）学习数据，返回导出的数据条数（失败返回 -1）。 */
    public static int export(Context c, Uri uri) {
        try {
            JsonObject root = new JsonObject();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            root.addProperty("app", "AlgoRobo");
            root.addProperty("exported_at", fmt.format(new Date()));
            for (String fileName : INCLUDED_PREFS_FILES) {
                android.content.SharedPreferences sp =
                        c.getSharedPreferences(fileName, Context.MODE_PRIVATE);
                JsonObject fileObj = new JsonObject();
                JsonObject typesObj = new JsonObject();
                Map<String, ?> all = sp.getAll();
                for (Map.Entry<String, ?> e : all.entrySet()) {
                    String key = e.getKey();
                    if (isExcluded(key)) continue;
                    Object v = e.getValue();
                    typesObj.addProperty(key, typeName(v));
                    if (v instanceof Set) {
                        JsonElement arr = new com.google.gson.Gson().toJsonTree(v);
                        fileObj.add(key, arr);
                    } else if (v instanceof Integer) {
                        fileObj.addProperty(key, (Integer) v);
                    } else if (v instanceof Long) {
                        fileObj.addProperty(key, (Long) v);
                    } else if (v instanceof Float) {
                        fileObj.addProperty(key, (Float) v);
                    } else if (v instanceof Boolean) {
                        fileObj.addProperty(key, (Boolean) v);
                    } else if (v instanceof String) {
                        fileObj.addProperty(key, (String) v);
                    }
                }
                fileObj.add(META_TYPES, typesObj);
                root.add(fileName, fileObj);
            }
            String json = new com.google.gson.Gson().toJson(root);
            OutputStream os = c.getContentResolver().openOutputStream(uri);
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            return countEntries(root);
        } catch (Exception e) {
            android.util.Log.e("BackupUtil", "export failed", e);
            return -1;
        }
    }

    /** 导入备份文件，覆盖本地数据（API Key 相关项跳过）。返回导入条数（失败 -1）。 */
    public static int importBackup(Context c, Uri uri) {
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            byte[] buf = readAll(in);
            JsonObject root = JsonParser.parseString(new String(buf, StandardCharsets.UTF_8)).getAsJsonObject();
            int n = 0;
            for (String fileName : INCLUDED_PREFS_FILES) {
                if (!root.has(fileName)) continue;
                JsonObject fileObj = root.getAsJsonObject(fileName);
                JsonObject typesObj = fileObj.has(META_TYPES) && fileObj.get(META_TYPES).isJsonObject()
                        ? fileObj.getAsJsonObject(META_TYPES) : new JsonObject();
                android.content.SharedPreferences sp =
                        c.getSharedPreferences(fileName, Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor ed = sp.edit();
                for (Map.Entry<String, JsonElement> e : fileObj.entrySet()) {
                    String key = e.getKey();
                    if (META_TYPES.contentEquals(key) || isExcluded(key)) continue;
                    JsonElement v = e.getValue();
                    String type = typesObj.has(key) ? typesObj.get(key).getAsString() : inferType(v);
                    if ("StringSet".equals(type) && v.isJsonArray()) {
                        Set<String> set = new java.util.HashSet<>();
                        for (JsonElement el : v.getAsJsonArray()) set.add(el.getAsString());
                        ed.putStringSet(key, set);
                    } else if ("Integer".equals(type)) {
                        ed.putInt(key, v.getAsInt());
                    } else if ("Long".equals(type)) {
                        ed.putLong(key, v.getAsLong());
                    } else if ("Float".equals(type)) {
                        ed.putFloat(key, v.getAsFloat());
                    } else if ("Boolean".equals(type)) {
                        ed.putBoolean(key, v.getAsBoolean());
                    } else {
                        ed.putString(key, v.getAsString());
                    }
                    n++;
                }
                ed.apply();
            }
            return n;
        } catch (Exception ex) {
            android.util.Log.e("BackupUtil", "import failed", ex);
            return -1;
        }
    }

    private static boolean isExcluded(String key) {
        for (String k : EXCLUDED_KEYS) {
            if (k.equals(key)) return true;
        }
        return false;
    }

    private static String typeName(Object v) {
        if (v instanceof Integer) return "Integer";
        if (v instanceof Long) return "Long";
        if (v instanceof Float) return "Float";
        if (v instanceof Boolean) return "Boolean";
        if (v instanceof Set) return "StringSet";
        return "String";
    }

    private static String inferType(JsonElement v) {
        if (v != null && v.isJsonArray()) return "StringSet";
        if (v != null && v.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive p = v.getAsJsonPrimitive();
            if (p.isBoolean()) return "Boolean";
            if (p.isNumber()) return "Integer";
        }
        return "String";
    }

    private static int countEntries(JsonObject root) {
        int n = 0;
        for (String file : INCLUDED_PREFS_FILES) {
            if (!root.has(file)) continue;
            n += Math.max(0, root.getAsJsonObject(file).size() - 1);
        }
        return n;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) bos.write(buf, 0, len);
        in.close();
        return bos.toByteArray();
    }
}
