package com.algorobo.quiz;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * 在线导入真题：以完整页面列表形式展示 GitHub release 中可导入的真题，
 * 支持搜索、年份/月份/科目/等级筛选、勾选多选，底栏「导入所选 / 取消」。
 */
public class ImportOnlineActivity extends AppCompatActivity {
    /** 候选条目：对应一个 GitHub release 资产。 */
    private static class Cand {
        RobotExamUpdater.ReleaseAsset asset;
        int year;
        int month;
        boolean selected;
    }

    private final List<Cand> all = new ArrayList<>();
    private final List<Cand> shown = new ArrayList<>();
    private ListView lv;
    private BaseAdapter adapter;

    private EditText etSearch;
    private TextView btnYear, btnMonth, btnSubject, btnLevel, tvReset;
    private TextView tvCount, btnCancel, btnConfirm, btnSelectAll;
    private PopupWindow dropdownPopup;

    private int filterYear = -1;
    private int filterMonth = -1;
    private String filterSubject = null;
    private int filterLevel = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_online);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

        lv = findViewById(R.id.listImportExam);
        etSearch = findViewById(R.id.etImportSearch);
        btnYear = findViewById(R.id.btnImportFilterYear);
        btnMonth = findViewById(R.id.btnImportFilterMonth);
        btnSubject = findViewById(R.id.btnImportFilterSubject);
        btnLevel = findViewById(R.id.btnImportFilterLevel);
        tvReset = findViewById(R.id.tvImportFilterReset);
        tvCount = findViewById(R.id.tvImportCount);
        btnCancel = findViewById(R.id.btnImportCancel);
        btnConfirm = findViewById(R.id.btnImportConfirm);
        btnSelectAll = findViewById(R.id.btnImportSelectAll);

        findViewById(R.id.btnImportBack).setOnClickListener(v -> finish());

        // 从 Intent 读取候选资产列表（由 ExamListActivity 传入）
        java.io.Serializable data = getIntent().getSerializableExtra("candidates");
        if (data instanceof List) {
            List<?> raw = (List<?>) data;
            for (Object o : raw) {
                if (o instanceof RobotExamUpdater.ReleaseAsset) {
                    RobotExamUpdater.ReleaseAsset ra = (RobotExamUpdater.ReleaseAsset) o;
                    Cand c = new Cand();
                    c.asset = ra;
                    c.year = parseYearFromPeriod(ra.period);
                    c.month = parseMonthFromPeriod(ra.period);
                    all.add(c);
                }
            }
        }

        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int position) { return shown.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(ImportOnlineActivity.this)
                            .inflate(R.layout.item_import_exam, parent, false);
                }
                Cand c = shown.get(position);
                RobotExamUpdater.ReleaseAsset ra = c.asset;
                TextView tvTitle = convertView.findViewById(R.id.tvImportTitle);
                TextView tvSub = convertView.findViewById(R.id.tvImportSub);
                CheckBox cb = convertView.findViewById(R.id.cbImportSelect);
                tvTitle.setText(ra.title != null ? ra.title : ra.fileName);
                String sub = ra.fileName;
                tvSub.setText(sub != null ? sub : "");
                // 先解绑旧 listener，防止 ListView 复用 convertView 时，
                // 上一个 item 的 OnCheckedChangeListener 仍绑定在本 CheckBox 上，
                // 导致 setChecked 触发旧 listener 串改上一个 Cand 的 selected 状态（勾选丢失）。
                cb.setOnCheckedChangeListener(null);
                cb.setChecked(c.selected);
                cb.setOnCheckedChangeListener((btn, checked) -> {
                    c.selected = checked;
                    updateCount();
                    updateSelectAllState();
                });
                return convertView;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Cand c = shown.get(position);
            c.selected = !c.selected;
            adapter.notifyDataSetChanged();
            updateCount();
            updateSelectAllState();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { apply(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnYear.setOnClickListener(v -> showYearPicker());
        btnMonth.setOnClickListener(v -> showMonthPicker());
        btnSubject.setOnClickListener(v -> showSubjectPicker());
        btnLevel.setOnClickListener(v -> showLevelPicker());
        tvReset.setOnClickListener(v -> {
            filterYear = -1; filterMonth = -1; filterSubject = null; filterLevel = -1;
            etSearch.setText("");
            refreshFilterLabels();
            apply();
        });

        btnCancel.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmImport());

        // 全选 / 取消全选 切换按钮
        btnSelectAll.setOnClickListener(v -> {
            boolean allSelected = !shown.isEmpty();
            for (Cand c : shown) {
                if (!c.selected) { allSelected = false; break; }
            }
            boolean target = !allSelected;
            for (Cand c : shown) c.selected = target;
            updateSelectAllState();
            adapter.notifyDataSetChanged();
            updateCount();
        });

        apply();
    }

    private void apply() {
        String kw = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        shown.clear();
        for (Cand c : all) {
            RobotExamUpdater.ReleaseAsset ra = c.asset;
            if (filterYear >= 0 && c.year != filterYear) continue;
            if (filterMonth >= 0 && c.month != filterMonth) continue;
            if (filterSubject != null && !filterSubject.isEmpty()
                    && !filterSubject.equals(ra.subject)) continue;
            if (filterLevel >= 0 && ra.level != filterLevel) continue;
            if (!kw.isEmpty()) {
                String t = ra.title != null ? ra.title : ra.fileName;
                if (t == null || !t.toLowerCase().contains(kw.toLowerCase())) continue;
            }
            shown.add(c);
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    private void updateCount() {
        int sel = 0;
        for (Cand c : all) if (c.selected) sel++;
        tvCount.setText("共 " + shown.size() + " 套，已选 " + sel + " 套");
        updateSelectAllState();
    }

    private void updateSelectAllState() {
        if (btnSelectAll == null) return;
        boolean allSelected = !shown.isEmpty();
        for (Cand c : shown) {
            if (!c.selected) { allSelected = false; break; }
        }
        btnSelectAll.setText(allSelected ? "取消全选" : "全选");
    }

    private void confirmImport() {
        final List<RobotExamUpdater.ReleaseAsset> chosen = new ArrayList<>();
        for (Cand c : all) if (c.selected) chosen.add(c.asset);
        if (chosen.isEmpty()) {
            Toast.makeText(this, "请先勾选要导入的真题", Toast.LENGTH_SHORT).show();
            return;
        }
        btnConfirm.setEnabled(false);
        Toast.makeText(this, "开始导入 " + chosen.size() + " 套真题…", Toast.LENGTH_SHORT).show();
        final File mediaDir = getFilesDir();
        new Thread(() -> {
            final List<RobotExamBank.Paper> imported = new ArrayList<>();
            final StringBuilder err = new StringBuilder();
            for (RobotExamUpdater.ReleaseAsset ra : chosen) {
                try {
                    RobotExamBank.Paper p = RobotExamUpdater.importPaperFromDocx(this, ra, mediaDir);
                    if (p != null) imported.add(p);
                } catch (Exception e) {
                    err.append(ra.fileName).append(": ").append(e.getMessage()).append("\n");
                }
            }
            int saved = 0;
            if (!imported.isEmpty()) {
                try {
                    java.util.Map<String, RobotExamBank.Paper> map = RobotExamUpdater.loadCache(this);
                    if (map == null) map = new java.util.LinkedHashMap<>();
                    for (RobotExamBank.Paper p : imported) {
                        map.put(p.key, p);
                        saved++;
                    }
                    RobotExamUpdater.saveCachePublic(this, map);
                } catch (Exception e) {
                    err.append("写入缓存失败: ").append(e.getMessage());
                }
            }
            final int finalSaved = saved;
            final String errMsg = err.toString();
            runOnUiThread(() -> {
                btnConfirm.setEnabled(true);
                if (finalSaved > 0) {
                    Toast.makeText(this, "成功导入 " + finalSaved + " 套真题", Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "导入失败：" + (errMsg.isEmpty() ? "未知错误" : errMsg),
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ===== 筛选下拉 =====
    private void showYearPicker() {
        Set<Integer> years = new TreeSet<>();
        for (Cand c : all) if (c.year > 0) years.add(c.year);
        if (years.isEmpty()) { Toast.makeText(this, "暂无年份数据", Toast.LENGTH_SHORT).show(); return; }
        final Integer[] ys = years.toArray(new Integer[0]);
        final String[] labels = new String[ys.length + 1];
        labels[0] = "全部年份";
        for (int i = 0; i < ys.length; i++) labels[i + 1] = ys[i] + "年";
        showDropdown(btnYear, labels, choice -> {
            filterYear = choice == 0 ? -1 : ys[choice - 1];
            refreshFilterLabels(); apply();
        });
    }
    private void showMonthPicker() {
        final String[] labels = {"全部月份", "3月", "6月", "9月", "12月", "1月", "2月", "4月", "5月", "7月", "8月", "10月", "11月"};
        final int[] months = {-1, 3, 6, 9, 12, 1, 2, 4, 5, 7, 8, 10, 11};
        showDropdown(btnMonth, labels, choice -> {
            filterMonth = months[choice];
            refreshFilterLabels(); apply();
        });
    }
    private void showSubjectPicker() {
        final String[] codes = {null, "robot", "py", "c", "cpp", "gx"};
        final String[] labels = {"全部科目", "机器人等级考试", "软件编程 Python", "软件编程 C语言", "软件编程 C++", "图形化 Scratch"};
        showDropdown(btnSubject, labels, choice -> {
            filterSubject = codes[choice];
            refreshFilterLabels(); apply();
        });
    }
    private void showLevelPicker() {
        final String[] labels = {"全部等级", "一级", "二级", "三级", "四级", "五级", "六级", "七级", "八级"};
        final int[] levels = {-1, 1, 2, 3, 4, 5, 6, 7, 8};
        showDropdown(btnLevel, labels, choice -> {
            filterLevel = levels[choice];
            refreshFilterLabels(); apply();
        });
    }
    private void showDropdown(View anchor, String[] labels, Consumer<Integer> cb) {
        if (dropdownPopup != null && dropdownPopup.isShowing()) {
            dropdownPopup.dismiss();
            dropdownPopup = null;
        }
        ListView dlv = new ListView(this);
        dlv.setDivider(null);
        dlv.setDividerHeight(0);
        dlv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.length; }
            @Override public Object getItem(int position) { return labels[position]; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = (TextView) LayoutInflater.from(ImportOnlineActivity.this)
                            .inflate(R.layout.item_dropdown, parent, false);
                }
                tv.setText(labels[position]);
                return tv;
            }
        });
        dlv.setOnItemClickListener((parent, view, position, id) -> {
            if (dropdownPopup != null && dropdownPopup.isShowing()) dropdownPopup.dismiss();
            dropdownPopup = null;
            cb.accept(position);
        });
        int maxItems = Math.min(labels.length, 5);
        int itemH = (int) (44 * getResources().getDisplayMetrics().density + 0.5f);
        int height = maxItems * itemH;
        android.graphics.Paint p = new android.graphics.Paint();
        p.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        int maxTextW = 0;
        for (String s : labels) maxTextW = Math.max(maxTextW, (int) p.measureText(s));
        int padW = (int) (32 * getResources().getDisplayMetrics().density + 0.5f);
        int width = Math.max(maxTextW + padW, anchor.getWidth());
        dropdownPopup = new PopupWindow(dlv, width, height, true);
        dlv.setBackgroundResource(R.drawable.bg_popup_menu);
        dropdownPopup.setElevation(12f);
        dropdownPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        dropdownPopup.showAsDropDown(anchor, 0, 4);
    }
    private void refreshFilterLabels() {
        btnYear.setText(filterYear >= 0 ? filterYear + "年" : "年份");
        btnMonth.setText(filterMonth >= 0 ? filterMonth + "月" : "月份");
        btnSubject.setText(filterSubject != null ? subjectDisplayName(filterSubject) : "科目");
        btnLevel.setText(filterLevel >= 0 ? filterLevel + "级" : "等级");
    }
    private String subjectDisplayName(String subject) {
        if (subject == null) return "科目";
        if (subject.equals("py")) return "Python";
        if (subject.equals("cpp")) return "C++";
        if (subject.equals("c")) return "C语言";
        if (subject.equals("gx")) return "Scratch";
        if (subject.equals("robot")) return "机器人";
        return "科目";
    }
    private int parseYearFromPeriod(String period) {
        if (period == null) return 0;
        String[] parts = period.split("_");
        if (parts.length == 0) return 0;
        try { return Integer.parseInt(parts[0]); } catch (Exception e) { return 0; }
    }
    private int parseMonthFromPeriod(String period) {
        if (period == null) return 0;
        String[] parts = period.split("_");
        if (parts.length < 2) return 0;
        try { return Integer.parseInt(parts[1]); } catch (Exception e) { return 0; }
    }
}
