package com.algorobo.quiz;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.content.SharedPreferences;

public class WrongBookActivity extends AppCompatActivity {
    private List<Question> wrongQuestions = new ArrayList<>();
    private WrongAdapter adapter;
    private String sortOrder = "default"; // default / errcount / recent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_book);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);
        PageTitle.apply(this, R.id.tvPageTitle);

        findViewById(R.id.btnWrongBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnWrongClear).setOnClickListener(v -> {
            DataStore.clearWrong(this);
            loadWrong();
        });
        findViewById(R.id.btnWrongSort).setOnClickListener(v -> showSortDialog());

        ListView listView = findViewById(R.id.listWrong);
        TextView empty = findViewById(R.id.tvWrongEmpty);
        adapter = new WrongAdapter();
        listView.setAdapter(adapter);

        SharedPreferences sp = getSharedPreferences(DataStore.PREFS, MODE_PRIVATE);
        sortOrder = DataStore.getWrongSort(this);

        loadWrong();
        if (wrongQuestions.isEmpty()) empty.setVisibility(View.VISIBLE);
        else empty.setVisibility(View.GONE);
    }

    private void showSortDialog() {
        final String[] labels = {"默认顺序", "错误次数多优先", "最近做错优先"};
        final String[] values = {"default", "errcount", "recent"};
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(sortOrder)) { idx = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("排序方式")
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    sortOrder = values[which];
                    DataStore.setWrongSort(WrongBookActivity.this, sortOrder);
                    loadWrong();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sortWrong() {
        if ("errcount".equals(sortOrder)) {
            java.util.Collections.sort(wrongQuestions, (a, b) ->
                    DataStore.getWrongCount(this, b.uniqueKey())
                            - DataStore.getWrongCount(this, a.uniqueKey()));
        } else if ("recent".equals(sortOrder)) {
            java.util.Collections.sort(wrongQuestions, (a, b) ->
                    Long.compare(DataStore.getWrongTime(this, b.uniqueKey()),
                            DataStore.getWrongTime(this, a.uniqueKey())));
        }
    }

    private void loadWrong() {
        wrongQuestions.clear();
        Set<String> ids = DataStore.getWrongIds(this);

        // 内置题库
        for (Question q : QuestionBank.getAll()) {
            if (ids.contains(q.uniqueKey())) wrongQuestions.add(q);
        }
        // 自定义题库
        for (Question q : DataStore.getCustomQuestions(this)) {
            if (ids.contains(q.uniqueKey())) wrongQuestions.add(q);
        }
        // 下载真题（RobotExamBank 缓存中的所有试卷）
        for (RobotExamBank.Paper p : RobotExamBank.getCachedPapersList(this)) {
            if (p.questions == null) continue;
            for (Question q : p.questions) {
                if (ids.contains(q.uniqueKey())) wrongQuestions.add(q);
            }
        }

        sortWrong();
        adapter.notifyDataSetChanged();
        TextView empty = findViewById(R.id.tvWrongEmpty);
        empty.setVisibility(wrongQuestions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    class WrongAdapter extends android.widget.BaseAdapter {
        @Override public int getCount() { return wrongQuestions.size(); }
        @Override public Object getItem(int p) { return wrongQuestions.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View c, android.view.ViewGroup parent) {
            if (c == null) c = getLayoutInflater().inflate(R.layout.item_wrong, parent, false);
            Question q = wrongQuestions.get(p);
            ((TextView) c.findViewById(R.id.tvWrongQuestion)).setText(q.stem);
            ((TextView) c.findViewById(R.id.tvWrongAnswer)).setText("正确答案：" + (char) ('A' + q.answerIndex));
            // 行标签：题型 + 错了 N 次（有记录时）
            int wc = DataStore.getWrongCount(WrongBookActivity.this, q.uniqueKey());
            String label = (q.type == null || q.type.isEmpty() ? "" : q.type + " · ")
                    + (wc > 0 ? "错了 " + wc + " 次" : "错题");
            ((TextView) c.findViewById(R.id.tvWrongDate)).setText(label);
            return c;
        }
    }
}
