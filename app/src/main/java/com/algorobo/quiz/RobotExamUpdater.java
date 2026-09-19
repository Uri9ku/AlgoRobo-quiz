package com.algorobo.quiz;
import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真题缓存存取器：负责真题缓存 JSON 的读写、原始 docx 文件的下载，
 * 以及更改题库下载目录后的文件迁移。
 *
 * 缓存文件：filesDir/robot_questions_cached.json
 * 原始文件下载：由 downloadDocxToPublic 写入用户配置的题库下载目录。
 */
public class RobotExamUpdater {
    private static final String TAG = "RobotExamUpdater";
    // 缓存文件名
    private static final String CACHE_JSON = "robot_questions_cached.json";
    // 网络超时（毫秒）
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;

    /** 从缓存文件加载（若存在）。不存在返回 null。 */
    public static Map<String, RobotExamBank.Paper> loadCache(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), CACHE_JSON);
            if (!f.exists()) return null;
            String json = new String(readAll(new java.io.FileInputStream(f)), "UTF-8");
            java.lang.reflect.Type type =
                    new com.google.gson.reflect.TypeToken<LinkedHashMap<String, RobotExamBank.Paper>>() {}.getType();
            Map<String, RobotExamBank.Paper> map =
                    new com.google.gson.Gson().fromJson(json, type);
            if (map != null) {
                for (String k : map.keySet()) {
                    RobotExamBank.Paper p = map.get(k);
                    if (p == null) continue;
                    if (p.key == null) p.key = k;
                    if (p.questions != null) {
                        for (Question q : p.questions) {
                            if (q.uid == null || q.uid.isEmpty()) q.uid = Question.makeUid(p.key, q.id);
                        }
                    }
                }
            }
            return map;
        } catch (Exception e) {
            Log.w(TAG, "读取缓存失败", e);
            return null;
        }
    }

    /** 持久化结果到缓存 JSON。 */
    private static void saveCache(Context ctx, Map<String, RobotExamBank.Paper> papers) throws Exception {
        File f = new File(ctx.getFilesDir(), CACHE_JSON);
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(new com.google.gson.Gson().toJson(papers).getBytes("UTF-8"));
        fos.close();
        Log.i(TAG, "缓存已写入：" + f.getAbsolutePath());
    }

    /** 公开的缓存持久化入口（供 RobotExamBank 合并缓存时调用）。 */
    public static void saveCachePublic(Context ctx, Map<String, RobotExamBank.Paper> papers) throws Exception {
        saveCache(ctx, papers);
    }

    /**
     * 下载原始 docx 文件到用户配置的题库下载目录（DataStore.getExamDir）。
     * 返回最终保存的本地绝对路径（失败返回 null）。在后台线程调用。
     */
    public static String downloadDocxToPublic(Context ctx, String url, String fileName)
            throws Exception {
        if (url == null || url.isEmpty()) return null;
        byte[] data = httpGetBytes(url);
        if (data == null || data.length == 0) return null;
        String name = (fileName == null || fileName.isEmpty())
                ? "robot_exam_" + System.currentTimeMillis() + ".docx"
                : fileName;
        if (!name.toLowerCase().endsWith(".docx") && !name.toLowerCase().endsWith(".doc")) {
            name = name + ".docx";
        }
        File outDir = new File(DataStore.getExamDir(ctx));
        if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, name);
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(data);
        fos.close();
        Log.i(TAG, "docx 已保存到题库下载目录：" + out.getAbsolutePath());
        return out.getAbsolutePath();
    }

    /**
     * 更改题库下载目录后，迁移旧目录中的已下载题库文件到新目录。
     * 迁移对象：已下载的 docx 文件。返回迁移的文件数。
     */
    public static int migrateDownloads(Context ctx, File oldDir, File newDir) {
        if (oldDir == null || newDir == null) return 0;
        if (oldDir.getAbsolutePath().equals(newDir.getAbsolutePath())) return 0;
        if (!oldDir.exists()) return 0;
        if (!newDir.exists()) newDir.mkdirs();
        int moved = 0;
        File[] files = oldDir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".docx") && !name.endsWith(".doc")) continue;
            File dest = new File(newDir, f.getName());
            if (f.renameTo(dest)) {
                moved++;
            } else {
                try {
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    java.io.FileOutputStream os = new java.io.FileOutputStream(dest);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close(); os.close();
                    f.delete();
                    moved++;
                } catch (Exception e) {
                    Log.w(TAG, "迁移文件失败: " + f.getName(), e);
                }
            }
        }
        if (moved > 0) Log.i(TAG, "已迁移 " + moved + " 个题库文件到 " + newDir.getAbsolutePath());
        return moved;
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    /** 公开的 HTTP GET 字节下载入口（供在线导入真题等功能调用）。 */
    public static byte[] httpGetBytes(String urlStr) throws Exception {
        return doHttpGetBytes(urlStr);
    }

    private static byte[] doHttpGetBytes(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/100.0 Mobile Safari/537.36");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            if (loc != null) {
                return httpGetBytes(absolutize(loc));
            }
            throw new Exception("HTTP " + code);
        }
        if (code != 200) throw new Exception("HTTP " + code);
        InputStream is = new BufferedInputStream(conn.getInputStream());
        byte[] data = readAll(is);
        is.close();
        conn.disconnect();
        return data;
    }

    private static String absolutize(String href) {
        if (href == null || href.isEmpty()) return href;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "http:" + href;
        return "http://www.hunanie.com/" + href;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    // ------------------------------------------------------------------
    // 在线导入真题：GitHub release 抓取与元数据解析
    // ------------------------------------------------------------------

    /** GitHub release 资产描述（文件名、下载 URL、大小、可读标题、期次、科目、等级）。 */
    public static class ReleaseAsset implements java.io.Serializable {
        public String fileName;    // 原始文件名，如 2026.6.CIE.RLE.1.docx
        public String downloadUrl; // 浏览器下载地址
        public long size;          // 字节大小
        public String title;       // 可读标题
        public String period;      // 2026_06
        public String subject;     // robot
        public int level;          // 1
        public String key;         // 缓存 key（用于入库）
    }

    /** 抓取指定 GitHub 仓库的 release 资产列表（含可读标题与元数据）。失败返回空列表。 */
    public static List<ReleaseAsset> fetchGitHubReleases(Context ctx, String owner, String repo)
            throws Exception {
        List<ReleaseAsset> result = new ArrayList<>();
        String api = "https://api.github.com/repos/" + owner + "/" + repo + "/releases";
        byte[] data = httpGetBytes(api);
        if (data == null || data.length == 0) return result;
        String json = new String(data, "UTF-8");
        com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            com.google.gson.JsonObject rel = arr.get(i).getAsJsonObject();
            com.google.gson.JsonArray assets = rel.has("assets")
                    ? rel.get("assets").getAsJsonArray()
                    : new com.google.gson.JsonArray();
            for (int j = 0; j < assets.size(); j++) {
                com.google.gson.JsonObject a = assets.get(j).getAsJsonObject();
                String name = a.has("name") ? a.get("name").getAsString() : null;
                String url = a.has("browser_download_url")
                        ? a.get("browser_download_url").getAsString() : null;
                long size = a.has("size") ? a.get("size").getAsLong() : 0;
                if (name == null) continue;
                if (!name.toLowerCase().endsWith(".docx") && !name.toLowerCase().endsWith(".doc")) continue;
                ReleaseAsset ra = new ReleaseAsset();
                ra.fileName = name;
                ra.downloadUrl = url;
                ra.size = size;
                ra.title = formatFileNameToTitle(name);
                ra.period = parsePeriodFromFileName(name);
                ra.subject = parseSubjectFromFileName(name);
                ra.level = parseLevelFromFileName(name);
                ra.key = makePaperKey(ra);
                result.add(ra);
            }
        }
        return result;
    }

    /** 构造缓存 key：period + "_" + subject + "_" + level。 */
    public static String makePaperKey(ReleaseAsset ra) {
        return (ra.period == null ? "unknown" : ra.period)
                + "_" + (ra.subject == null ? "robot" : ra.subject)
                + "_" + ra.level;
    }

    /**
     * 将 docx 文件名转换为可读标题。
     * 规则（多科目支持）：
     *   2026.6.CIE.RLE.1      -> "2026年06月 CIE 机器人技术等级考试 1级"
     *   2026.6.CIE.Python.1   -> "2026年06月 CIE 软件编程Python 1级"
     *   2026.6.CIE.C.1        -> "2026年06月 CIE 软件编程C语言 1级"
     *   2026.6.CIE.C++.3      -> "2026年06月 CIE 软件编程C++ 3级"
     *   2026.6.CIE.Graphical.1-> "2026年06月 CIE 软件编程图形化 1级"
     */
    public static String formatFileNameToTitle(String fileName) {
        if (fileName == null) return "";
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        // 注意：C++ 中含 "+" 号，不能简单用 "." split 后再还原；故在 split 前先把 "C++" 归一，
        // 但 "." 编号通过正则逐段解析更稳妥，这里采用「逐个 part 判断」方式：
        String[] parts = base.split("\\.");
        if (parts.length == 0) return fileName;

        int year = 0, month = 0, level = 0;
        String sysTag = null;    // "CIE"

        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.matches("\\d{4}")) {
                if (year == 0) year = Integer.parseInt(p);
            } else if (p.matches("\\d{1,2}")) {
                if (month == 0) month = Integer.parseInt(p);
                else if (level == 0) level = Integer.parseInt(p);
            } else if (p.equalsIgnoreCase("cie")) {
                sysTag = "CIE";
            }
        }
        // 等级也可能作为独立数字段出现在末尾；保险起见再从末尾找一次等级。
        if (level == 0) level = parseLevelFromFileName(fileName);

        String subject = parseSubjectFromFileName(fileName);
        String subjectName = subjectDisplayName(subject);

        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(year).append("年");
        if (month > 0) sb.append(String.format("%02d月", month));
        if (sysTag != null) sb.append(" ").append(sysTag);
        sb.append(" ").append(subjectName);
        if (level > 0) sb.append(" ").append(level).append("级");
        return sb.toString().trim();
    }

    /** subject 缩写 -> 中文科目名（含考试类别前缀）。 */
    public static String subjectDisplayName(String subject) {
        if (subject == null) return "真题";
        switch (subject) {
            case "robot": return "机器人技术等级考试";
            case "py":    return "软件编程Python";
            case "c":     return "软件编程C语言";
            case "cpp":   return "软件编程C++";
            case "gx":    return "软件编程图形化";
            default:      return "真题";
        }
    }

    /** 从文件名解析 period：2026.6.CIE.RLE.1 -> 2026_06 */
    public static String parsePeriodFromFileName(String fileName) {
        if (fileName == null) return null;
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String[] parts = base.split("\\.");
        int year = 0, month = 0;
        for (String p : parts) {
            p = p.trim();
            if (p.matches("\\d{4}")) {
                if (year == 0) year = Integer.parseInt(p);
            } else if (p.matches("\\d{1,2}") && month == 0) {
                month = Integer.parseInt(p);
            }
        }
        if (year == 0) return null;
        return String.format("%04d_%02d", year, month);
    }

    /**
     * 从文件名解析 subject 缩写，支持多科目：
     *   RLE       -> "robot"  （机器人技术等级考试）
     *   Python    -> "py"     （软件编程 Python）
     *   C         -> "c"      （软件编程 C语言）
     *   C++       -> "cpp"    （软件编程 C++）
     *   Graphical -> "gx"     （软件编程图形化）
     */
    public static String parseSubjectFromFileName(String fileName) {
        if (fileName == null) return "robot";
        String lower = fileName.toLowerCase();
        // 注意：C++ 含 "+"，须在判断 "c" 之前先判断 "c++"，避免误判为 C。
        if (lower.contains("c++")) return "cpp";
        if (lower.contains("graphical")) return "gx";
        if (lower.contains("python")) return "py";
        if (lower.contains("rle") || lower.contains("robot")) return "robot";
        if (lower.contains(".c.") || lower.contains("c语言")) return "c";
        return "robot";
    }

    /**
     * 从文件名解析等级：末尾的 1~2 位数字段（跳过年份/月份，兼容 "C++" 含加号、无等级后缀等场景）。
     * 例如 2026.6.CIE.RLE.1 -> 1，2026.6.CIE.C++.3 -> 3。
     */
    public static int parseLevelFromFileName(String fileName) {
        if (fileName == null) return 0;
        // 去掉扩展名
        String s = fileName;
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        // 末尾是否为数字段（可能是 "1"、"3" 等）——直接取末尾连着的数字。
        int i = s.length() - 1;
        while (i >= 0 && Character.isDigit(s.charAt(i))) i--;
        if (i < s.length() - 1) {
            String num = s.substring(i + 1);
            try {
                return Integer.parseInt(num);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 在线导入流程核心：下载 docx → 解析为 Paper → 合并写入缓存。
     * mediaDir 用于存放提取出的图片（应传 filesDir，使媒体路径与 mediaPath 一致）。
     * 返回解析后的 Paper；失败抛出异常。
     */
    public static RobotExamBank.Paper importPaperFromDocx(
            Context ctx, ReleaseAsset ra, File mediaDir) throws Exception {
        if (ra.downloadUrl == null || ra.downloadUrl.isEmpty()) {
            throw new Exception("资产无下载地址");
        }
        byte[] docx = httpGetBytes(ra.downloadUrl);
        if (docx == null || docx.length == 0) throw new Exception("下载 docx 失败");
        RobotExamBank.Paper paper = DocxParser.parse(docx, ra.key, mediaDir);
        if (paper == null) throw new Exception("解析 docx 失败");
        paper.title = ra.title;
        paper.period = ra.period;
        paper.subject = ra.subject;
        paper.level = ra.level;
        paper.sourceUrl = ra.downloadUrl;
        paper.sourceName = ra.fileName;
        return paper;
    }
}
