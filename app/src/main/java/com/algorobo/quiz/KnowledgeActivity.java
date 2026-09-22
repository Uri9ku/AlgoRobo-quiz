package com.algorobo.quiz;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** 知识点库：支持用户增删改查知识点，并与题目知识点引用联动。 */
public class KnowledgeActivity extends AppCompatActivity {
    private ListView lvKnowledge;
    private TextView tvEmpty;
    private ArrayAdapter<String> adapter;
    private List<String> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);
        PageTitle.apply(this, R.id.tvPageTitle);
        findViewById(R.id.btnKnowledgeBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnKnowledgeAdd).setOnClickListener(v -> showAddDialog());

        lvKnowledge = findViewById(R.id.lvKnowledge);
        tvEmpty = findViewById(R.id.tvKnowledgeEmpty);
        findViewById(R.id.btnGoImportExam).setOnClickListener(v -> goImportExam());
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, items);
        lvKnowledge.setAdapter(adapter);
        lvKnowledge.setOnItemClickListener((parent, view, pos, id) ->
                showActionDialog(items.get(pos)));
        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(KnowledgeStore.getAll(this));
        adapter.notifyDataSetChanged();
        boolean empty = items.isEmpty();
        findViewById(R.id.knowledgeEmptyBox).setVisibility(empty ? View.VISIBLE : View.GONE);
        lvKnowledge.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** 知识点为空时：跳转「真题练习」页面导入带知识点标签的真题。 */
    private void goImportExam() {
        android.content.Intent i = new android.content.Intent(this, ExamListActivity.class);
        PageTitle.put(i, "真题练习");
        startActivity(i);
    }

    private void showAddDialog() {
        final EditText et = new EditText(this);
        et.setHint("输入知识点名称，如：循环结构");
        new AlertDialog.Builder(this)
                .setTitle("新增知识点")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!KnowledgeStore.add(this, name)) {
                        Toast.makeText(this, "知识点已存在", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reload();
                    Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showActionDialog(String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"重命名", "删除"}, (d, which) -> {
                    if (which == 0) showRenameDialog(name);
                    else showDeleteDialog(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameDialog(String oldName) {
        final EditText et = new EditText(this);
        et.setText(oldName);
        et.setSelection(oldName.length());
        new AlertDialog.Builder(this)
                .setTitle("重命名知识点")
                .setMessage("重命名后，所有引用该知识点的题目会同步更新。")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!KnowledgeStore.rename(this, oldName, newName)) {
                        Toast.makeText(this, "重命名失败（可能存在同名）", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reload();
                    Toast.makeText(this, "已重命名并同步题目", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(String name) {
        new AlertDialog.Builder(this)
                .setTitle("删除知识点")
                .setMessage("确定删除「" + name + "」吗？\n所有题目中对该知识点的引用也会被清除。")
                .setPositiveButton("删除", (d, w) -> {
                    if (!KnowledgeStore.delete(this, name)) {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reload();
                    Toast.makeText(this, "已删除并清除引用", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
