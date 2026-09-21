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

    // ===== 在线导入：GitHub 真题来源仓库（单一数据源） =====
    public static final String GITHUB_OWNER = "Uri9ku";
    public static final String[] GITHUB_REPOS = {
        "CIE-Graphical-Exam", // 软件编程图形化
        "CIE-Python-Exam",    // 软件编程 Python
        "CIE-C-Exam",         // 软件编程 C 语言
        "CIE-Robot-Exam"      // 机器人技术等级考试
    };
    /** 仓库名 -> 展示用中文名。 */
    public static String repoDisplayName(String repo) {
        if (repo == null) return "真题";
        if (repo.contains("Graphical")) return "图形化";
        if (repo.contains("Python")) return "Python";
        if (repo.contains("C-Exam")) return "C";
        if (repo.contains("Robot")) return "机器人";
        return repo;
    }
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
                        for (int i = 0; i < p.questions.size(); i++) {
                            Question q = p.questions.get(i);
                            // uid 缺失、或为历史缺陷值（同卷所有题共用 "...key#0"）时，
                            // 统一按卷内题号(1 起)重建，保证每题唯一。
                            if (q.uid == null || q.uid.isEmpty() || q.uid.endsWith("#0")) {
                                q.uid = Question.makeUid(p.key, i + 1);
                            }
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
        // 文件已变更：同步失效内存快照，避免后续读到旧数据（导入后点击新真题提示「暂无题目」）
        RobotExamBank.invalidateCache();
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
        return saveDocxToExamDir(ctx, fileName, data);
    }

    /** 公开包装：供下载流程复用同一套网络抓取逻辑。 */
    public static byte[] httpGetBytesPublic(String url) throws Exception {
        return httpGetBytes(url);
    }

    /** 将 docx 字节写入当前下载目录（默认系统 Download，可在设置更改），返回绝对路径。 */
    public static String saveDocxToExamDir(Context ctx, String fileName, byte[] data)
            throws Exception {
        if (data == null || data.length == 0) return null;
        String name = (fileName == null || fileName.isEmpty())
                ? "robot_exam_" + System.currentTimeMillis() + ".docx"
                : fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
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

    /** 下载进度回调（百分比 0~100）。 */
    public interface DownloadProgress {
        void onPercent(int percent);
    }

    /** 公开的 HTTP GET 字节下载入口（供在线导入真题等功能调用）。 */
    public static byte[] httpGetBytes(String urlStr) throws Exception {
        return doHttpGetBytes(urlStr, null);
    }

    /** 带进度的 HTTP GET 下载入口。 */
    public static byte[] httpGetBytes(String urlStr, DownloadProgress cb) throws Exception {
        return doHttpGetBytes(urlStr, cb);
    }

    private static byte[] doHttpGetBytes(String urlStr) throws Exception {
        return doHttpGetBytes(urlStr, null);
    }

    private static byte[] doHttpGetBytes(String urlStr, DownloadProgress cb) throws Exception {
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
                return httpGetBytes(absolutize(loc), cb);
            }
            throw new Exception("HTTP " + code);
        }
        if (code != 200) throw new Exception("HTTP " + code);
        int total = conn.getContentLength();
        InputStream is = new BufferedInputStream(conn.getInputStream());
        byte[] data = readAll(is, total, cb);
        is.close();
        conn.disconnect();
        if (cb != null) cb.onPercent(100);
        return data;
    }

    private static String absolutize(String href) {
        if (href == null || href.isEmpty()) return href;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "http:" + href;
        return "http://www.hunanie.com/" + href;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        return readAll(is, -1, null);
    }

    /** 读取全部字节；total>0 且 cb 非空时按百分比回报下载进度（同值不重复回调）。 */
    private static byte[] readAll(InputStream is, int total, DownloadProgress cb) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long read = 0;
        int last = -1;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
            read += n;
            if (cb != null && total > 0) {
                int pct = (int) (read * 100 / total);
                if (pct > 100) pct = 100;
                if (pct != last) {
                    last = pct;
                    cb.onPercent(pct);
                }
            }
        }
        return baos.toByteArray();
    }

    // ------------------------------------------------------------------
    // 在线导入真题：GitHub release 抓取与元数据解析
    // ------------------------------------------------------------------

    /** GitHub release 资产描述（文件名、下载 URL、大小、可读标题、期次、科目、等级）。 */
    public static class ReleaseAsset implements java.io.Serializable {
        public String fileName;    // 原始文件名，如 2026.6.CIE.RLE.1.docx
        public String downloadUrl; // 浏览器下载地址
        public String repo;        // 来源仓库名，如 CIE-Robot-Exam
        public long size;          // 字节大小
        public String title;       // 可读标题
        public String period;      // 2026_06
        public String subject;     // robot
        public int level;          // 1
        public String key;         // 缓存 key（用于入库）
    }

    /** 抓取指定 GitHub 仓库中的真题文件列表（含可读标题与元数据）。失败返回空列表。 */
    public static List<ReleaseAsset> fetchGitHubReleases(Context ctx, String owner, String repo)
            throws Exception {
        List<ReleaseAsset> result = new ArrayList<>();
        // 优先使用 Git Trees API 递归列出仓库所有 .docx/.doc 文件（真题直接提交在仓库文件系统里，而非 release 资产）。
        String treeApi = "https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/main?recursive=1";
        byte[] data;
        try {
            data = httpGetBytes(treeApi);
        } catch (Exception e) {
            Log.w(TAG, "Git Trees API 获取失败，回退 release 接口：" + e.getMessage());
            data = null;
        }
        if (data != null && data.length > 0) {
            String json = new String(data, "UTF-8");
            try {
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                if (root.has("tree") && root.get("tree").isJsonArray()) {
                    com.google.gson.JsonArray tree = root.get("tree").getAsJsonArray();
                    for (int i = 0; i < tree.size(); i++) {
                        com.google.gson.JsonObject entry = tree.get(i).getAsJsonObject();
                        String path = entry.has("path") ? entry.get("path").getAsString() : null;
                        String type = entry.has("type") ? entry.get("type").getAsString() : null;
                        long size = entry.has("size") ? entry.get("size").getAsLong() : 0;
                        if (path == null || !"blob".equals(type)) continue;
                        String lower = path.toLowerCase();
                        if (!lower.endsWith(".docx") && !lower.endsWith(".doc")) continue;
                        String name = path;
                        int slash = name.lastIndexOf('/');
                        if (slash >= 0) name = name.substring(slash + 1);
                        ReleaseAsset ra = new ReleaseAsset();
                        ra.fileName = name;
                        ra.repo = repo;
                        // 构造 raw 下载地址（路径需 URL 编码，保留 '/' 分隔）
                        ra.downloadUrl = buildRawDownloadUrl(owner, repo, "main", path);
                        ra.size = size;
                        ra.title = formatFileNameToTitle(name);
                        ra.period = parsePeriodFromFileName(name);
                        ra.subject = parseSubjectFromFileName(name);
                        ra.level = parseLevelFromFileName(name);
                        ra.key = makePaperKey(ra);
                        result.add(ra);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "解析 Git Trees 响应失败：" + e.getMessage());
            }
            if (!result.isEmpty()) return result;
        }

        // 回退：仓库无 git tree（如空仓库或仅 release），尝试 release 资产接口。
        String api = "https://api.github.com/repos/" + owner + "/" + repo + "/releases";
        byte[] relData = httpGetBytes(api);
        if (relData == null || relData.length == 0) return result;
        String relJson = new String(relData, "UTF-8");
        com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(relJson).getAsJsonArray();
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
                ra.repo = repo;
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

    /** 构造 raw.githubusercontent.com 下载地址（对路径各段做 URL 编码，保留 '/' 分隔）。 */
    private static String buildRawDownloadUrl(String owner, String repo, String branch, String path) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String seg : path.split("/")) {
                if (sb.length() > 0) sb.append('/');
                sb.append(java.net.URLEncoder.encode(seg, "UTF-8")
                        .replace("+", "%20"));
            }
            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + sb.toString();
        } catch (Exception e) {
            return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + path;
        }
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
        int year = 0, month = 0, level = 0;
        // 中文年月解析
        java.util.regex.Matcher cm = java.util.regex.Pattern
                .compile("(\\d{4})年(\\d{1,2})月").matcher(base);
        if (cm.find()) {
            year = Integer.parseInt(cm.group(1));
            month = Integer.parseInt(cm.group(2));
        }
        if (level == 0) level = parseLevelFromFileName(fileName);
        String subject = parseSubjectFromFileName(fileName);
        String subjectName = subjectShortName(subject);
        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(year % 100).append("年");
        if (month > 0) sb.append(String.format("%02d月", month));
        sb.append("CIE").append(subjectName);
        if (level > 0) sb.append(level).append("级");
        return sb.toString().trim();
    }
    /** subject 缩写 -> 简洁科目名。 */
    public static String subjectShortName(String subject) {
        if (subject == null) return "真题";
        switch (subject) {
            case "robot": return "机器人";
            case "py":    return "Python";
            case "c":     return "C";
            case "cpp":   return "C++";
            case "gx":    return "图形化";
            default:      return "真题";
        }
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

    /** 从文件名解析 period：2026.6.CIE.RLE.1 -> 2026_06；2026年6月... -> 2026_06 */
    public static String parsePeriodFromFileName(String fileName) {
        if (fileName == null) return null;
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        // ① 中文格式：YYYY年M月...
        java.util.regex.Matcher cm = java.util.regex.Pattern
                .compile("(\\d{4})年(\\d{1,2})月").matcher(base);
        if (cm.find()) {
            int y = Integer.parseInt(cm.group(1));
            int m = Integer.parseInt(cm.group(2));
            return String.format("%04d_%02d", y, m);
        }

        // ② 英文格式：YYYY.M.CIE... 
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
        if (lower.contains("c++") || lower.contains("c＋＋")) return "cpp";
        if (lower.contains("graphical") || lower.contains("图形化")) return "gx";
        if (lower.contains("python") || lower.contains("python编程")) return "py";
        if (lower.contains("rle") || lower.contains("robot")
                || lower.contains("机器人技术")) return "robot";
        if (lower.contains("c语言") || lower.contains("c语言编程")) return "c";
        // C 语言：按 "." 切段后精确匹配，避免 ".c." 子串对非标准命名的脆弱依赖。
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String[] parts = base.split("\\.");
        for (String p : parts) {
            if (p.trim().equalsIgnoreCase("c")) return "c";
        }
        return "robot";
    }

    /**
     * 从文件名解析等级：末尾的 1~2 位数字段（跳过年份/月份，兼容 "C++" 含加号、无等级后缀等场景）。
     * 例如 2026.6.CIE.RLE.1 -> 1，2026.6.CIE.C++.3 -> 3。
     */
    public static int parseLevelFromFileName(String fileName) {
        if (fileName == null) return 0;
        String s = fileName;
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        // 支持两种结尾："...N" 或 "...N级"（N为连续数字）。
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)(?:级)?$").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception e) { return 0; }
        }
        return 0;
    }

    /**
     * 由仓库原始文件名生成可读的中文文件名：
     * 2026.6.CIE.RLE.3.docx → 2026年6月CIE软件编程图形化3级（无法解析时返回 null，调用方回退原名）。
     */
    public static String buildChineseDocxName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return null;
        String base = sourceName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String[] seg = base.split("\\.");
        if (seg.length < 2) return null;
        int year = 0, month = 0;
        try { year = Integer.parseInt(seg[0]); } catch (Exception ignore) { }
        try { month = Integer.parseInt(seg[1]); } catch (Exception ignore) { }
        String subject = parseSubjectFromFileName(sourceName);
        int level = parseLevelFromFileName(sourceName);
        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(year).append("年");
        if (month > 0) sb.append(month).append("月");
        sb.append("CIE软件编程");
        if (subject == null) subject = "robot";
        switch (subject) {
            case "py": sb.append("Python"); break;
            case "c": sb.append("C语言"); break;
            case "cpp": sb.append("C++"); break;
            case "gx": sb.append("图形化"); break;
            case "robot": sb.append("机器人"); break;
            default: sb.append(subject); break;
        }
        if (level > 0) sb.append(level).append("级");
        return sb.toString();
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
