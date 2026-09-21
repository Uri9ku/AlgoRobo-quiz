package com.algorobo.quiz;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class CheckInActivity extends AppCompatActivity {
    private TextView tvStreak, tvHint;
    private Button btnCheckin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkin);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);
        PageTitle.apply(this, R.id.tvPageTitle);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvStreak = findViewById(R.id.tvStreak);
        tvHint = findViewById(R.id.tvHint);
        btnCheckin = findViewById(R.id.btnCheckin);

        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        Set<String> days = DataStore.getCheckinDays(this);

        btnCheckin.setOnClickListener(v -> {
            DataStore.addCheckin(this, today);
            refresh();
        });

        refresh();
    }

    private void refresh() {
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        Set<String> days = DataStore.getCheckinDays(this);
        int streak = calcStreak(days);
        tvStreak.setText("已连续打卡 " + streak + " 天");
        if (days.contains(today)) {
            btnCheckin.setText("今日已打卡");
            btnCheckin.setEnabled(false);
            tvHint.setText("今天已打卡，继续保持！");
        } else {
            btnCheckin.setText("今日打卡");
            btnCheckin.setEnabled(true);
            tvHint.setText("坚持每日打卡，养成学习习惯");
        }
    }

    private int calcStreak(Set<String> days) {
        if (days.isEmpty()) return 0;
        List<String> sorted = new ArrayList<>(days);
        Collections.sort(sorted, Collections.reverseOrder());
        java.util.Calendar c = java.util.Calendar.getInstance();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        int streak = 0;
        String expect = fmt.format(c.getTime());
        for (String d : sorted) {
            if (d.equals(expect)) {
                streak++;
                c.add(java.util.Calendar.DAY_OF_YEAR, -1);
                expect = fmt.format(c.getTime());
            } else {
                break;
            }
        }
        return streak;
    }
}