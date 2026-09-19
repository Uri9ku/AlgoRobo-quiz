package com.algorobo.quiz;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;

public class CalendarActivity extends AppCompatActivity {
    private Calendar cal = Calendar.getInstance();
    private TextView tvMonth;
    private GridView grid;
    private Set<String> checkinDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvMonth = findViewById(R.id.tvMonth);
        grid = findViewById(R.id.gridCalendar);

        findViewById(R.id.btnPrev).setOnClickListener(v -> { cal.add(Calendar.MONTH, -1); render(); });
        findViewById(R.id.btnNext).setOnClickListener(v -> { cal.add(Calendar.MONTH, 1); render(); });

        render();
    }

    private void render() {
        checkinDays = DataStore.getCheckinDays(this);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy年M月");
        // 显示目标月份
        Calendar display = (Calendar) cal.clone();
        tvMonth.setText(fmt.format(display.getTime()));

        final int year = cal.get(Calendar.YEAR);
        final int month = cal.get(Calendar.MONTH);

        Calendar first = Calendar.getInstance();
        first.set(year, month, 1);
        int firstDayOfWeek = first.get(Calendar.DAY_OF_WEEK); // 1=周日
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);

        final int totalCells = firstDayOfWeek - 1 + daysInMonth;
        final int[] days = new int[totalCells];
        for (int i = 0; i < daysInMonth; i++) days[firstDayOfWeek - 1 + i] = i + 1;

        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");

        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return totalCells; }
            @Override public Object getItem(int p) { return null; }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View c, ViewGroup parent) {
                if (c == null) c = getLayoutInflater().inflate(R.layout.item_calendar_day, parent, false);
                TextView tvDay = c.findViewById(R.id.tvDay);
                TextView tvDot = c.findViewById(R.id.tvDot);
                int day = days[p];
                if (day == 0) {
                    tvDay.setText("");
                    tvDot.setVisibility(View.INVISIBLE);
                    return c;
                }
                tvDay.setText(String.valueOf(day));
                Calendar d = (Calendar) first.clone();
                d.set(year, month, day);
                String key = dayFmt.format(d.getTime());
                tvDot.setVisibility(checkinDays.contains(key) ? View.VISIBLE : View.INVISIBLE);

                // 今天高亮
                Calendar today = Calendar.getInstance();
                if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month && today.get(Calendar.DAY_OF_MONTH) == day) {
                    c.setBackgroundResource(R.drawable.bg_calendar_today);
                } else {
                    c.setBackgroundResource(android.R.color.transparent);
                }
                return c;
            }
        });
    }
}