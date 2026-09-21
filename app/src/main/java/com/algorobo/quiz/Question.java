package com.algorobo.quiz;

import java.io.Serializable;

public class Question implements Serializable {
    public static final String TYPE_SINGLE = "单选题";
    public static final String TYPE_MULTI = "多选题";
    public static final String TYPE_JUDGE = "判断题";
    public static final String TYPE_PRACTICAL = "实操题";
    public static final String TYPE_SHORT = "简答题";
    public static final String TYPE_ATTACH = "附件题";

    /**
     * 题干内容块：按 docx 中文字/图片出现的先后顺序保存，用于「文字 + 多图」混排展示。
     * 兼容旧缓存：stemBlocks 为空时回退到 stem + stemImg 的单图排版。
     */
    public static class StemBlock implements Serializable {
        public static final int KIND_TEXT = 0;
        public static final int KIND_IMAGE = 1;
        public int kind;
        /** kind=KIND_TEXT 时的文字内容。 */
        public String text;
        /** kind=KIND_IMAGE 时的图片文件名（已由 rId 解析为本地文件名）。 */
        public String image;

        public StemBlock() {
        }

        public StemBlock(int kind, String value) {
            this.kind = kind;
            if (kind == KIND_TEXT) this.text = value;
            else this.image = value;
        }
    }

    public int id;
    /** 全局唯一标识：来源作用域 + "#" + 试卷内题号。用于错题本/收藏/做对次数等持久化 key，避免不同题库间 id 冲突。 */
    public String uid;
    public String type;
    /** docx 中的原始题型名称（如「编程题」），仅用于展示；作答逻辑仍以 {@link #type} 为准。 */
    public String typeLabel;
    public String stem;
    /** 题干混排块（文字/图片按原顺序），为空表示旧数据（使用 stem + stemImg）。 */
    public java.util.List<StemBlock> stemBlocks;
    public String[] options;
    public int answerIndex;
    public String analysis;

    public String stemImg;
    public String[] optionImgs;
    public String[] answerIndexes;
    public String judgeAnswer;
    public boolean hasAnswer;
    public String difficulty;
    /** 该题关联的知识点名称列表（可空，多知识点用数组表达）。 */
    public String[] knowledgePoints;
    /** 该题分值（0 表示未解析到分值）。 */
    public int score;

    public Question(int id, String type, String stem, String[] options, int answerIndex, String analysis) {
        this.id = id;
        this.type = type;
        this.stem = stem;
        this.options = options;
        this.answerIndex = answerIndex;
        this.analysis = analysis;
        this.stemImg = null;
        this.optionImgs = null;
        this.answerIndexes = null;
        this.judgeAnswer = null;
        this.hasAnswer = true;
        this.difficulty = "";
        this.score = 0;
    }

    public Question() {
        this.hasAnswer = true;
        this.score = 0;
    }

    public boolean isJudgeable() {
        if (TYPE_JUDGE.equals(type)) return true;
        if (TYPE_SINGLE.equals(type) || TYPE_MULTI.equals(type)) return hasAnswer;
        return false;
    }
    public boolean isSingle() { return TYPE_SINGLE.equals(type); }
    public boolean isMulti() { return TYPE_MULTI.equals(type); }
    public boolean isJudge() { return TYPE_JUDGE.equals(type); }
    public boolean isPracticalOnly() {
        return TYPE_PRACTICAL.equals(type) || TYPE_SHORT.equals(type) || TYPE_ATTACH.equals(type) || "".equals(type);
    }
    public boolean hasOptionImages() {
        if (optionImgs == null) return false;
        for (String s : optionImgs) if (s != null && !s.isEmpty()) return true;
return false;
    }
    /** 构造全局唯一标识：作用域 + "#" + 题号。 */
    public static String makeUid(String scope, int id) {
        return (scope == null ? "" : scope) + "#" + id;
    }
    /** 返回该题用于持久化的唯一 key。uid 为空时（历史数据）回退为 "legacy#<id>"，保证不因 id 冲突跨题库串号。 */
    public String uniqueKey() {
        return (uid != null && !uid.isEmpty()) ? uid : ("legacy#" + id);
    }
}
