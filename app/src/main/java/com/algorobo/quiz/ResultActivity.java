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
    /** 是否隐藏实操题（按钮切换）。 */
    private boolean hidePractical = false;

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

        // 隐藏/显示实操题：按钮式，文本区分状态（点击后重算统计并播放数字动画）
        final TextView btnHide = findViewById(R.id.btnResultHidePractical);
        btnHide.setOnClickListener(v -> {
            hidePractical = !hidePractical;
            btnHide.setText(hidePractical ? "显示实操题" : "隐藏实操题");
            render(hidePractical);
        });
        render(false);
        findViewById(R.id.btnResultHome).setOnClickListener(v -> finish());
        findViewById(R.id.btnResultAgain).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, AllAnalysisActivity.class);
            intent.putExtra("records", (java.io.Serializable) new ArrayList<>(records));
            intent.putExtra("source", getIntent().getStringExtra("source"));
            startActivity(intent);
        });
    }

    /**
     * 渲染统计。hidePractical=true 时剔除主观题（实操题/编程题/搭建题/简答题/附件题）后重新统计；
     * 数值变化使用与首页角标一致的滑动动画。
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
        int wrong = Math.max(0, total - correct);
        int score = total > 0 ? (int) Math.round(correct * 100.0 / total) : 0;

        android.widget.FrameLayout scoreBox = findViewById(R.id.scoreBox);
        android.widget.FrameLayout correctBox = findViewById(R.id.correctBox);
        android.widget.FrameLayout wrongBox = findViewById(R.id.wrongBox);
        android.widget.FrameLayout accuracyBox = findViewById(R.id.accuracyBox);
        // 只有数字做切换动画：答对/答错、百分号等文字保持不动
        CountSlideAnim.play(scoreBox, tvScore, tvScore.getText().toString(), String.valueOf(score));
        CountSlideAnim.play(correctBox, tvCorrect, tvCorrect.getText().toString(), String.valueOf(correct));
        CountSlideAnim.play(wrongBox, tvWrong, tvWrong.getText().toString(), String.valueOf(wrong));
        CountSlideAnim.play(accuracyBox, tvAccuracy, tvAccuracy.getText().toString(), String.valueOf(score));
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
