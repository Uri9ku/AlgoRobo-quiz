package com.algorobo.quiz;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK_EXAM_DIR = 1001;

    private TextView tvToastValue, tvAnimValue, tvDarkModeValue;
    private SeekBar sbToast, sbAnim;
    private SwitchCompat swAutoNext;
    private TextView tvAiModelValue;
    private TextView tvAiProgress;
    private ProgressBar pbAiProgress;
    private TextView tvWrongThresholdValue;
    private SeekBar sbWrongThreshold;
    private SwitchCompat swAutoWrong;
    private SwitchCompat swAutoAiAnalysis;
    private TextView tvAutoAiAnalysisModeValue;
    private TextView tvExamDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvToastValue = findViewById(R.id.tvToastValue);
        tvAnimValue = findViewById(R.id.tvAnimValue);
        tvDarkModeValue = findViewById(R.id.tvDarkModeValue);
        sbToast = findViewById(R.id.sbToast);
        sbAnim = findViewById(R.id.sbAnim);
        swAutoNext = findViewById(R.id.swAutoNext);
        tvAiModelValue = findViewById(R.id.tvAiModelValue);
        tvAiProgress = findViewById(R.id.tvAiProgress);
        pbAiProgress = findViewById(R.id.pbAiProgress);
        tvWrongThresholdValue = findViewById(R.id.tvWrongThresholdValue);
        sbWrongThreshold = findViewById(R.id.sbWrongThreshold);
        swAutoWrong = findViewById(R.id.swAutoWrong);
        swAutoAiAnalysis = findViewById(R.id.swAutoAiAnalysis);
        tvAutoAiAnalysisModeValue = findViewById(R.id.tvAutoAiAnalysisModeValue);
        tvExamDir = findViewById(R.id.tvExamDir);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());

        // 读取并显示当前设置
        refreshUi();

        // Toast 时长：SeekBar 范围 1000~5000ms，步长 100ms
        sbToast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int ms = 1000 + progress * 100;
                if (fromUser) {
                    DataStore.setToastDuration(SettingsActivity.this, ms);
                }
                tvToastValue.setText(String.format(java.util.Locale.US, "%.1f 秒", ms / 1000.0));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                showToast("Toast 时长已设为 " + String.format(java.util.Locale.US, "%.1f 秒", (1000 + bar.getProgress() * 100) / 1000.0));
            }
        });

        // 动画时长：SeekBar 范围 100~600ms，步长 10ms
        sbAnim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int ms = 100 + progress * 10;
                if (fromUser) {
                    DataStore.setAnimDuration(SettingsActivity.this, ms);
                }
                tvAnimValue.setText(ms + " 毫秒");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                showToast("动画时长已设为 " + (100 + bar.getProgress() * 10) + " 毫秒");
            }
        });

        // 深色模式：弹出选项
        findViewById(R.id.btnDarkMode).setOnClickListener(v -> showDarkModeDialog());

        // 自动翻页
        swAutoNext.setChecked(DataStore.isAutoNext(this));
        swAutoNext.setOnCheckedChangeListener((btn, checked) -> {
            DataStore.setAutoNext(this, checked);
            showToast("自动下一题已" + (checked ? "开启" : "关闭"));
        });

        // 做对次数阈值：SeekBar 范围 0~9，映射到 1~10 次
        sbWrongThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int n = progress + 1;
                tvWrongThresholdValue.setText(n + " 次");
                if (fromUser) {
                    DataStore.setWrongThreshold(SettingsActivity.this, n);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                showToast("做对次数阈值已设为 " + (bar.getProgress() + 1) + " 次");
            }
        });

        // 自动加入错题本开关
        swAutoWrong.setOnCheckedChangeListener((btn, checked) -> {
            DataStore.setAutoWrong(SettingsActivity.this, checked);
            showToast("自动加入错题本已" + (checked ? "开启" : "关闭"));
        });

        // AI 自动解析开关
        swAutoAiAnalysis.setOnCheckedChangeListener((btn, checked) -> {
            DataStore.setAutoAiAnalysis(SettingsActivity.this, checked);
            showToast("AI 自动解析已" + (checked ? "开启" : "关闭"));
        });
        // AI 自动解析模式选择
        findViewById(R.id.btnAutoAiAnalysisMode).setOnClickListener(v -> showAutoAiAnalysisModeDialog());

        // 更改题库下载目录
        findViewById(R.id.btnChangeExamDir).setOnClickListener(v -> onChangeExamDir());

        // 打开题库下载目录
        findViewById(R.id.btnOpenExamDir).setOnClickListener(v -> onOpenExamDir());

        initAiConfig();
    }

    private void refreshUi() {
        int toastMs = DataStore.getToastDuration(this);
        tvToastValue.setText(String.format(java.util.Locale.US, "%.1f 秒", toastMs / 1000.0));
        sbToast.setProgress((toastMs - 1000) / 100);

        int animMs = DataStore.getAnimDuration(this);
        tvAnimValue.setText(animMs + " 毫秒");
        sbAnim.setProgress((animMs - 100) / 10);

        int mode = DataStore.getDarkMode(this);
        tvDarkModeValue.setText(mode == 2 ? "深色" : mode == 1 ? "浅色" : "跟随系统");

        tvAiModelValue.setText(aiModelDisplayName(DataStore.getAiModel(this)));
        int threshold = DataStore.getWrongThreshold(this);
        tvWrongThresholdValue.setText(threshold + " 次");
        sbWrongThreshold.setProgress(threshold - 1);

        swAutoWrong.setChecked(DataStore.isAutoWrong(this));
        swAutoAiAnalysis.setChecked(DataStore.getAutoAiAnalysis(this));
        tvAutoAiAnalysisModeValue.setText(DataStore.getAutoAiAnalysisMode(this).equals("wrong") ? "只对错题解析" : "对错都生成解析");

        tvExamDir.setText(DataStore.getExamDir(this));
    }

    private void showAutoAiAnalysisModeDialog() {
        final String[] labels = {"对错都生成解析", "只对错题解析"};
        final String[] values = {"all", "wrong"};
        String current = DataStore.getAutoAiAnalysisMode(this);
        int idx = current.equals("wrong") ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("AI 解析模式")
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    DataStore.setAutoAiAnalysisMode(this, values[which]);
                    refreshUi();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private void showDarkModeDialog() {
        final String[] labels = {"跟随系统", "浅色", "深色"};
        final int[] values = {0, 1, 2};
        int current = DataStore.getDarkMode(this);

        new AlertDialog.Builder(this)
                .setTitle("深色模式")
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    DataStore.setDarkMode(this, values[which]);
                    applyDarkMode(values[which]);
                    refreshUi();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyDarkMode(int mode) {
        int nightMode;
        switch (mode) {
            case 1: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
            case 2: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ==================== 题库下载目录 ====================

    /**
     * 更改题库下载目录：弹出系统目录选择器（SAF），选中后记录新目录并迁移旧目录中的题库文件。
     */
    private void onChangeExamDir() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(intent, REQ_PICK_EXAM_DIR);
        } catch (Exception e) {
            showToast("当前设备不支持系统目录选择器");
        }
    }

    /** 打开题库下载目录（复用 DataStore 统一安全方案，FileProvider + 多 MIME）。 */
    private void onOpenExamDir() {
        DataStore.openExamDir(this, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_EXAM_DIR && resultCode == RESULT_OK && data != null) {
            android.net.Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    // 持久化目录访问权限
                    final int flags = data.getFlags() & (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(treeUri, flags);

                    // 将 Uri 转换为实际文件路径，若失败则记录 Uri 字符串
                    String newPath = resolveTreeUriPath(treeUri);
                    String oldPath = DataStore.getExamDir(this);
                    if (newPath == null || newPath.isEmpty()) {
                        newPath = treeUri.toString();
                    }

                    DataStore.setExamDir(this, newPath);

                    // 迁移旧目录中的题库文件到新目录
                    if (newPath != null && !newPath.isEmpty() && !newPath.startsWith("content://")) {
                        java.io.File oldDir = new java.io.File(oldPath);
                        java.io.File newDir = new java.io.File(newPath);
                        if (!oldPath.equals(newPath)) {
                            int moved = RobotExamUpdater.migrateDownloads(this, oldDir, newDir);
                            if (moved > 0) {
                                showToast("已迁移 " + moved + " 个题库文件");
                            }
                        }
                    }

                    tvExamDir.setText(newPath);
                    showToast("题库下载目录已更新");
                } catch (Exception e) {
                    showToast("设置目录失败：" + e.getMessage());
                }
            }
        }
    }

    /** 尽量将 SAF 树 Uri 解析为真实文件系统路径。 */
    private String resolveTreeUriPath(android.net.Uri treeUri) {
        // 尝试直接映射到文件系统路径（仅支持部分 ROM 的 DocumentsProvider）
        String docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
        if (docId == null) return null;
        // 常见格式：primary:Download/xxx 或 storage 卷
        if (docId.startsWith("primary:")) {
            String rel = docId.substring("primary:".length());
            java.io.File base = android.os.Environment.getExternalStorageDirectory();
            java.io.File f = new java.io.File(base, rel);
            return f.getAbsolutePath();
        }
        return null;
    }

    // ==================== AI 知识点识别配置 ====================
    private static final String[] AI_MODELS = {"operit", "openai", "custom"};
    private static final String[] AI_MODEL_LABELS = {"Operit AI", "OpenAI", "自定义"};

    private void initAiConfig() {
        findViewById(R.id.btnAiModelConfig).setOnClickListener(v -> {
            startActivity(new Intent(this, AiConfigActivity.class));
        });
        findViewById(R.id.btnAiTest).setOnClickListener(v -> onAiTest());
        findViewById(R.id.btnRecognizeKnowledge).setOnClickListener(v -> onRecognizeKnowledge());
    }

    private String aiModelDisplayName(String model) {
        for (int i = 0; i < AI_MODELS.length; i++) {
            if (AI_MODELS[i].equals(model)) return AI_MODEL_LABELS[i];
        }
        return model == null || model.isEmpty() ? "Operit AI" : model;
    }

    private void showAiModelDialog() {
        int current = 0;
        String m = DataStore.getAiModel(this);
        for (int i = 0; i < AI_MODELS.length; i++) {
            if (AI_MODELS[i].equals(m)) { current = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("AI 模型")
                .setSingleChoiceItems(AI_MODEL_LABELS, current, (d, which) -> {
                    DataStore.setAiModel(this, AI_MODELS[which]);
                    tvAiModelValue.setText(AI_MODEL_LABELS[which]);
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void onAiTest() {
        AiApi.Config cfg = DataStore.getCurrentAiConfig(this);
        if (cfg == null || !cfg.isComplete()) {
            showToast("请先点击上方「AI 模型配置」完善 API 配置");
            startActivity(new Intent(this, AiConfigActivity.class));
            return;
        }
        pbAiProgress.setVisibility(android.view.View.VISIBLE);
        tvAiProgress.setText("正在测试连接…");
        new Thread(() -> {
            AiApi.Result r = AiApi.testConnection(cfg);
            runOnUiThread(() -> {
                pbAiProgress.setVisibility(android.view.View.GONE);
                if (r.ok) {
                    tvAiProgress.setText("连接成功");
                    showToast("连接测试成功");
                } else {
                    tvAiProgress.setText("连接失败");
                    showToast("连接失败：" + r.text);
                }
            });
        }).start();
    }

    private void onRecognizeKnowledge() {
        AiApi.Config cfg = DataStore.getCurrentAiConfig(this);
        if (cfg == null || !cfg.isComplete()) {
            showToast("请先点击上方「AI 模型配置」完善 API 配置");
            startActivity(new Intent(this, AiConfigActivity.class));
            return;
        }
        pbAiProgress.setVisibility(android.view.View.VISIBLE);
        tvAiProgress.setText("正在识别知识点…");
        new Thread(() -> {
            Question sample = new Question(0, Question.TYPE_SINGLE,
                    "衡量一个国家教育水平的重要指标是（ ）",
                    new String[]{"经济发展水平", "国民受教育程度", "人口数量", "国土面积"},
                    1, "国民受教育程度是衡量教育发展水平的核心指标。");
            AiApi.Result r = AiApi.recognizeKnowledge(this, cfg, sample);
            runOnUiThread(() -> {
                pbAiProgress.setVisibility(android.view.View.GONE);
                if (r.ok) {
                    tvAiProgress.setText("识别完成");
                    showToast("知识点识别成功");
                } else {
                    tvAiProgress.setText("识别失败");
                    showToast("识别失败：" + r.text);
                }
            });
        }).start();
    }
}