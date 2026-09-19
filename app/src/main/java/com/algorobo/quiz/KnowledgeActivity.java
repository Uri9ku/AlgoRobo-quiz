package com.algorobo.quiz;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** 知识点专题入口（壳页面）。当前仅搭框架，具体分类内容数据待后续完善。 */
public class KnowledgeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);
        findViewById(R.id.btnKnowledgeBack).setOnClickListener(v -> finish());
    }
}
