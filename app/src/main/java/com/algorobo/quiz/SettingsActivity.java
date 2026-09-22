package com.algorobo.quiz;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
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
    private TextView tvFontSizeValue;
    private SeekBar sbFontSize;
    private TextView tvAiModelValue;
    private TextView tvWrongThresholdValue;
    private SeekBar sbWrongThreshold;
    private SwitchCompat swAutoWrong;
    private SwitchCompat swAutoAiAnalysis;
    private TextView tvAutoAiAnalysisModeValue;
    private TextView tvExamDir;
    private android.widget.LinearLayout headerTopBar;
    private android.widget.LinearLayout themeColorPalette;
    private android.widget.HorizontalScrollView themeColorScroll;
    private android.widget.LinearLayout customColorRow;
    private android.widget.TextView customColorBtn;
    private android.widget.TextView customColorSwatch;
    private boolean themeColorExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ThemeManager.applyStatusBar(this);
        PageTitle.apply(this, R.id.tvPageTitle);
        tvToastValue = findViewById(R.id.tvToastValue);
        tvAnimValue = findViewById(R.id.tvAnimValue);
        tvDarkModeValue = findViewById(R.id.tvDarkModeValue);
        sbToast = findViewById(R.id.sbToast);
        sbAnim = findViewById(R.id.sbAnim);
        swAutoNext = findViewById(R.id.swAutoNext);
        tvFontSizeValue = findViewById(R.id.tvFontSizeValue);
        sbFontSize = findViewById(R.id.sbFontSize);
        tvAiModelValue = findViewById(R.id.tvAiModelValue);
        tvWrongThresholdValue = findViewById(R.id.tvWrongThresholdValue);
        sbWrongThreshold = findViewById(R.id.sbWrongThreshold);
        swAutoWrong = findViewById(R.id.swAutoWrong);
        swAutoAiAnalysis = findViewById(R.id.swAutoAiAnalysis);
        tvAutoAiAnalysisModeValue = findViewById(R.id.tvAutoAiAnalysisModeValue);
        tvExamDir = findViewById(R.id.tvExamDir);
        headerTopBar = findViewById(R.id.settingsTopBar);
        themeColorPalette = findViewById(R.id.themeColorPalette);
        themeColorScroll = findViewById(R.id.themeColorScroll);
        customColorRow = findViewById(R.id.customColorRow);
        customColorBtn = findViewById(R.id.customColorBtn);
        customColorSwatch = findViewById(R.id.customColorSwatch);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());

        // 点击标题行折叠/展开滑动条（三个数字类设置卡片）
        setupCollapse(R.id.cardToastHeader, R.id.cardToastBody);
        setupCollapse(R.id.cardAnimHeader, R.id.cardAnimBody);
        setupCollapse(R.id.cardThresholdHeader, R.id.cardThresholdBody);
        setupCollapse(R.id.cardFontSizeHeader, R.id.cardFontSizeBody);

        // 主题色：渲染色板 + 即时染色
        setupThemeColor();

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
        // 真题字号：SeekBar 范围 14~26sp，步长 1sp
        sbFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float sp = 14f + progress;
                tvFontSizeValue.setText(((int) sp) + " sp");
                if (fromUser) {
                    DataStore.setQuestionFontSp(SettingsActivity.this, sp);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                showToast("真题字号已设为 " + (14 + bar.getProgress()) + " sp");
            }
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

        // 下载目录标题点击折叠/展开按钮区
        final android.widget.LinearLayout examDirButtons = findViewById(R.id.examDirButtons);
        final TextView ivExamDirArrow = findViewById(R.id.ivExamDirArrow);
        findViewById(R.id.cardExamDirHeader).setOnClickListener(v -> {
            if (examDirButtons.getVisibility() == android.view.View.GONE) {
                examDirButtons.setVisibility(android.view.View.VISIBLE);
                ivExamDirArrow.setText(" ˯");
            } else {
                examDirButtons.setVisibility(android.view.View.GONE);
                ivExamDirArrow.setText(" ›");
            }
        });

        // 更改题库下载目录
        findViewById(R.id.btnChangeExamDir).setOnClickListener(v -> onChangeExamDir());

        // 打开题库下载目录
        findViewById(R.id.btnOpenExamDir).setOnClickListener(v -> onOpenExamDir());

        // 存储权限（写入系统 Download 目录需要）
        findViewById(R.id.rowStoragePermission).setOnClickListener(v -> onStoragePermission());
        findViewById(R.id.btnGrantStorage).setOnClickListener(v -> onStoragePermission());

        // 导入时自动下载原题 docx
        SwitchCompat swAutoDownloadDocx = findViewById(R.id.swAutoDownloadDocx);
        swAutoDownloadDocx.setChecked(DataStore.isAutoDownloadDocx(this));
        swAutoDownloadDocx.setOnCheckedChangeListener((btn, checked) -> {
            DataStore.setAutoDownloadDocx(this, checked);
            showToast(checked
                    ? "导入真题时将自动下载原题 docx 到下载目录"
                    : "导入真题时只解析入库，不下载原题（仍可刷题）");
        });

        // 数量角标（错题本 / 收藏题）
        SwitchCompat swBadgeVisible = findViewById(R.id.swBadgeVisible);
        SeekBar sbBadgeAnim = findViewById(R.id.sbBadgeAnim);
        TextView tvBadgeAnimValue = findViewById(R.id.tvBadgeAnimValue);
        swBadgeVisible.setChecked(DataStore.isBadgeVisible(this));
        swBadgeVisible.setOnCheckedChangeListener((btn, checked) -> {
            DataStore.setBadgeVisible(this, checked);
            showToast(checked ? "已显示数量角标" : "已隐藏数量角标");
        });
        sbBadgeAnim.setProgress(badgeProgressOf(DataStore.getBadgeAnimDuration(this)));
        tvBadgeAnimValue.setText(DataStore.getBadgeAnimDuration(this) + " ms");
        sbBadgeAnim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int ms = badgeDurationOf(progress);
                tvBadgeAnimValue.setText(ms + " ms");
                DataStore.setBadgeAnimDuration(SettingsActivity.this, ms);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 开发者模式悬浮球：关闭后每个页面都不再显示悬浮球
        SwitchCompat swDevMode = findViewById(R.id.swDevMode);
        swDevMode.setChecked(DataStore.isDevModeEnabled(this));
        swDevMode.setOnCheckedChangeListener((btn, checked) -> {
            DataStore.setDevModeEnabled(this, checked);
            DevModeOverlay.onSettingChanged(this);
            showToast(checked ? "已开启开发者模式悬浮球" : "已关闭开发者模式悬浮球");
        });

        initAiConfig();
    }

    /** 角标动画时长 ↔ 滑动条进度（100~1000ms，步长 50）。 */
    private int badgeProgressOf(int ms) {
        int p = (ms - 100) / 50;
        return Math.max(0, Math.min(18, p));
    }

    private int badgeDurationOf(int progress) {
        return 100 + Math.max(0, Math.min(18, progress)) * 50;
    }

    /** 存储权限入口：未授予则申请，已授予则提示（默认下载目录在系统 Download 下）。 */
    private void onStoragePermission() {
        if (DataStore.hasStorageAccess(this)) {
            showToast("已具备存储权限，可直接下载到系统 Download 目录");
            return;
        }
        DataStore.requestStorageAccess(this);
    }

    /** 刷新下载目录与存储权限的显示状态。 */
    private void refreshExamDirAndPermission() {
        tvExamDir.setText(DataStore.getExamDir(this));
        boolean granted = DataStore.hasStorageAccess(this);
        TextView tvPerm = findViewById(R.id.tvStoragePermission);
        if (tvPerm != null) {
            tvPerm.setText(granted
                    ? "存储权限：已授予（默认下载到系统 Download 目录）"
                    : "存储权限：未授予（暂存到应用专属目录，点右侧授予后可写入 Download）");
        }
        TextView btnGrant = findViewById(R.id.btnGrantStorage);
        if (btnGrant != null) {
            btnGrant.setVisibility(granted ? android.view.View.GONE : android.view.View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统权限页返回后同步状态
        refreshExamDirAndPermission();
    }

    private void setupCollapse(int headerId, int bodyId) {
        android.view.View header = findViewById(headerId);
        android.view.View body = findViewById(bodyId);
        header.setOnClickListener(v -> {
            if (body.getVisibility() == android.view.View.GONE) {
                body.setVisibility(android.view.View.VISIBLE);
            } else {
                body.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void setupThemeColor() {
        // 点击标题行展开/收起色板
        findViewById(R.id.tvThemeColorTitle).setOnClickListener(v -> toggleThemePalette());
        findViewById(R.id.tvThemeColorSub).setOnClickListener(v -> toggleThemePalette());
        // 自定义颜色入口
        customColorBtn.setOnClickListener(v -> showCustomColorDialog());
        // 渲染色板
        for (int i = 0; i < ThemeManager.PRESET_COLORS.length; i++) {
            final int idx = i;
            android.widget.TextView dot = new android.widget.TextView(this);
            int size = (int) (40 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
            dot.setLayoutParams(lp);
            dot.setText("");
            dot.setOnClickListener(v -> {
                ThemeManager.applyPreset(this, idx);
                applyThemeColor();
                renderThemePalette();
            });
            themeColorPalette.addView(dot);
        }
        renderThemePalette();
        applyThemeColor();
    }

    /** 展开/收起主题色色板。 */
    private void toggleThemePalette() {
        themeColorExpanded = !themeColorExpanded;
        if (themeColorExpanded) {
            themeColorScroll.setVisibility(android.view.View.VISIBLE);
            customColorRow.setVisibility(android.view.View.VISIBLE);
        } else {
            themeColorScroll.setVisibility(android.view.View.GONE);
            customColorRow.setVisibility(android.view.View.GONE);
        }
    }

    /** 用当前主题色即时染色顶栏与设置页值文字。 */
    private void applyThemeColor() {
        int color = ThemeManager.getThemeColor(this);
        headerTopBar.setBackgroundColor(color);
        // 染色所有引用 @color/primary 的值文字
        int[] valueIds = {
                R.id.tvToastValue, R.id.tvAnimValue, R.id.tvWrongThresholdValue,
                R.id.tvDarkModeValue, R.id.tvAutoAiAnalysisModeValue, R.id.tvAiModelValue
        };
        for (int id : valueIds) {
            android.widget.TextView tv = findViewById(id);
            if (tv != null) tv.setTextColor(color);
        }
    }

    /** 渲染色板时给当前选中项加边框指示 + 显示自定义色块。 */
    private void renderThemePalette() {
        int current = ThemeManager.getThemeColorIndex(this);
        for (int i = 0; i < themeColorPalette.getChildCount(); i++) {
            android.view.View child = themeColorPalette.getChildAt(i);
            int size = (int) (40 * getResources().getDisplayMetrics().density);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(android.graphics.Color.parseColor(ThemeManager.PRESET_COLORS[i]));
            if (i == current) {
                gd.setStroke((int) (3 * getResources().getDisplayMetrics().density), android.graphics.Color.WHITE);
            } else {
                gd.setStroke((int) (1 * getResources().getDisplayMetrics().density), 0x33000000);
            }
            child.setBackground(gd);
        }
        // 显示自定义色块
        if (ThemeManager.isCustom(this)) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(ThemeManager.getThemeColor(this));
            gd.setStroke((int) (3 * getResources().getDisplayMetrics().density), android.graphics.Color.WHITE);
            customColorSwatch.setBackground(gd);
        } else {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(android.graphics.Color.TRANSPARENT);
            gd.setStroke((int) (1 * getResources().getDisplayMetrics().density), 0x33000000);
            customColorSwatch.setBackground(gd);
        }
    }

    /** 自定义色盘：hex 输入对话框。 */
    private void showCustomColorDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("#RRGGBB 或 RRGGBB");
        String cur = ThemeManager.getCustomColor(this);
        if (cur != null && !cur.isEmpty()) input.setText(cur);
        new AlertDialog.Builder(this)
                .setTitle("自定义主题色")
                .setMessage("输入十六进制颜色值，例如 #FF6600")
                .setView(input)
                .setPositiveButton("确定", (d, which) -> {
                    String hex = ThemeManager.normalizeHex(input.getText().toString());
                    if (hex == null) {
                        Toast.makeText(this, "颜色值格式无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ThemeManager.applyCustom(this, hex);
                    applyThemeColor();
                    renderThemePalette();
                    Toast.makeText(this, "已应用 " + hex, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
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

        refreshExamDirAndPermission();
        float fontSp = DataStore.getQuestionFontSp(this);
        tvFontSizeValue.setText(((int) fontSp) + " sp");
        sbFontSize.setProgress(Math.max(0, Math.min(12, (int) fontSp - 14)));
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
        // 其他存储卷（如 SD 卡）："1234-5678:xxx" → /storage/1234-5678/xxx
        int colon = docId.indexOf(':');
        if (colon > 0) {
            String vol = docId.substring(0, colon);
            String rel = docId.substring(colon + 1);
            java.io.File f = new java.io.File("/storage/" + vol, rel);
            if (f.exists()) return f.getAbsolutePath();
        }
        return null;
    }

    // ==================== AI 知识点识别配置 ====================
    private static final String[] AI_MODELS = {"operit", "openai", "custom"};
    private static final String[] AI_MODEL_LABELS = {"Operit AI", "OpenAI", "自定义"};

    private void initAiConfig() {
        findViewById(R.id.btnAiModelConfig).setOnClickListener(v -> {
            Intent i = new Intent(this, AiConfigActivity.class);
            PageTitle.put(i, (TextView) findViewById(R.id.labelAiModelConfig));
            startActivity(i);
        });
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
}