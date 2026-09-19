package com.algorobo.quiz;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WrongBookActivity extends AppCompatActivity {
    private List<Question> wrongQuestions = new ArrayList<>();
    private WrongAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_book);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

        findViewById(R.id.btnWrongBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnWrongClear).setOnClickListener(v -> {
            DataStore.clearWrong(this);
            loadWrong();
        });

        ListView listView = findViewById(R.id.listWrong);
        TextView empty = findViewById(R.id.tvWrongEmpty);
        adapter = new WrongAdapter();
        listView.setAdapter(adapter);

        loadWrong();
        if (wrongQuestions.isEmpty()) empty.setVisibility(View.VISIBLE);
        else empty.setVisibility(View.GONE);
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
            ((TextView) c.findViewById(R.id.tvWrongDate)).setText(q.type);
            return c;
        }
    }
}