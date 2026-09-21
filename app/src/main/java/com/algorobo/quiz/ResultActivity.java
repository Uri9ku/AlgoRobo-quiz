package com.algorobo.quiz;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {

    private int totalOriginal;
    private int correctOriginal;
    private long durationSec;
    private List<AnswerRecord> records = new ArrayList<>();

    private TextView tvScore, tvCorrect, tvWrong, tvAccuracy, tvDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        totalOriginal = getIntent().getIntExtra("total", 0);
        correctOriginal = getIntent().getIntExtra("correct", 0);
        durationSec = getIntent().getLongExtra("duration_sec", 0);
        java.io.Serializable data = getIntent().getSerializableExtra("records");
        if (data instanceof List) {
            for (Object o : (List<?>) data) {
                if (o instanceof AnswerRecord) records.add((AnswerRecord) o);
            }
        }

        tvScore = findViewById(R.id.tvScore);
        tvCorrect = findViewById(R.id.tvCorrect);
        tvWrong = findViewById(R.id.tvWrong);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        tvDuration = findViewById(R.id.tvDuration);
        tvDuration.setText(formatDuration(durationSec));

        CheckBox cbHidePractical = findViewById(R.id.cbHidePractical);
        cbHidePractical.setOnCheckedChangeListener((btn, checked) -> render(checked));
        render(false);

        findViewById(R.id.btnResultHome).setOnClickListener(v -> finish());
        findViewById(R.id.btnResultAgain).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, AllAnalysisActivity.class);
            intent.putExtra("records", (java.io.Serializable) new ArrayList<>(records));
            startActivity(intent);
        });
    }

    /**
     * 渲染统计。hidePractical=true 时剔除主观题（实操题/编程题/搭建题/简答题/附件题）后重新统计。
     */
    private void render(boolean hidePractical) {
        int total = totalOriginal;
        int correct = correctOriginal;
        if (hidePractical) {
            total = 0;
            correct = 0;
            for (AnswerRecord r : records) {
                if (r == null || isSubjective(r.type)) continue;
                total++;
                if (r.isCorrect()) correct++;
            }
        }
        int wrong = total - correct;
        int score = total > 0 ? (int) Math.round(correct * 100.0 / total) : 0;
        tvScore.setText(String.valueOf(score));
        tvCorrect.setText("答对 " + correct);
        tvWrong.setText("答错 " + wrong);
        tvAccuracy.setText(score + "%");
    }

    /** 主观题判定：实操题/简答题/附件题（含解析得到的“编程题/搭建题”等均归入实操题）。 */
    private boolean isSubjective(String type) {
        if (type == null) return false;
        return Question.TYPE_PRACTICAL.equals(type)
                || Question.TYPE_SHORT.equals(type)
                || Question.TYPE_ATTACH.equals(type);
    }

    private String formatDuration(long sec) {
        long m = sec / 60, s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }
}
