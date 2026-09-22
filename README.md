# AlgoRobo

面向少儿的编程刷题 Android 应用，界面风格参考「粉笔」刷题 App。

## 信息

- 英文名：AlgoRobo
- 包名：`com.algorobo.quiz`
- 仓库名：`algorobo-quiz`
- minSdk 24 / targetSdk 34 / compileSdk 34

## 功能

- 真题练习 / 题库练习 / 随机练习
- 错题本、收藏题、学习统计、打卡日历
- 自定义题库、知识点专题
- AI 知识点识别与 AI 解析（OpenAI 兼容协议 + Anthropic 原生协议）
- 内置 20 道少儿编程题（Scratch 基础 / Python 语法 / 算法思维 / 机器人结构）

## 数据与安全

- 学习数据（题目/错题/收藏/统计/知识点/设置）**仅保存在本机**，卸载应用即会丢失。
  可在「设置 → 更多 → 备份与导出」中导出 JSON 备份，换机后导入恢复。
- 错题重做排序可在「设置 → 错题本 → 错题排序」中选择（默认顺序 / 错误次数多优先 / 最近做错优先）。
- 每日打卡自动判定：当天「首次作答」达到 N 题（N 可在「设置 → 常规 → 打卡判定」调整，范围 10~100）自动记为打卡。
- AI 的 API Key 使用 AndroidKeyStore 硬件级密钥加密后存储，且不随备份导出；API 端点强制要求 `https://`。
- 全局统计只计入每道题的**首次作答**，复刷不会重复计入正确率（复刷会给出提示）。

## 构建

```bash
gradle assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
