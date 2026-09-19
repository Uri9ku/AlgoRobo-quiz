package com.algorobo.quiz;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.cardPractice).setOnClickListener(v ->
            startActivity(new Intent(this, ExamListActivity.class)));
        findViewById(R.id.modeSequence).setOnClickListener(v -> startQuizBySource("custom"));
        findViewById(R.id.modeRandom).setOnClickListener(v -> startQuiz(true));

        findViewById(R.id.cardWrongBook).setOnClickListener(v -> startQuizBySource("wrong"));
        findViewById(R.id.cardFavorite).setOnClickListener(v -> startQuizBySource("favorite"));
        findViewById(R.id.cardStatistics).setOnClickListener(v ->
            startActivity(new Intent(this, StatisticsActivity.class)));
        findViewById(R.id.cardCalendar).setOnClickListener(v ->
            startActivity(new Intent(this, CalendarActivity.class)));

        findViewById(R.id.cardCustomBank).setOnClickListener(v ->
            startActivity(new Intent(this, CustomBankActivity.class)));

        findViewById(R.id.cardKnowledge).setOnClickListener(v ->
            startActivity(new Intent(this, KnowledgeActivity.class)));

        findViewById(R.id.fabSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));

        bindProgress();
        bindBadges();
        bindResume();
    }

    // 今日进度概览数据绑定
    private void bindProgress() {
        int[] today = DataStore.getTodayStats(this);
        ((TextView) findViewById(R.id.tvTodayCount)).setText(String.valueOf(today[0]));
        ((TextView) findViewById(R.id.tvTotalCount)).setText(String.valueOf(DataStore.getTotal(this)));
        ((TextView) findViewById(R.id.tvAccuracy)).setText(DataStore.getAccuracy(this) + "%");
        ((TextView) findViewById(R.id.tvStreak)).setText(String.valueOf(DataStore.getStreakDays(this)));
    }

    // 错题本 / 收藏角标
    private void bindBadges() {
        int wrong = DataStore.getWrongIds(this).size();
        int favorite = DataStore.getFavoriteIds(this).size();
        TextView badgeWrong = findViewById(R.id.badgeWrong);
        TextView badgeFavorite = findViewById(R.id.badgeFavorite);
        if (wrong > 0) { badgeWrong.setVisibility(View.VISIBLE); badgeWrong.setText(String.valueOf(wrong)); }
        else badgeWrong.setVisibility(View.GONE);
        if (favorite > 0) { badgeFavorite.setVisibility(View.VISIBLE); badgeFavorite.setText(String.valueOf(favorite)); }
        else badgeFavorite.setVisibility(View.GONE);
    }

    // 继续上次进度按钮
    private void bindResume() {
        DataStore.ResumeState resume = DataStore.getResume(this);
        TextView btnResume = findViewById(R.id.btnResume);
        if (resume == null) {
            btnResume.setVisibility(View.GONE);
            return;
        }
        btnResume.setVisibility(View.VISIBLE);
        btnResume.setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra("source", resume.source);
            i.putExtra("random", resume.random);
            i.putExtra("resume", true);
            i.putExtra("resume_index", resume.index);
            i.putStringArrayListExtra("resume_uids", new java.util.ArrayList<>(resume.uids));
            startActivity(i);
        });
    }

    private void startQuiz(boolean random) {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("random", random);
        startActivity(i);
    }

    private void startQuizBySource(String source) {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("source", source);
        i.putExtra("random", false);
        startActivity(i);
    }
}