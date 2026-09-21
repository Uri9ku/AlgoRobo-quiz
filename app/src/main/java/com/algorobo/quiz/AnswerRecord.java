package com.algorobo.quiz;
import java.io.Serializable;

public class AnswerRecord implements Serializable {
    public int id;
    public String type;
    public String stem;
    public String[] options;
    public int answerIndex;
    public String analysis;
    public int userAnswer;

    public String stemImg;
    public String[] optionImgs;
    public String[] answerIndexes;
    public String judgeAnswer;
    public boolean hasAnswer;
    public String difficulty;
    public int[] userAnswerIndexes;
    /** 题目唯一键：用于复用 AI 解析缓存（与刷题页一致）。 */
    public String uid;
    /** 知识点：全部解析页复用刷题页的知识点标签。 */
    public String[] knowledgePoints;

    public AnswerRecord(Question q, int userAnswer) {
        this.id = q.id;
        this.type = q.type;
        this.stem = q.stem;
        this.options = q.options;
        this.answerIndex = q.answerIndex;
        this.analysis = q.analysis;
        this.userAnswer = userAnswer;
        this.stemImg = q.stemImg;
        this.optionImgs = q.optionImgs;
        this.answerIndexes = q.answerIndexes;
        this.judgeAnswer = q.judgeAnswer;
        this.hasAnswer = q.hasAnswer;
        this.difficulty = q.difficulty;
        this.uid = q.uniqueKey();
        this.knowledgePoints = q.knowledgePoints;
    }

    public boolean isAnswered() {
        return userAnswer >= 0;
    }

    public boolean isCorrect() {
        if (!hasAnswer) return true;
        if (Question.TYPE_JUDGE.equals(type)) {
            return userAnswer >= 0 && String.valueOf(userAnswer).equals(judgeAnswer);
        }
        return userAnswer == answerIndex;
    }
}
