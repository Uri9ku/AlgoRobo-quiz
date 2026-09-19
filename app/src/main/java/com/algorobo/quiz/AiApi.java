package com.algorobo.quiz;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * AI 能力封装：基于 OpenAI 兼容协议（同时兼容 DeepSeek 等 OpenAI 兼容服务）。
 * 采用系统原生 HttpURLConnection，不引入额外网络依赖，适配离线构建。
 *
 * 协议说明：
 *   Endpoint 形如 https://api.deepseek.com/v1/chat/completions 或
 *   https://api.openai.com/v1/chat/completions。
 *   返回格式为 OpenAI ChatCompletion：
 *   { "choices": [ { "message": { "content": "..." } } ] }
 */
public class AiApi {
    private static final String TAG = "AiApi";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;

    /** 单个模型配置（Provider + Endpoint + Key + 模型名列表）。 */
    public static class Config implements java.io.Serializable {
        public String name;      // 配置名称，如 "默认配置"
        public String provider;  // 提供商，如 Deepseek / OpenAI / 自定义
        public String endpoint;  // API 端点（chat/completions 完整地址）
        public String apiKey;    // API 密钥
        public String models;    // 模型名称，逗号分隔多个

        public boolean vision;  // 模型是否支持识图
        public boolean audio;   // 模型是否支持音频解析
        public Config() {
        }

        public Config(String name, String provider, String endpoint, String apiKey, String models) {
            this.name = name;
            this.provider = provider;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.models = models;
        }

        public Config(String name, String provider, String endpoint, String apiKey, String models, boolean vision, boolean audio) {
            this.name = name;
            this.provider = provider;
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.models = models;
            this.vision = vision;
            this.audio = audio;
        }
        /** 返回第一个模型名（用于默认调用）。 */
        public String firstModel() {
            if (models == null || models.trim().isEmpty()) return "";
            String[] parts = models.split(",");
            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty()) return p;
            }
            return "";
        }

        /** 是否已填写完调用所需的最小字段。 */
        public boolean isComplete() {
            return nonEmpty(endpoint) && nonEmpty(apiKey) && !firstModel().isEmpty();
        }

        private static boolean nonEmpty(String s) {
            return s != null && !s.trim().isEmpty();
        }
    }

    /** 调用结果。 */
    public static class Result {
        public boolean ok;
        public String text;    // 成功时为模型返回内容，失败时为错误描述
        public int statusCode;

        public Result(boolean ok, String text, int statusCode) {
            this.ok = ok;
            this.text = text;
            this.statusCode = statusCode;
        }
    }

    /**
     * 测试连接：向配置的 endpoint 发送一条极简 chat 请求，验证 Key/端点/模型是否可用。
     * 需在后台线程调用，避免阻塞主线程。
     */
    public static Result testConnection(Config cfg) {
        if (cfg == null || !cfg.isComplete()) {
            return new Result(false, "请先完善 API 提供商、端点、密钥与模型名称", 0);
        }
        String endpoint = normalizeEndpoint(cfg.endpoint);
        return chat(cfg, endpoint, "ping", "这条消息用于测试连接，请回复“ok”。", 0.0f);
    }

    /**
     * 识别知识点：根据题目内容调用 AI，返回识别出的知识点（或解析说明）。
     * 需在后台线程调用。
     */
    public static Result recognizeKnowledge(Context ctx, Config cfg, Question q) {
        if (cfg == null || !cfg.isComplete()) {
            return new Result(false, "请先在「AI 模型配置」中完善配置", 0);
        }
        String endpoint = normalizeEndpoint(cfg.endpoint);
        String prompt = buildKnowledgePrompt(q);
        return chat(cfg, endpoint, "knowledge", prompt, 0.2f);
    }

    /** 构造知识点识别的 prompt。 */
    private static String buildKnowledgePrompt(Question q) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名少儿编程辅导老师。请针对下面这道编程题，用孩子能听懂的语言，简要(200字以内)输出：");
        sb.append("1) 该题考查的核心知识点；2) 解题要点或易错点。\n\n");
        sb.append("题型：").append(q == null ? "" : q.type).append("\n");
        sb.append("题目：").append(q == null ? "" : q.stem).append("\n");
        if (q != null && q.options != null && q.options.length > 0) {
            sb.append("选项：\n");
            char label = 'A';
            for (String o : q.options) {
                sb.append(label++).append(". ").append(o == null ? "" : o).append("\n");
            }
        }
        if (q != null && q.analysis != null && !q.analysis.isEmpty()) {
            sb.append("参考答案解析：").append(q.analysis).append("\n");
        }
        return sb.toString();
    }

    /**
     * AI 解析：根据题目内容 + 用户答案 + 参考答案解析，生成详细解析文字。
     * 需在后台线程调用。
     */
    public static Result analyzeQuestion(Context ctx, Config cfg, Question q, String userAnswerText, boolean isCorrect) {
        if (cfg == null || !cfg.isComplete()) {
            return new Result(false, "请先在「AI 模型配置」中完善配置", 0);
        }
        String endpoint = normalizeEndpoint(cfg.endpoint);
        String prompt = buildAnalysisPrompt(q, userAnswerText, isCorrect);
        return chat(cfg, endpoint, "analysis", prompt, 0.3f);
    }
    /** 构造 AI 解析的 prompt：题目 + 正确答案 + 用户答案 + 参考答案解析。 */
    private static String buildAnalysisPrompt(Question q, String userAnswerText, boolean isCorrect) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名少儿编程辅导老师。请针对下面这道编程题给出详细解析，");
        sb.append("要求语言通俗易懂、条理清晰，帮助小朋友理解为什么选这个答案、错在哪里。\n\n");
        sb.append("题型：").append(q == null ? "" : q.type).append("\n");
        sb.append("题目：").append(q == null ? "" : q.stem).append("\n");
        if (q != null && q.options != null && q.options.length > 0) {
            sb.append("选项：\n");
            char label = 'A';
            for (String o : q.options) {
                sb.append(label++).append(". ").append(o == null ? "" : o).append("\n");
            }
        }
        if (q != null) {
            sb.append("正确答案：").append(formatAnswerForPrompt(q)).append("\n");
        }
        sb.append("用户作答：").append(userAnswerText == null || userAnswerText.isEmpty() ? "未作答" : userAnswerText)
          .append("（").append(isCorrect ? "回答正确" : "回答错误").append("）\n");
        if (q != null && q.analysis != null && !q.analysis.isEmpty()) {
            sb.append("参考解析（请结合它展开）：").append(q.analysis).append("\n");
        }
        sb.append("请输出一段完整的题目解析。");
        return sb.toString();
    }
    /** 将题目的正确答案格式化为可读文本（用于 prompt）。 */
    private static String formatAnswerForPrompt(Question q) {
        if (q == null) return "";
        if (!q.hasAnswer) return "（无标准答案）";
        if (q.isJudge()) return q.judgeAnswer == null ? "" : q.judgeAnswer;
        if (q.isMulti()) {
            if (q.answerIndexes == null) return "";
            StringBuilder sb = new StringBuilder();
            for (String s : q.answerIndexes) {
                int idx = 0;
                try { idx = Integer.parseInt(s.trim()); } catch (Exception e) {}
                if (sb.length() > 0) sb.append("、");
                sb.append((char) ('A' + idx));
            }
            return sb.toString();
        }
        return String.valueOf((char) ('A' + q.answerIndex));
    }

    /** 统一 chat 调用。systemRole 用于区分用途；temperature 控制随机性。 */
    private static Result chat(Config cfg, String endpoint, String systemRole, String userContent, float temperature) {
        boolean anthropic = endpoint != null && endpoint.contains("anthropic");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            byte[] payload;
            if (anthropic) {
                // Anthropic 原生协议：x-api-key + /v1/messages + content 数组
                conn.setRequestProperty("x-api-key", cfg.apiKey);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                JsonObject body = new JsonObject();
                body.addProperty("model", cfg.firstModel());
                body.addProperty("max_tokens", 1024);
                if (temperature > 0) body.addProperty("temperature", temperature);
                JsonArray messages = new JsonArray();
                JsonObject user = new JsonObject();
                user.addProperty("role", "user");
                user.addProperty("content", systemRole + "\n\n" + userContent);
                messages.add(user);
                body.add("messages", messages);
                payload = body.toString().getBytes("UTF-8");
            } else {
                // OpenAI 兼容协议：Authorization Bearer + choices
                conn.setRequestProperty("Authorization", "Bearer " + cfg.apiKey);
                JsonObject body = new JsonObject();
                body.addProperty("model", cfg.firstModel());
                body.addProperty("temperature", temperature);
                JsonArray messages = new JsonArray();
                JsonObject sys = new JsonObject();
                sys.addProperty("role", "system");
                sys.addProperty("content", systemRole);
                JsonObject user = new JsonObject();
                user.addProperty("role", "user");
                user.addProperty("content", userContent);
                messages.add(sys);
                messages.add(user);
                body.add("messages", messages);
                payload = body.toString().getBytes("UTF-8");
            }
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String resp = readStream(is);
            conn.disconnect();
            if (code >= 200 && code < 300) {
                String content = anthropic ? parseAnthropicContent(resp) : parseContent(resp);
                if (content == null || content.isEmpty()) {
                    return new Result(false, "响应中未找到内容：" + truncate(resp), code);
                }
                return new Result(true, content, code);
            } else {
                String err = anthropic ? parseAnthropicError(resp) : parseError(resp);
                return new Result(false, "HTTP " + code + (err == null ? "" : "：" + err), code);
            }
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, "请求超时，请检查网络或端点地址", 0);
        } catch (Exception e) {
            Log.w(TAG, "chat 调用失败", e);
            return new Result(false, "请求失败：" + e.getMessage(), 0);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
    /** 从 Anthropic 响应中提取 content[0].text。 */
    private static String parseAnthropicContent(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("content")) {
                JsonArray content = root.getAsJsonArray("content");
                if (content.size() > 0) {
                    JsonObject first = content.get(0).getAsJsonObject();
                    if (first.has("text")) {
                        return first.get("text").getAsString();
                    }
                    if (first.has("content") && first.get("content").isJsonPrimitive()) {
                        return first.get("content").getAsString();
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }
    /** 从 Anthropic 错误响应中提取 error 描述。 */
    private static String parseAnthropicError(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("error")) {
                JsonObject err = root.getAsJsonObject("error");
                if (err != null && err.has("message")) {
                    return err.get("message").getAsString();
                }
                return err != null ? err.toString() : null;
            }
        } catch (Exception ignore) {
        }
        return truncate(json);
    }
    /** 从 OpenAI 兼容响应中提取 message.content。 */
    private static String parseContent(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("choices")) {
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject first = choices.get(0).getAsJsonObject();
                    if (first.has("message")) {
                        JsonObject msg = first.getAsJsonObject("message");
                        if (msg.has("content")) {
                            return msg.get("content").getAsString();
                        }
                    }
                }
            }
            // 某些服务直接返回 { "content": "..." }
            if (root.has("content")) {
                return root.get("content").getAsString();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    /** 从错误响应中提取 error.message。 */
    private static String parseError(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("error")) {
                JsonObject err = root.getAsJsonObject("error");
                if (err != null && err.has("message")) {
                    return err.get("message").getAsString();
                }
                return err != null ? err.toString() : null;
            }
        } catch (Exception ignore) {
        }
        return truncate(json);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= 200) return s;
        return s.substring(0, 200) + "…";
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        br.close();
        return sb.toString();
    }

    /**
     * 规范化端点：若用户只填了 base url（如 https://api.deepseek.com 或
     * https://api.deepseek.com/v1），自动补全 /chat/completions 后缀。
     */
    public static String normalizeEndpoint(String endpoint) {
        if (endpoint == null) return "";
        String s = endpoint.trim();
        if (s.isEmpty()) return s;
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/chat/completions") || s.endsWith("/completions")) {
            return s;
        }
        if (s.endsWith("/v1")) {
            return s + "/chat/completions";
        }
        return s + "/v1/chat/completions";
    }
}
