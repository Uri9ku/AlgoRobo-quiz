package com.algorobo.quiz;

import java.util.ArrayList;
import java.util.List;

/**
 * 主流 AI API 提供商目录：内置各厂商的官方默认端点与常见模型清单，
 * 用于「按提供商选择模型」的快捷操作，避免用户手动记忆模型名。
 *
 * 所有厂商均提供 OpenAI 兼容或官方 REST 接口，模型列表参考其官网公开信息整理，
 * 以实际官方文档为准，可随时增补。
 */
public class AiProviderCatalog {

    /** 单个提供商的元信息。 */
    public static class Provider {
        public String name;       // 显示名，如 "DeepSeek"
        public String endpoint;   // chat/completions 完整端点
        public String baseUrl;    // 官网/文档地址（供用户查阅模型列表）
        public String[] models;   // 官方模型名清单
        public boolean vision;    // 是否普遍支持识图
        public boolean audio;     // 是否普遍支持音频解析

        Provider(String name, String endpoint, String baseUrl, boolean vision, boolean audio, String... models) {
            this.name = name;
            this.endpoint = endpoint;
            this.baseUrl = baseUrl;
            this.vision = vision;
            this.audio = audio;
            this.models = models;
        }
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    static {
        PROVIDERS.add(new Provider("DeepSeek",
                "https://api.deepseek.com/v1/chat/completions",
                "https://platform.deepseek.com",
                false, false,
                "deepseek-chat", "deepseek-reasoner"));
        PROVIDERS.add(new Provider("OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "https://platform.openai.com/docs/models",
                true, true,
                "gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini",
                "gpt-4-turbo", "gpt-3.5-turbo", "o1", "o3-mini"));
        PROVIDERS.add(new Provider("Operit AI",
                "https://api.openai.com/v1/chat/completions",
                "https://operit.ai",
                true, true,
                "operit-chat", "operit-vision", "operit-reasoner"));
        PROVIDERS.add(new Provider("Anthropic Claude",
                "https://api.anthropic.com/v1/messages",
                "https://docs.anthropic.com/en/docs/about-claude/models",
                true, false,
                "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest",
                "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307"));
        PROVIDERS.add(new Provider("Google Gemini",
                "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                "https://ai.google.dev/gemini-api/docs/models",
                true, true,
                "gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-2.5-pro"));
        PROVIDERS.add(new Provider("智谱 GLM",
                "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                "https://open.bigmodel.cn/dev/api",
                true, false,
                "glm-4-plus", "glm-4-air", "glm-4-flash", "glm-4v", "glm-4-long"));
        PROVIDERS.add(new Provider("通义千问 Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                "https://help.aliyun.com/zh/model-studio/models",
                true, true,
                "qwen-max", "qwen-plus", "qwen-turbo", "qwen-long", "qwen-vl-plus", "qwen-vl-max"));
        PROVIDERS.add(new Provider("Kimi (Moonshot)",
                "https://api.moonshot.cn/v1/chat/completions",
                "https://platform.moonshot.cn/docs",
                true, false,
                "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k", "kimi-k2"));
        PROVIDERS.add(new Provider("百度文心一言",
                "https://qianfan.baidubce.com/v2/chat/completions",
                "https://cloud.baidu.com/doc/WENXINWORKSHOP/s/wlmhmfmv0",
                false, false,
                "ernie-4.0-8k", "ernie-3.5-8k", "ernie-speed-8k", "ernie-lite-8k"));
        PROVIDERS.add(new Provider("讯飞星火",
                "https://spark-api-open.xf-yun.com/v1/chat/completions",
                "https://www.xfyun.cn/doc/spark/HTTP%E8%B0%83%E7%94%A8%E6%96%87%E6%A1%A3.html",
                false, false,
                "generalv3.5", "generalv3", "max-32k", "pro-128k"));
        PROVIDERS.add(new Provider("MiniMax",
                "https://api.minimax.chat/v1/text/chatcompletion_v2",
                "https://www.minimax.io/platform/document/ChatCompletion",
                true, false,
                "abab6.5s-chat", "abab6.5-chat", "abab6.5g-chat"));
        PROVIDERS.add(new Provider("腾讯混元",
                "https://api.hunyuan.cloud.tencent.com/v1/chat/completions",
                "https://cloud.tencent.com/document/product/1729",
                true, false,
                "hunyuan-pro", "hunyuan-standard", "hunyuan-turbo", "hunyuan-lite", "hunyuan-vision"));
        PROVIDERS.add(new Provider("硅基流动 SiliconFlow",
                "https://api.siliconflow.cn/v1/chat/completions",
                "https://siliconflow.cn/zh-cn/models",
                true, true,
                "deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct", "meta-llama/Meta-Llama-3.1-70B-Instruct"));
        PROVIDERS.add(new Provider("自定义",
                "",
                "",
                false, false));
    }

    public static List<Provider> all() {
        return PROVIDERS;
    }

    /** 按名称查找提供商，找不到返回 null。 */
    public static Provider find(String name) {
        if (name == null) return null;
        for (Provider p : PROVIDERS) {
            if (p.name.equals(name)) return p;
        }
        return null;
    }

    /** 提取所有提供商显示名。 */
    public static String[] names() {
        String[] arr = new String[PROVIDERS.size()];
        for (int i = 0; i < PROVIDERS.size(); i++) arr[i] = PROVIDERS.get(i).name;
        return arr;
    }
}
