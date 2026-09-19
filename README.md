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

## 构建

```bash
gradle assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
