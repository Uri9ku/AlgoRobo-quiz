package com.algorobo.quiz;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CustomBankActivity extends AppCompatActivity {
    private static final int REQ_IMPORT_FILE = 1001;

    private TextView tvCustomBankCount;
    private EditText etStem, etAnswer, etAnalysis;
    private LinearLayout optionsInputContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_bank);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

        tvCustomBankCount = findViewById(R.id.tvCustomBankCount);
        etStem = findViewById(R.id.etStem);
        etAnswer = findViewById(R.id.etAnswer);
        etAnalysis = findViewById(R.id.etAnalysis);
        optionsInputContainer = findViewById(R.id.optionsInputContainer);

        TextView btnCustomBankBack = findViewById(R.id.btnCustomBankBack);
        TextView btnSaveOne = findViewById(R.id.btnSaveOne);
        TextView btnSaveDone = findViewById(R.id.btnSaveDone);
        LinearLayout btnImportFile = findViewById(R.id.btnImportFile);
        TextView btnClearBank = findViewById(R.id.btnClearBank);

        btnCustomBankBack.setOnClickListener(v -> finish());
        btnSaveOne.setOnClickListener(v -> saveOne(true));
        btnSaveDone.setOnClickListener(v -> saveOne(false));
        btnImportFile.setOnClickListener(v -> openFilePicker());
        btnClearBank.setOnClickListener(v -> clearBank());

        // 初始化选项输入框（默认 4 个 A/B/C/D）
        resetOptionInputs(4);
        refreshCount();
    }

    // 重置选项输入框数量
    private void resetOptionInputs(int count) {
        optionsInputContainer.removeAllViews();
        for (int i = 0; i < count; i++) {
            addOptionInput();
        }
    }

    private void addOptionInput() {
        EditText et = new EditText(this);
        int idx = optionsInputContainer.getChildCount();
        et.setHint("选项 " + (char) ('A' + idx));
        et.setTextColor(getResources().getColor(R.color.text_main));
        et.setHintTextColor(getResources().getColor(R.color.text_sub));
        et.setBackgroundResource(R.drawable.bg_option_normal);
        et.setPadding(dp(12), dp(12), dp(12), dp(12));
        et.setTextSize(14);
        int marginTop = idx == 0 ? 0 : dp(8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = marginTop;
        et.setLayoutParams(lp);
        optionsInputContainer.addView(et);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshCount() {
        int n = DataStore.getCustomQuestions(this).size();
        tvCustomBankCount.setText("当前题库共 " + n + " 题");
    }

    // 保存一道题。continueAdd=true 则清空输入继续添加，否则保存后退出
    private void saveOne(boolean continueAdd) {
        String stem = etStem.getText().toString().trim();
        String answerStr = etAnswer.getText().toString().trim().toUpperCase();
        String analysis = etAnalysis.getText().toString().trim();

        if (stem.isEmpty()) {
            toast("请输入题目内容");
            return;
        }
        if (answerStr.isEmpty() || answerStr.length() != 1 || answerStr.charAt(0) < 'A' || answerStr.charAt(0) > 'Z') {
            toast("请输入正确的答案选项（A-Z）");
            return;
        }

        // 收集非空选项
        List<String> opts = new ArrayList<>();
        for (int i = 0; i < optionsInputContainer.getChildCount(); i++) {
            View v = optionsInputContainer.getChildAt(i);
            if (v instanceof EditText) {
                String s = ((EditText) v).getText().toString().trim();
                if (!s.isEmpty()) opts.add(s);
            }
        }
        if (opts.size() < 2) {
            toast("请至少填写两个选项");
            return;
        }
        int answerIndex = answerStr.charAt(0) - 'A';
        if (answerIndex >= opts.size()) {
            toast("答案选项超出已填选项范围");
            return;
        }

        int id = nextId();
        Question q = new Question(id, "自定义", stem, opts.toArray(new String[0]), answerIndex, analysis);
        List<Question> one = new ArrayList<>();
        one.add(q);
        DataStore.addCustomQuestions(this, one);

        toast("已保存 1 题，当前共 " + DataStore.getCustomQuestions(this).size() + " 题");
        refreshCount();

        if (continueAdd) {
            // 清空输入
            etStem.setText("");
            etAnswer.setText("");
            etAnalysis.setText("");
            resetOptionInputs(4);
        } else {
            finish();
        }
    }

    private int nextId() {
        List<Question> all = DataStore.getCustomQuestions(this);
        int max = 100000; // 自定义题库 id 从 100000 开始，避免与内置冲突
        for (Question q : all) if (q.id >= max) max = q.id;
        return max + 1;
    }

    private void clearBank() {
        int n = DataStore.getCustomQuestions(this).size();
        if (n == 0) {
            toast("题库已为空");
            return;
        }
        DataStore.clearCustomQuestions(this);
        refreshCount();
        toast("已清空 " + n + " 题");
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mime = {"text/plain", "text/markdown", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mime);
        startActivityForResult(intent, REQ_IMPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handleImportedFile(uri);
            }
        }
    }

    private void handleImportedFile(Uri uri) {
        try {
            String name = getFileName(uri);
            String content;
            if (name != null && name.toLowerCase().endsWith(".docx")) {
                content = extractDocxText(uri);
            } else {
                content = readTextStream(uri);
            }
            if (content == null || content.trim().isEmpty()) {
                toast("文件内容为空或无法读取");
                return;
            }
            List<Question> parsed = parseQuestions(content);
            if (parsed.isEmpty()) {
                toast("未能从文件中解析出题目，请检查格式");
                return;
            }
            DataStore.addCustomQuestions(this, parsed);
            refreshCount();
            toast("成功导入 " + parsed.size() + " 题，当前共 " + DataStore.getCustomQuestions(this).size() + " 题");
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    private String getFileName(Uri uri) {
        String name = null;
        String last = uri.getLastPathSegment();
        if (last != null) {
            int idx = last.lastIndexOf('/');
            name = idx >= 0 ? last.substring(idx + 1) : last;
        }
        return name;
    }

    private String readTextStream(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }

    // 从 .docx 提取纯文本：.docx 本质是 zip，word/document.xml 中包含文本
    private String extractDocxText(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) return null;
        StringBuilder sb = new StringBuilder();
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().equals("word/document.xml")) {
                BufferedReader br = new BufferedReader(new InputStreamReader(zis, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                break;
            }
        }
        zis.close();
        if (sb.length() == 0) return null;
        // 将 <w:p> 段落转为换行，<w:t> 之间的文本保留，去掉标签
        String xml = sb.toString();
        xml = xml.replaceAll("<w:p[^>]*>", "\n");
        xml = xml.replaceAll("</w:p>", "");
        xml = xml.replaceAll("<[^>]+>", "");
        return xml;
    }

    // 解析文本为题目列表。支持两种格式：
    // 1) JSON 数组：与 Question 结构一致
    // 2) 文本：题目/选项/答案/解析 分行格式
    private List<Question> parseQuestions(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("[")) {
            List<Question> fromJson = parseJson(trimmed);
            if (fromJson != null) return fromJson;
        }

        // 文本格式解析
        List<Question> result = new ArrayList<>();
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> block = new ArrayList<>();
        int idSeed = nextId();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (!block.isEmpty()) {
                    Question q = parseBlock(block, idSeed);
                    if (q != null) { result.add(q); idSeed++; }
                    block.clear();
                }
            } else {
                block.add(line);
            }
        }
        if (!block.isEmpty()) {
            Question q = parseBlock(block, idSeed);
            if (q != null) result.add(q);
        }
        return result;
    }

    private List<Question> parseJson(String json) {
        try {
            Type type = new TypeToken<List<Question>>() {}.getType();
            List<Question> list = new Gson().fromJson(json, type);
            return list == null ? new ArrayList<Question>() : list;
        } catch (Exception e) {
            return null;
        }
    }

    // 解析一个题目块。约定：
    // 第一行 = 题干
    // 以 A./A)/A、 等开头的行 = 选项
    // 「答案」开头的行 = 正确答案
    // 「解析」开头的行 = 解析
    private Question parseBlock(List<String> block, int id) {
        if (block.isEmpty()) return null;
        String stem = block.get(0);
        List<String> opts = new ArrayList<>();
        String answer = null;
        StringBuilder analysis = new StringBuilder();

        for (int i = 1; i < block.size(); i++) {
            String line = block.get(i);
            String low = line;
            // 匹配选项行：A. 或 A) 或 A、 或 A： 等
            if (low.length() >= 2) {
                char c = low.charAt(0);
                char sep = low.charAt(1);
                boolean isOption = false;
                if (c >= 'A' && c <= 'Z' && (sep == '.' || sep == ')' || sep == '、' || sep == '：' || sep == ':' || sep == ' ')) {
                    // 排除 "答案"/"解析" 等中文，排除单词如 An
                    if (!(low.startsWith("答案") || low.startsWith("解析"))) {
                        isOption = true;
                    }
                }
                if (isOption) {
                    opts.add(line.substring(1).replaceAll("^[.、：:) ]+", "").trim());
                    continue;
                }
            }
            if (low.startsWith("答案") || low.startsWith("正确答案") || low.startsWith("Answer")) {
                String a = line.replaceAll("^[^：:]*[：:]", "").trim();
                if (!a.isEmpty()) answer = a;
                continue;
            }
            if (low.startsWith("解析") || low.startsWith("Analysis")) {
                String a = line.replaceAll("^[^：:]*[：:]", "").trim();
                if (!a.isEmpty()) analysis.append(a);
                continue;
            }
            // 其他行归入解析
            if (analysis.length() > 0) analysis.append("\n");
            analysis.append(line);
        }

        if (opts.size() < 2) return null;
        int answerIndex = -1;
        if (answer != null) {
            String a = answer.trim().toUpperCase();
            if (!a.isEmpty() && a.charAt(0) >= 'A' && a.charAt(0) <= 'Z') {
                answerIndex = a.charAt(0) - 'A';
            }
        }
        if (answerIndex < 0 || answerIndex >= opts.size()) {
            answerIndex = 0; // 默认第一个选项
        }

        return new Question(id, "自定义", stem, opts.toArray(new String[0]), answerIndex, analysis.toString());
    }

    private void toast(String msg) {
        int duration = DataStore.getToastDuration(this);
        Toast.makeText(this, msg,
                duration >= 3500 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }
}