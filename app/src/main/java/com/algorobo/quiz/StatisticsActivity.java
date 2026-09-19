package com.algorobo.quiz;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class StatisticsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        int total = DataStore.getTotal(this);
        int right = DataStore.getRight(this);
        int accuracy = total > 0 ? (int) Math.round(right * 100.0 / total) : 0;

        ((TextView) findViewById(R.id.tvTotal)).setText(String.valueOf(total));
        ((TextView) findViewById(R.id.tvAccuracy)).setText(accuracy + "%");

        LineChartView lineChart = findViewById(R.id.lineChart);
        lineChart.setData(DataStore.getRecent7Days(this));

        BarChartView barChart = findViewById(R.id.barChart);
        barChart.setData(DataStore.getTypeStats(this));
    }
}