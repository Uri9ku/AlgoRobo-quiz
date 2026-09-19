package com.algorobo.quiz;

import java.io.Serializable;

public class Exam implements Serializable {
    public String key;
    public String title;
    public String difficulty;
    public int doneCount;

    public Exam(String key, String title, String difficulty, int doneCount) {
        this.key = key;
        this.title = title;
        this.difficulty = difficulty;
        this.doneCount = doneCount;
    }
}
