package com.algorobo.quiz;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        int total = getIntent().getIntExtra("total", 0);
        int correct = getIntent().getIntExtra("correct", 0);
        long durationSec = getIntent().getLongExtra("duration_sec", 0);
        int wrong = total - correct;

        int score = total > 0 ? (int) Math.round(correct * 100.0 / total) : 0;

        ((TextView) findViewById(R.id.tvScore)).setText(String.valueOf(score));
        ((TextView) findViewById(R.id.tvCorrect)).setText("答对 " + correct);
        ((TextView) findViewById(R.id.tvWrong)).setText("答错 " + wrong);
        ((TextView) findViewById(R.id.tvAccuracy)).setText(score + "%");
        ((TextView) findViewById(R.id.tvDuration)).setText(formatDuration(durationSec));

        findViewById(R.id.btnResultHome).setOnClickListener(v -> {
            finish();
        });
        findViewById(R.id.btnResultAgain).setOnClickListener(v -> {
            java.io.Serializable records = getIntent().getSerializableExtra("records");
            android.content.Intent intent = new android.content.Intent(this, AllAnalysisActivity.class);
            intent.putExtra("records", records);
            startActivity(intent);
        });
        findViewById(R.id.btnResultRedoWrong).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, QuizActivity.class);
            intent.putExtra("source", "wrong");
            intent.putExtra("random", false);
            startActivity(intent);
            finish();
        });
        findViewById(R.id.btnResultOneMore).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, QuizActivity.class);
            intent.putExtra("source", "all");
            intent.putExtra("random", true);
            startActivity(intent);
            finish();
        });
    }

    private String formatDuration(long sec) {
        long m = sec / 60, s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }
}