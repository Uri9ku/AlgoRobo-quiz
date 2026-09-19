package com.algorobo.quiz;

import java.io.Serializable;

public class Question implements Serializable {
    public static final String TYPE_SINGLE = "单选题";
    public static final String TYPE_MULTI = "多选题";
    public static final String TYPE_JUDGE = "判断题";
    public static final String TYPE_PRACTICAL = "实操题";
    public static final String TYPE_SHORT = "简答题";
    public static final String TYPE_ATTACH = "附件题";

    public int id;
    /** 全局唯一标识：来源作用域 + "#" + 试卷内题号。用于错题本/收藏/做对次数等持久化 key，避免不同题库间 id 冲突。 */
    public String uid;
    public String type;
    public String stem;
    public String[] options;
    public int answerIndex;
    public String analysis;

    public String stemImg;
    public String[] optionImgs;
    public String[] answerIndexes;
    public String judgeAnswer;
    public boolean hasAnswer;
    public String difficulty;

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
    }

    public Question() {
        this.hasAnswer = true;
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
