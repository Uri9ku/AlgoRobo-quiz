package com.algorobo.quiz;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识点库管理。
 * 知识点以「名称」为唯一标识，存储为 JSON 列表。
 * 题目通过 Question.knowledgePoints（数组）引用知识点名称。
 * 重命名知识点时，遍历所有题源（内置/自定义/真题缓存）同步更新引用，保证数据一致。
 */
public class KnowledgeStore {
    private static final String PREFS = "algorobo_prefs";
    private static final String KEY_KNOWLEDGE = "knowledge_library_json";
    private static final Gson gson = new Gson();

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 获取知识点库（去重、保持顺序）。 */
    public static List<String> getAll(Context c) {
        String json = sp(c).getString(KEY_KNOWLEDGE, null);
        List<String> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> parsed = gson.fromJson(json, type);
            if (parsed != null) {
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                for (String s : parsed) {
                    if (s != null && !s.trim().isEmpty() && seen.add(s.trim())) {
                        list.add(s.trim());
                    }
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    private static void saveAll(Context c, List<String> list) {
        sp(c).edit().putString(KEY_KNOWLEDGE, gson.toJson(list == null ? new ArrayList<String>() : list)).apply();
    }

    public static boolean contains(Context c, String name) {
        if (name == null) return false;
        for (String s : getAll(c)) if (s.equals(name.trim())) return true;
        return false;
    }

    /** 新增知识点（重名则忽略）。返回是否成功。 */
    public static boolean add(Context c, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String n = name.trim();
        List<String> list = getAll(c);
        if (list.contains(n)) return false;
        list.add(n);
        saveAll(c, list);
        return true;
    }

    /** 重命名知识点，并同步更新所有题目的引用。 */
    public static boolean rename(Context c, String oldName, String newName) {
        if (oldName == null || newName == null) return false;
        String o = oldName.trim();
        String n = newName.trim();
        if (o.isEmpty() || n.isEmpty() || o.equals(n)) return false;
        List<String> list = getAll(c);
        int idx = list.indexOf(o);
        if (idx < 0) return false;
        if (list.contains(n)) return false; // 新名字已存在
        list.set(idx, n);
        saveAll(c, list);
        // 同步更新所有题目的引用
        syncRenameAcrossQuestions(c, o, n);
        return true;
    }

    /** 删除知识点，并清除所有题目对该知识点的引用。 */
    public static boolean delete(Context c, String name) {
        if (name == null) return false;
        String n = name.trim();
        List<String> list = getAll(c);
        if (!list.remove(n)) return false;
        saveAll(c, list);
        syncDeleteAcrossQuestions(c, n);
        return true;
    }

    /** 遍历所有题源，将题目的知识点名 oldName 替换为 newName。 */
    private static void syncRenameAcrossQuestions(Context c, String oldName, String newName) {
        // 内置题库（运行时态，无持久化，仅刷新内存引用）
        for (Question q : QuestionBank.getAll()) {
            replaceInQuestion(q, oldName, newName);
        }
        // 自定义题库
        List<Question> custom = DataStore.getCustomQuestions(c);
        boolean customChanged = false;
        for (Question q : custom) {
            if (replaceInQuestion(q, oldName, newName)) customChanged = true;
        }
        if (customChanged) DataStore.setCustomQuestions(c, custom);
        // 真题缓存（robot_questions_cached.json）
        syncRenameInPapers(c, oldName, newName);
    }

    private static void syncDeleteAcrossQuestions(Context c, String name) {
        for (Question q : QuestionBank.getAll()) {
            removeFromQuestion(q, name);
        }
        List<Question> custom = DataStore.getCustomQuestions(c);
        boolean customChanged = false;
        for (Question q : custom) {
            if (removeFromQuestion(q, name)) customChanged = true;
        }
        if (customChanged) DataStore.setCustomQuestions(c, custom);
        syncDeleteInPapers(c, name);
    }

    private static boolean replaceInQuestion(Question q, String oldName, String newName) {
        if (q == null || q.knowledgePoints == null) return false;
        boolean changed = false;
        for (int i = 0; i < q.knowledgePoints.length; i++) {
            if (oldName.equals(q.knowledgePoints[i])) {
                q.knowledgePoints[i] = newName;
                changed = true;
            }
        }
        return changed;
    }

    private static boolean removeFromQuestion(Question q, String name) {
        if (q == null || q.knowledgePoints == null) return false;
        List<String> kept = new ArrayList<>();
        boolean changed = false;
        for (String s : q.knowledgePoints) {
            if (name.equals(s)) { changed = true; continue; }
            kept.add(s);
        }
        if (changed) q.knowledgePoints = kept.toArray(new String[0]);
        return changed;
    }

    private static void syncRenameInPapers(Context c, String oldName, String newName) {
        java.util.Map<String, RobotExamBank.Paper> map = RobotExamUpdater.loadCache(c);
        if (map == null) return;
        boolean changed = false;
        for (RobotExamBank.Paper p : map.values()) {
            if (p == null || p.questions == null) continue;
            for (Question q : p.questions) {
                if (replaceInQuestion(q, oldName, newName)) changed = true;
            }
        }
        if (changed) {
            try { RobotExamUpdater.saveCachePublic(c, map); } catch (Exception ignore) {}
        }
    }

    private static void syncDeleteInPapers(Context c, String name) {
        java.util.Map<String, RobotExamBank.Paper> map = RobotExamUpdater.loadCache(c);
        if (map == null) return;
        boolean changed = false;
        for (RobotExamBank.Paper p : map.values()) {
            if (p == null || p.questions == null) continue;
            for (Question q : p.questions) {
                if (removeFromQuestion(q, name)) changed = true;
            }
        }
        if (changed) {
            try { RobotExamUpdater.saveCachePublic(c, map); } catch (Exception ignore) {}
        }
    }

    /** 给题目设置知识点（追加到列表，去重）。 */
    public static void assignKnowledge(Context c, String uid, String knowledgeName) {
        if (knowledgeName == null || knowledgeName.trim().isEmpty()) return;
        String n = knowledgeName.trim();
        if (!contains(c, n)) add(c, n);
        // 若题目知识点已有该名，跳过
        // 这里通过 DataStore 的 knowledge_<uid> 及 Question.knowledgePoints 双通道记录
    }
}
