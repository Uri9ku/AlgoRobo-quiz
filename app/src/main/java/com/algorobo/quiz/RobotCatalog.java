package com.algorobo.quiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 机器人等级考试「备考目录」：
 * - 默认读取 assets/robot_catalog.md（内容与项目根目录《机器人等级考试备考目录.md》一致）；
 * - 可在线刷新：从题库仓库 _meta/robot_catalog.md 拉取最新目录并缓存到 filesDir，
 *   下次（或拉取成功后）按新目录重新分级 —— 目录文件改了，App 的层级就跟着改。
 * - 解析 md 得到「级别 › 分组 › 条目 › 子项」树，再把已导入题目按知识点挂到对应节点上。
 */
public final class RobotCatalog {

    private static final String TAG = "RobotCatalog";
    /** assets / 本地缓存文件名。 */
    private static final String FILE_NAME = "robot_catalog.md";
    /** 在线目录地址（题库仓库 _meta 下，缺文件时静默回落本地）。 */
    private static final String REMOTE_URL =
            "https://raw.githubusercontent.com/Uri9ku/CIE-Robot-Exam/main/_meta/" + FILE_NAME;
    private static final String PREFS = "robot_catalog";
    private static final long REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L; // 6 小时

    private RobotCatalog() {
    }

    /** 知识点树节点。 */
    public static class KpNode {
        public String title;
        public String path;
        public final List<KpNode> children = new ArrayList<>();
        public final Set<String> uids = new HashSet<>();
        KpNode parent;
        List<String> chain;

        public boolean isLeaf() {
            return children.isEmpty();
        }
    }

    // ===================== 目录文本：本地读取 + 在线刷新 =====================

    private static File cacheFile(Context c) {
        return new File(c.getFilesDir(), FILE_NAME);
    }

    /** 优先用在线刷新后的本地缓存，其次用 assets 内置目录。 */
    public static String loadText(Context c) {
        try {
            File f = cacheFile(c);
            if (f.exists() && f.length() > 0) {
                return new String(readAll(new java.io.FileInputStream(f)), "UTF-8");
            }
        } catch (Exception ignore) {
        }
        try {
            InputStream is = c.getAssets().open(FILE_NAME);
            String s = new String(readAll(is), "UTF-8");
            is.close();
            return s;
        } catch (Exception e) {
            Log.w(TAG, "读取内置备考目录失败", e);
            return null;
        }
    }

    /**
     * 后台刷新在线目录（有节流），拉取成功且内容有变化时回传 true。
     * 回调在工作线程；调用方需自行切回主线程刷新界面。
     */
    public static void refreshAsync(final Context c, final Updated cb) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            boolean updated = false;
            try {
                SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                long last = sp.getLong("last_fetch", 0);
                if (System.currentTimeMillis() - last < REFRESH_INTERVAL_MS) return;
                byte[] data = RobotExamUpdater.httpGetBytes(REMOTE_URL);
                sp.edit().putLong("last_fetch", System.currentTimeMillis()).apply();
                if (data == null || data.length == 0) return;
                String text = new String(data, "UTF-8");
                if (text.trim().isEmpty() || !text.contains("标准")) return;
                String old = loadText(app);
                if (old != null && old.equals(text)) return;
                FileOutputStream fos = new FileOutputStream(cacheFile(app));
                fos.write(data);
                fos.close();
                updated = true;
            } catch (Exception e) {
                Log.w(TAG, "刷新备考目录失败（使用本地目录）：" + e.getMessage());
            } finally {
                if (cb != null) cb.onUpdated(updated);
            }
        }).start();
    }

    public interface Updated {
        void onUpdated(boolean changed);
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    // ===================== 解析 md =====================

    /**
     * 解析「## 级别 / ### 分组 / - 条目 / 缩进- 子项」结构。
     * 忽略 "#" 标题、">" 引用、"---" 分隔线以及 "- **科目**" 这类元数据行。
     */
    public static List<KpNode> parseCatalog(String md) {
        List<KpNode> roots = new ArrayList<>();
        if (md == null) return roots;
        KpNode level = null;
        KpNode section = null;
        Map<Integer, KpNode> lastByIndent = new HashMap<>();
        String[] lines = md.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.replace("\r", "");
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("## ")) {
                level = new KpNode();
                level.title = t.substring(3).trim();
                level.path = level.title;
                roots.add(level);
                section = null;
                lastByIndent.clear();
            } else if (t.startsWith("### ")) {
                if (level == null) continue;
                section = new KpNode();
                section.title = t.substring(4).trim();
                section.parent = level;
                section.path = level.path + "/" + section.title;
                level.children.add(section);
                lastByIndent.clear();
            } else if (t.startsWith("- ")) {
                if (t.startsWith("- **")) continue; // 科目/器材等元数据
                if (level == null) continue;
                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') indent++;
                String text = t.substring(2).trim();
                if (text.isEmpty()) continue;
                KpNode parent = section != null ? section : level;
                for (int p = indent - 1; p >= 0; p--) {
                    KpNode cand = lastByIndent.get(p);
                    if (cand != null) {
                        parent = cand;
                        break;
                    }
                }
                KpNode node = new KpNode();
                node.title = text;
                node.parent = parent;
                node.path = parent.path + "/" + text;
                parent.children.add(node);
                lastByIndent.put(indent, node);
                for (int k = indent + 1; k < 16; k++) lastByIndent.remove(k);
            }
        }
        return roots;
    }

    // ===================== 生成带计数的知识点树 =====================

    /**
     * 机器人科目：用备考目录的层级，把已导入题目按知识点挂到目录节点上。
     * 目录里没有的题目标签统一挂到末尾的「其他知识点」。
     */
    public static List<KpNode> buildCatalogTree(Context c, List<Question> qs) {
        List<KpNode> roots = parseCatalog(loadText(c));
        if (roots.isEmpty()) return roots;

        List<KpNode> all = new ArrayList<>();
        collect(roots, all, new ArrayList<>());

        KpNode other = null;
        for (Question q : qs) {
            if (q.knowledgePoints == null) continue;
            for (String kp : q.knowledgePoints) {
                if (kp == null || kp.trim().isEmpty()) continue;
                String[] segs = splitPath(kp);
                if (segs.length == 0) continue;
                KpNode hit = matchNode(all, segs);
                if (hit != null) {
                    addUidUpward(hit, q.uniqueKey());
                } else {
                    if (other == null) {
                        other = new KpNode();
                        other.title = "其他知识点";
                        other.path = "其他知识点";
                        other.parent = null;
                        roots.add(other);
                    }
                    other.uids.add(q.uniqueKey());
                }
            }
        }
        return roots;
    }

    /** 其他科目：直接按题目标签「A / B / C」聚合（同级按题量降序）。 */
    public static List<KpNode> buildTagTree(List<Question> qs) {
        List<KpNode> roots = new ArrayList<>();
        for (Question q : qs) {
            if (q.knowledgePoints == null) continue;
            for (String kp : q.knowledgePoints) {
                if (kp == null) continue;
                String[] segs = splitPath(kp);
                List<KpNode> level = roots;
                StringBuilder path = new StringBuilder();
                for (String seg : segs) {
                    if (path.length() > 0) path.append('/');
                    path.append(seg);
                    KpNode node = null;
                    for (KpNode n : level) {
                        if (n.title.equals(seg)) {
                            node = n;
                            break;
                        }
                    }
                    if (node == null) {
                        node = new KpNode();
                        node.title = seg;
                        node.path = path.toString();
                        node.parent = level.isEmpty() ? null : null;
                        level.add(node);
                    }
                    node.uids.add(q.uniqueKey());
                    level = node.children;
                }
            }
        }
        sortByCount(roots);
        return roots;
    }

    private static void sortByCount(List<KpNode> list) {
        java.util.Collections.sort(list, (a, b) -> b.uids.size() - a.uids.size());
        for (KpNode n : list) sortByCount(n.children);
    }

    private static String[] splitPath(String kp) {
        String[] raw = kp.split(" / ");
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.trim().isEmpty()) out.add(s.trim());
        }
        return out.toArray(new String[0]);
    }

    private static void collect(List<KpNode> list, List<KpNode> out, List<String> parentChain) {
        for (KpNode n : list) {
            List<String> chain = new ArrayList<>(parentChain);
            chain.add(n.title);
            n.chain = chain;
            out.add(n);
            collect(n.children, out, chain);
        }
    }

    /** 匹配：某节点的标题链以题目标签段结尾（如「简单机械原理/杠杆」匹配 一级标准›实践›简单机械原理›杠杆）。 */
    private static KpNode matchNode(List<KpNode> all, String[] segs) {
        List<String> want = Arrays.asList(segs);
        KpNode fallback = null;
        for (KpNode n : all) {
            if (n.chain == null || n.chain.size() < segs.length) continue;
            if (n.chain.subList(n.chain.size() - segs.length, n.chain.size()).equals(want)) {
                if (n.chain.size() == segs.length + 2) return n; // 优先「级别›分组›条目…」的精确位置
                if (fallback == null) fallback = n;
            }
        }
        return fallback;
    }

    private static void addUidUpward(KpNode node, String uid) {
        KpNode n = node;
        while (n != null) {
            n.uids.add(uid);
            n = n.parent;
        }
    }
}
