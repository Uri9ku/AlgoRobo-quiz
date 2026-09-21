package com.algorobo.quiz;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

/**
 * AI 模型与参数配置页面：支持多套配置的新建/切换/保存/删除/测试连接。
 * 参照图片 10376 的 UI 结构，沿用 XML 布局 activity_ai_config.xml。
 */
public class AiConfigActivity extends AppCompatActivity {

    private TextView tvCurrentConfig;
    private TextView tvProviderValue;
    private TextView tvAiConfigStatus;
    private EditText etEndpoint;
    private EditText etApiKey;
    private EditText etModels;
    private SwitchCompat swVision;
    private SwitchCompat swAudio;
    private ProgressBar pbAiConfig;

    private List<AiApi.Config> configs;
    private AiApi.Config current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_config);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);
        PageTitle.apply(this, R.id.tvPageTitle);

        tvCurrentConfig = findViewById(R.id.tvCurrentConfig);
        tvProviderValue = findViewById(R.id.tvProviderValue);
        tvAiConfigStatus = findViewById(R.id.tvAiConfigStatus);
        etEndpoint = findViewById(R.id.etEndpoint);
        etApiKey = findViewById(R.id.etApiKey);
        etModels = findViewById(R.id.etModels);
        swVision = findViewById(R.id.swVision);
        swAudio = findViewById(R.id.swAudio);
        pbAiConfig = findViewById(R.id.pbAiConfig);

        findViewById(R.id.btnBackAiConfig).setOnClickListener(v -> finish());
        findViewById(R.id.btnSelectConfig).setOnClickListener(v -> showConfigSelector());
        findViewById(R.id.btnSelectProvider).setOnClickListener(v -> showProviderDialog());
        findViewById(R.id.btnSelectModels).setOnClickListener(v -> showModelDialog());
        findViewById(R.id.btnTestConnection).setOnClickListener(v -> onTestConnection());
        findViewById(R.id.btnSaveConfig).setOnClickListener(v -> onSaveConfig());
        findViewById(R.id.btnNewConfig).setOnClickListener(v -> onNewConfig());
        findViewById(R.id.btnDeleteConfig).setOnClickListener(v -> onDeleteConfig());

        configs = DataStore.getAiConfigs(this);
        String curName = DataStore.getAiCurrentConfig(this);
        current = findByName(curName);
        if (current == null) current = configs.get(0);
        loadIntoUi(current);
    }

    private AiApi.Config findByName(String name) {
        for (AiApi.Config c : configs) {
            if (c.name != null && c.name.equals(name)) return c;
        }
        return null;
    }

    private void loadIntoUi(AiApi.Config cfg) {
        current = cfg;
        tvCurrentConfig.setText(cfg.name == null || cfg.name.isEmpty() ? "默认配置" : cfg.name);
        tvProviderValue.setText(cfg.provider == null || cfg.provider.isEmpty() ? "自定义" : cfg.provider);
        etEndpoint.setText(cfg.endpoint == null ? "" : cfg.endpoint);
        etApiKey.setText(cfg.apiKey == null ? "" : cfg.apiKey);
        etModels.setText(cfg.models == null ? "" : cfg.models);
        swVision.setChecked(cfg.vision);
        swAudio.setChecked(cfg.audio);
        tvAiConfigStatus.setText("");
        pbAiConfig.setVisibility(View.GONE);
    }

    private AiApi.Config collectFromUi() {
        String name = tvCurrentConfig.getText().toString();
        if (name == null || name.trim().isEmpty()) name = "默认配置";
        return new AiApi.Config(
                name.trim(),
                tvProviderValue.getText().toString(),
                etEndpoint.getText().toString().trim(),
                etApiKey.getText().toString().trim(),
                etModels.getText().toString().trim(),
                swVision.isChecked(),
                swAudio.isChecked()
        );
    }

    private void showConfigSelector() {
        if (configs == null || configs.isEmpty()) return;
        String[] names = new String[configs.size()];
        int selected = 0;
        for (int i = 0; i < configs.size(); i++) {
            names[i] = configs.get(i).name == null || configs.get(i).name.isEmpty()
                    ? "默认配置" : configs.get(i).name;
            if (current != null && current.name != null && current.name.equals(configs.get(i).name)) {
                selected = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("选择模型配置")
                .setSingleChoiceItems(names, selected, (d, which) -> {
                    current = configs.get(which);
                    DataStore.setAiCurrentConfig(this, current.name);
                    loadIntoUi(current);
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showProviderDialog() {
        String[] names = AiProviderCatalog.names();
        int curIdx = -1;
        String cur = tvProviderValue.getText().toString();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(cur)) { curIdx = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("API提供商")
                .setSingleChoiceItems(names, curIdx, (d, which) -> {
                    AiProviderCatalog.Provider p = AiProviderCatalog.find(names[which]);
                    tvProviderValue.setText(names[which]);
                    if (p != null) {
                        if (p.endpoint != null && !p.endpoint.isEmpty()
                                && (etEndpoint.getText() == null || etEndpoint.getText().toString().trim().isEmpty())) {
                            etEndpoint.setText(p.endpoint);
                        }
                        // 根据提供商能力更新识图/音频开关默认值（仅在用户尚未填写模型时同步）
                        if (etModels.getText() == null || etModels.getText().toString().trim().isEmpty()) {
                            swVision.setChecked(p.vision);
                            swAudio.setChecked(p.audio);
                        }
                    }
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showModelDialog() {
        String provider = tvProviderValue.getText().toString();
        AiProviderCatalog.Provider p = AiProviderCatalog.find(provider);
        if (p == null || p.models == null || p.models.length == 0) {
            showToast("该提供商暂无内置模型清单，请手动输入模型名称");
            return;
        }
        final String[] mnames = p.models;
        final boolean[] checked = new boolean[mnames.length];
        // 预勾选当前已输入的模型
        String cur = etModels.getText() == null ? "" : etModels.getText().toString();
        String[] existing = cur.split(",");
        for (String ex : existing) {
            ex = ex.trim();
            for (int i = 0; i < mnames.length; i++) {
                if (mnames[i].equals(ex)) checked[i] = true;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("选择模型（" + provider + "）")
                .setMultiChoiceItems(mnames, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定", (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mnames.length; i++) {
                        if (checked[i]) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(mnames[i]);
                        }
                    }
                    etModels.setText(sb.toString());
                    if (sb.length() > 0) {
                        swVision.setChecked(p.vision);
                        swAudio.setChecked(p.audio);
                    }
                })
                .setNeutralButton("查看官网", (d, w) -> {
                    if (p.baseUrl != null && !p.baseUrl.isEmpty()) {
                        try {
                            android.content.Intent it = new android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(p.baseUrl));
                            startActivity(it);
                        } catch (Exception e) {
                            showToast("无法打开官网链接");
                        }
                    } else {
                        showToast("该提供商暂无官网链接");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void onTestConnection() {
        final AiApi.Config cfg = collectFromUi();
        if (cfg == null || !cfg.isComplete()) {
            tvAiConfigStatus.setText("请先完善 API 端点、密钥与模型名称");
            tvAiConfigStatus.setTextColor(getColor(R.color.danger));
            return;
        }
        pbAiConfig.setVisibility(View.VISIBLE);
        tvAiConfigStatus.setText("正在测试连接…");
        tvAiConfigStatus.setTextColor(getColor(R.color.text_sub));

        new Thread(() -> {
            final AiApi.Result r = AiApi.testConnection(cfg);
            runOnUiThread(() -> {
                pbAiConfig.setVisibility(View.GONE);
                if (r.ok) {
                    tvAiConfigStatus.setText("连接成功：" + r.text);
                    tvAiConfigStatus.setTextColor(getColor(R.color.primary));
                    showToast("连接成功");
                    persistConfig(cfg);
                } else {
                    tvAiConfigStatus.setText("连接失败：" + r.text);
                    tvAiConfigStatus.setTextColor(getColor(R.color.danger));
                    showToast("连接失败：" + r.text);
                }
            });
        }).start();
    }

    private void onSaveConfig() {
        AiApi.Config cfg = collectFromUi();
        persistConfig(cfg);
        showToast("配置已保存");
    }

    private void persistConfig(AiApi.Config cfg) {
        boolean replaced = false;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).name != null && configs.get(i).name.equals(cfg.name)) {
                configs.set(i, cfg);
                replaced = true;
                break;
            }
        }
        if (!replaced) configs.add(cfg);
        DataStore.saveAiConfigs(this, configs);
        DataStore.setAiCurrentConfig(this, cfg.name);
        current = cfg;
        syncLegacyFields(cfg);
    }

    private void syncLegacyFields(AiApi.Config cfg) {
        DataStore.setAiModel(this, cfg.provider == null ? "custom" : mapProviderToLegacy(cfg.provider));
        DataStore.setAiBaseUrl(this, cfg.endpoint == null ? "" : cfg.endpoint);
        DataStore.setAiApiKey(this, cfg.apiKey == null ? "" : cfg.apiKey);
    }

    private String mapProviderToLegacy(String provider) {
        if ("Operit AI".equals(provider)) return "operit";
        if ("OpenAI".equals(provider)) return "openai";
        return "custom";
    }

    private void onNewConfig() {
        final EditText input = new EditText(this);
        input.setHint("请输入配置名称");
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("新建配置")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        showToast("名称不能为空");
                        return;
                    }
                    AiApi.Config exists = findByName(name);
                    if (exists != null) {
                        showToast("该名称已存在");
                        return;
                    }
                    AiApi.Config cfg = new AiApi.Config(name, "DeepSeek",
                            "https://api.deepseek.com/v1/chat/completions", "", "");
                    configs.add(cfg);
                    DataStore.saveAiConfigs(this, configs);
                    DataStore.setAiCurrentConfig(this, name);
                    loadIntoUi(cfg);
                    showToast("已新建配置 " + name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void onDeleteConfig() {
        if (configs.size() <= 1) {
            showToast("至少保留一套配置");
            return;
        }
        final AiApi.Config target = current;
        new AlertDialog.Builder(this)
                .setTitle("删除配置")
                .setMessage("确定删除「" + (target.name == null ? "默认配置" : target.name) + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    configs.remove(target);
                    DataStore.saveAiConfigs(this, configs);
                    AiApi.Config next = configs.get(0);
                    DataStore.setAiCurrentConfig(this, next.name);
                    loadIntoUi(next);
                    showToast("已删除");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
