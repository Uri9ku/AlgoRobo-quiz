package com.algorobo.quiz;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ExamListActivity extends AppCompatActivity {

    /** 列表项：仅对应本地缓存中的 Paper。 */
    private static class Item {
        String key;        // 缓存 key
        String title;      // 展示标题
        String subtitle;   // 展示副标题（难度/日期）
        String sourceUrl;  // 原始 docx 下载地址（下载按钮用）
        String sourceName; // 原始 docx 文件名
        boolean downloaded; // 原题 docx 是否已下载到下载目录
        boolean downloading; // 正在下载（仅内存态，用于按钮/副标题提示）
        boolean parsing;     // 下载完成，正在解析
        int percent = -1;    // 下载进度 0~100

        int year;          // 年份（筛选用）
        int month;         // 月份（筛选用）
        String subject;    // 科目缩写（筛选用）
        int level;         // 等级（筛选用）
        boolean selected;  // 多选模式下的选中状态
    }

    private List<Item> items = new ArrayList<>();        // 全量数据
    private List<Item> filteredItems = new ArrayList<>(); // 过滤/搜索后的展示数据
    private ListView lvExams;
    private BaseAdapter adapter;

    // 工具栏控件
    private EditText etSearch;
    private TextView btnFilterYear, btnFilterMonth, btnFilterSubject, btnFilterLevel;
    private TextView tvFilterReset;
    private View llMultiActions;
    private CheckBox cbSelectAll;
    private TextView btnMultiDownload;
    private TextView tvMultiCancel;
    private TextView btnRemoveExams;
    private TextView fabAddExam;
    // FAB 菜单展开状态与动画
    private boolean fabMenuOpen = false;
    private PopupWindow fabPopup;
    private PopupWindow dropdownPopup;
    // 多选模式状态
    private boolean multiSelectMode = false;
    // 自动下载：正在跑、本次会话已尝试过的 key（避免失败后反复重试）
    private boolean autoDownloadRunning = false;
    private final Set<String> autoAttempted = new HashSet<>();

    // 筛选状态（-1 或空表示未选择）
    private int filterYear = -1;
    private int filterMonth = -1;
    private String filterSubject = null;
    private int filterLevel = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_list);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);
        PageTitle.apply(this, R.id.tvPageTitle);

        lvExams = findViewById(R.id.listExam);
        etSearch = findViewById(R.id.etExamSearch);
        btnFilterYear = findViewById(R.id.btnFilterYear);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterSubject = findViewById(R.id.btnFilterSubject);
        btnFilterLevel = findViewById(R.id.btnFilterLevel);
        tvFilterReset = findViewById(R.id.tvFilterReset);
        llMultiActions = findViewById(R.id.llMultiActions);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        btnMultiDownload = findViewById(R.id.btnMultiDownload);
        tvMultiCancel = findViewById(R.id.tvMultiCancel);
        btnRemoveExams = findViewById(R.id.btnRemoveExams);
        fabAddExam = findViewById(R.id.fabAddExam);

        TextView btnBack = findViewById(R.id.btnExamBack);
        btnBack.setOnClickListener(v -> finish());

        adapter = new BaseAdapter() {
            @Override public int getCount() { return filteredItems.size(); }
            @Override public Object getItem(int position) { return filteredItems.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_exam, parent, false);
                }
                Item item = filteredItems.get(position);
                TextView tvTitle = convertView.findViewById(R.id.tvExamTitle);
                TextView tvDoneCount = convertView.findViewById(R.id.tvExamDone);
                TextView btnDownload = convertView.findViewById(R.id.btnExamDownload);
                CheckBox cbSelect = convertView.findViewById(R.id.cbExamSelect);
                tvTitle.setText(item.title);
                tvDoneCount.setVisibility(View.VISIBLE);
                if (item.downloading) {
                    if (item.parsing) tvDoneCount.setText("解析中…");
                    else if (item.percent >= 0) tvDoneCount.setText("下载中 " + item.percent + "%");
                    else tvDoneCount.setText("下载中…");
                } else if (item.downloaded) {
                    int cnt = countDone(item.key);
                    tvDoneCount.setText(cnt > 0 ? cnt + " 题" : "已下载（未解析到题目）");
                } else {
                    // 只解析入库、未保存原文件的卷子依然可以刷题
                    int cnt = countDone(item.key);
                    tvDoneCount.setText(cnt > 0 ? cnt + " 题 · 未下载原文件" : "未下载");
                }

                // 多选模式：显示 CheckBox，隐藏下载按钮
                if (multiSelectMode) {
                    cbSelect.setVisibility(View.VISIBLE);
                    cbSelect.setChecked(item.selected);
                    cbSelect.setOnCheckedChangeListener(null); // 防重入
                    cbSelect.setOnCheckedChangeListener((btn, checked) -> {
                        item.selected = checked;
                        updateSelectAllState();
                    });
                    btnDownload.setVisibility(View.GONE);
                } else {
                    cbSelect.setVisibility(View.GONE);
                    if (item.sourceUrl != null && !item.sourceUrl.isEmpty()) {
                        btnDownload.setVisibility(View.VISIBLE);
                        if (item.downloading) {
                            btnDownload.setText("下载中…");
                        } else if (item.downloaded) {
                            btnDownload.setText("已下载");
                        } else {
                            btnDownload.setText("下载");
                        }
                        btnDownload.setOnClickListener(v -> onDownload(item));
                    } else {
                        btnDownload.setVisibility(View.GONE);
                    }
                }
                return convertView;
            }
        };
        lvExams.setAdapter(adapter);
        lvExams.setOnItemClickListener((parent, view, position, id) -> {
            Item item = filteredItems.get(position);
            if (multiSelectMode) {
                // 多选模式下点击条目切换选中
                item.selected = !item.selected;
                adapter.notifyDataSetChanged();
                updateSelectAllState();
                return;
            }
            onItemClick(item);
        });
        // 长按进入多选模式
        lvExams.setOnItemLongClickListener((parent, view, position, id) -> {
            if (items.isEmpty()) {
                Toast.makeText(this, "暂无真题", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (!multiSelectMode) {
                multiSelectMode = true;
                llMultiActions.setVisibility(View.VISIBLE);
                Item item = filteredItems.get(position);
                item.selected = true;
                adapter.notifyDataSetChanged();
                updateSelectAllState();
            }
            return true;
        });
        setupToolbar();
        // 仅加载本地缓存
        loadCached();
        // 进入页面时：自动补齐尚未下载的真题原卷（导入后回到本页即自动下载并解析）
        autoDownloadMissing();
    }

    private void setupToolbar() {
        // 搜索框实时过滤
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilterAndSearch(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            applyFilterAndSearch();
            return true;
        });
        // 筛选按钮
        btnFilterYear.setOnClickListener(v -> showYearPicker());
        btnFilterMonth.setOnClickListener(v -> showMonthPicker());
        btnFilterSubject.setOnClickListener(v -> showSubjectPicker());
        btnFilterLevel.setOnClickListener(v -> showLevelPicker());

        tvFilterReset.setOnClickListener(v -> {
            filterYear = -1;
            filterMonth = -1;
            filterSubject = null;
            filterLevel = -1;
            etSearch.setText("");
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
        // 多选操作栏
        cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            for (Item it : filteredItems) it.selected = checked;
            adapter.notifyDataSetChanged();
        });

        btnMultiDownload.setOnClickListener(v -> doBatchDownload());
        btnRemoveExams.setOnClickListener(v -> onRemoveSelected());
        tvMultiCancel.setOnClickListener(v -> {
            exitMultiSelectMode();
            adapter.notifyDataSetChanged();
        });

        // FAB：展开更多操作（打开目录 / 清除全部）
        fabAddExam.setOnClickListener(v -> showFabMenu());
    }

    /** FAB 展开菜单：打开下载目录 / 清除全部真题（PopupWindow + 旋转动画）。 */
    private void showFabMenu() {
        if (fabPopup != null && fabPopup.isShowing()) {
            closeFabMenu();
            return;
        }
        // 打开：FAB "+" 旋转 45° 变 "×"，菜单向上弹出
        openFabMenu();
    }
    /** 展开 FAB 菜单。 */
    private void openFabMenu() {
        fabMenuOpen = true;
        fabAddExam.animate().rotation(45f).setDuration(200).start();
        fabAddExam.setText("×");

        final String[] labels = {"在线导入真题", "打开真题下载目录", "清除全部真题"};
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_popup_menu);
        for (String label : labels) {
            TextView tv = (TextView) LayoutInflater.from(this).inflate(R.layout.item_dropdown, container, false);
            tv.setText(label);
            tv.setOnClickListener(v -> {
                closeFabMenu();
                String name = ((TextView) v).getText().toString();
                if (name.equals("在线导入真题")) {
                    importOnlineExams(name);
                } else if (name.equals("打开真题下载目录")) {
                    openExamDir();
                } else if (name.equals("清除全部真题")) {
                    onClearExams();
                }
            });
            container.addView(tv);
        }

        fabPopup = new PopupWindow(container,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        fabPopup.setElevation(12f);
        fabPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        // 先测量得到菜单尺寸，再锚定到 FAB 上方
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int menuW = container.getMeasuredWidth();
        int menuH = container.getMeasuredHeight();
        int[] loc = new int[2];
        fabAddExam.getLocationOnScreen(loc);
        int x = loc[0] + fabAddExam.getWidth() - menuW;
        int y = loc[1] - menuH;
        fabPopup.showAtLocation(fabAddExam, Gravity.NO_GRAVITY, x, y);
    }
    /** 收起 FAB 菜单。 */
    private void closeFabMenu() {
        fabMenuOpen = false;
        if (fabPopup != null && fabPopup.isShowing()) {
            fabPopup.dismiss();
        }
        fabPopup = null;
        fabAddExam.animate().rotation(0f).setDuration(200).start();
        fabAddExam.setText("+");
    }

    private void exitMultiSelectMode() {
        multiSelectMode = false;
        llMultiActions.setVisibility(View.GONE);
        for (Item it : items) it.selected = false;
        cbSelectAll.setChecked(false);
    }

    /** 打开下载目录（复用 DataStore 统一安全方案）。 */
    private void openExamDir() {
        DataStore.openExamDir(this, this);
    }

    /** 清除所有真题数据（保留布局与逻辑组件）。 */
    private void onClearExams() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("清除真题")
                .setMessage("确定清除本页面所有真题数据吗？此操作会删除本地缓存，无法撤销。")
                .setPositiveButton("清除", (d, w) -> {
                    RobotExamBank.clearCache(this);
                    items.clear();
                    filteredItems.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "已清除全部真题", Toast.LENGTH_SHORT).show();
                    // 清空后页面保持空置，不再自动抓取在线元数据填充列表
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ============ 在线导入真题 ============
    /** 进入在线导入真题页面（不再预加载，仓库数据由页面内按需懒加载）。 */
    private void importOnlineExams(String pageTitle) {
        android.content.Intent it = new android.content.Intent(this, ImportOnlineActivity.class);
        PageTitle.put(it, pageTitle);
        startActivityForResult(it, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            loadCached(); // 导入成功，刷新列表
            // 导入完成即自动下载并解析这些真题的原卷
            autoDownloadMissing();
        }
    }

    /** 移除选中真题（从真题库中删除其缓存）。 */
    private void onRemoveSelected() {
        final List<String> keys = new ArrayList<>();
        for (Item it : filteredItems) {
            // 未下载的登记项也应可移除
            if (it.selected && it.key != null) {
                keys.add(it.key);
            }
        }
        if (keys.isEmpty()) {
            Toast.makeText(this, "请先长按并勾选要移除的真题", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("移除真题库")
                .setMessage("确定从真题库中移除选中的 " + keys.size() + " 套真题吗？此操作仅删除本地缓存，不影响网站数据。")
                .setPositiveButton("移除", (d, w) -> {
                    RobotExamBank.removePapersByKeys(this, keys);
                    // 从列表移除对应项
                    items.removeIf(it -> it.key != null && keys.contains(it.key));
                    filteredItems.removeIf(it -> it.key != null && keys.contains(it.key));
                    adapter.notifyDataSetChanged();
                    exitMultiSelectMode();
                    Toast.makeText(this, "已移除 " + keys.size() + " 套真题", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSelectAllState() {
        if (filteredItems.isEmpty()) {
            cbSelectAll.setChecked(false);
            return;
        }
        boolean all = true;
        for (Item it : filteredItems) if (!it.selected) { all = false; break; }
        cbSelectAll.setOnCheckedChangeListener(null);
        cbSelectAll.setChecked(all);
        cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            for (Item it : filteredItems) it.selected = checked;
            adapter.notifyDataSetChanged();
        });
    }

    /** 读取缓存题数（仅针对已下载项）。 */
    private int countDone(String key) {
        if (key == null) return 0;
        List<Question> qs = RobotExamBank.getQuestionsFromCache(this, key);
        return qs == null ? 0 : qs.size();
    }

    /** 加载：仅展示本地缓存，不再在线抓取。 */
    private void loadCached() {
        items.clear();
        List<RobotExamBank.Paper> cached = RobotExamBank.getCachedPapersList(this);
        for (RobotExamBank.Paper p : cached) {
            Item it = new Item();
            it.key = p.key;
            it.title = buildExamTitle(p);
            it.subtitle = buildDifficulty(p);
            it.sourceUrl = p.sourceUrl;
            it.sourceName = p.sourceName;
            // 「下载/已下载」按钮反映原题 docx 是否已下载到本地下载目录
            it.downloaded = p.docxDownloaded;
            it.year = parseYearFromPeriod(p.period);
            it.month = parseMonthFromPeriod(p.period);
            it.subject = p.subject;
            it.level = p.level;
            items.add(it);
        }
        applyFilterAndSearch();
    }

    // ============ 筛选与搜索 ============

    private void applyFilterAndSearch() {
        String kw = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        filteredItems.clear();
        for (Item it : items) {
            // 筛选
            if (filterYear >= 0 && it.year != filterYear) continue;
            if (filterMonth >= 0 && it.month != filterMonth) continue;
            if (filterSubject != null && !filterSubject.isEmpty()
                    && !filterSubject.equals(it.subject)) continue;
            if (filterLevel >= 0 && it.level != filterLevel) continue;
            // 搜索
            if (!kw.isEmpty()) {
                if (it.title == null || !it.title.toLowerCase().contains(kw.toLowerCase())) continue;
            }
            filteredItems.add(it);
        }
        adapter.notifyDataSetChanged();
    }

    /** 搜索题目内容：遍历缓存 Paper 的 questions.stem（不要求已下载原文件）。 */
    private boolean itemContainsKeyword(Item it, String kw) {
        if (it.key != null) {
            List<Question> qs = RobotExamBank.getQuestionsFromCache(this, it.key);
            if (qs != null) {
                String k = kw.toLowerCase();
                for (Question q : qs) {
                    if (q != null && q.stem != null && q.stem.toLowerCase().contains(k)) return true;
                }
            }
        }
        return false;
    }

    private void showYearPicker() {
        // 从已有数据中收集年份
        Set<Integer> years = new java.util.TreeSet<>();
        for (Item it : items) if (it.year > 0) years.add(it.year);
        if (years.isEmpty()) { Toast.makeText(this, "暂无年份数据", Toast.LENGTH_SHORT).show(); return; }
        final Integer[] ys = years.toArray(new Integer[0]);
        final String[] labels = new String[ys.length + 1];
        labels[0] = "全部";
        for (int i = 0; i < ys.length; i++) labels[i + 1] = String.valueOf(ys[i]);
        showDropdown(btnFilterYear, labels, choice -> {
            filterYear = choice == 0 ? -1 : ys[choice - 1];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    private void showMonthPicker() {
        final String[] labels = {"全部", "3", "6", "9", "12", "1", "2", "4", "5", "7", "8", "10", "11"};
        final int[] months = {-1, 3, 6, 9, 12, 1, 2, 4, 5, 7, 8, 10, 11};
        showDropdown(btnFilterMonth, labels, choice -> {
            filterMonth = months[choice];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    private void showSubjectPicker() {
        final String[] codes = {null, "robot", "py", "c", "cpp", "gx"};
        final String[] labels = {"全部", "机器人", "Python", "C", "C++", "图形化"};
        showDropdown(btnFilterSubject, labels, choice -> {
            filterSubject = codes[choice];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    private void showLevelPicker() {
        final String[] labels = {"全部", "1", "2", "3", "4", "5", "6", "7", "8"};
        final int[] levels = {-1, 1, 2, 3, 4, 5, 6, 7, 8};
        showDropdown(btnFilterLevel, labels, choice -> {
            filterLevel = levels[choice];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    /** 在锚点 button 下方弹出下拉列表（PopupWindow + ListView），选中后回调。 */
    private void showDropdown(View anchor, String[] labels, Consumer<Integer> cb) {
        if (dropdownPopup != null && dropdownPopup.isShowing()) {
            dropdownPopup.dismiss();
            dropdownPopup = null;
        }
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.length; }
            @Override public Object getItem(int position) { return labels[position]; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = (TextView) LayoutInflater.from(ExamListActivity.this)
                            .inflate(R.layout.item_dropdown, parent, false);
                }
                tv.setText(labels[position]);
                return tv;
            }
        });
        lv.setOnItemClickListener((parent, view, position, id) -> {
            if (dropdownPopup != null && dropdownPopup.isShowing()) dropdownPopup.dismiss();
            dropdownPopup = null;
            cb.accept(position);
        });

        // 计算高度：最多显示 5 项，超过则滚动
        int maxItems = Math.min(labels.length, 5);
        int itemH = (int) (44 * getResources().getDisplayMetrics().density + 0.5f);
        int height = maxItems * itemH;

        // 计算宽度：自适应最长文本，但不小于锚点按钮宽度
        int maxTextW = 0;
        android.graphics.Paint p = new android.graphics.Paint();
        p.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        for (String s : labels) {
            maxTextW = Math.max(maxTextW, (int) p.measureText(s));
        }
        int padW = (int) (32 * getResources().getDisplayMetrics().density + 0.5f);
        int contentW = maxTextW + padW;
        int width = anchor.getWidth();

        dropdownPopup = new PopupWindow(lv, width, height, true);
        lv.setBackgroundResource(R.drawable.bg_popup_menu);
        dropdownPopup.setElevation(12f);
        dropdownPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        dropdownPopup.showAsDropDown(anchor, 0, 4);
    }
    private void refreshFilterButtonLabels() {
        btnFilterYear.setText(filterYear >= 0 ? String.valueOf(filterYear) : "年份");
        btnFilterMonth.setText(filterMonth >= 0 ? String.valueOf(filterMonth) : "月份");
        btnFilterSubject.setText(filterSubject != null ? subjectDisplayName(filterSubject) : "科目");
        btnFilterLevel.setText(filterLevel >= 0 ? String.valueOf(filterLevel) : "等级");
    }

    private String subjectDisplayName(String subject) {
        if (subject == null) return "科目";
        if (subject.equals("py")) return "Python";
        if (subject.equals("cpp")) return "C++";
        if (subject.equals("c")) return "C";
        if (subject.equals("gx")) return "图形化";
        if (subject.equals("robot")) return "机器人";
        return "科目";
    }

    // ============ 批量下载 ============
    private void doBatchDownload() {
        final List<Item> selected = new ArrayList<>();
        for (Item it : filteredItems) if (it.selected) selected.add(it);
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先勾选要下载的真题", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始下载所选 " + selected.size() + " 套真题…", Toast.LENGTH_SHORT).show();
        batchDownload(selected, 0, 0);
    }
    /** 递归逐个下载选中项：下载 GitHub 仓库原始 docx，并按中文规则重命名。 */
    private void batchDownload(final List<Item> selected, final int idx, final int success) {
        if (idx >= selected.size()) {
            // 全部完成
            Toast.makeText(this, "下载完成：成功 " + success + " 套，失败 " + (selected.size() - success) + " 套",
                    Toast.LENGTH_LONG).show();
            exitMultiSelectMode();
            return;
        }
        final Item item = selected.get(idx);
        if (item.key == null || item.sourceUrl == null || item.sourceUrl.isEmpty()) {
            Toast.makeText(this, "第 " + (idx + 1) + " 套暂无原始文件地址，已跳过", Toast.LENGTH_SHORT).show();
            batchDownload(selected, idx + 1, success);
            return;
        }
        // 生成中文文件名（若失败则回退原始文件名）
        final String cnName = buildSourceDocxName(item.sourceName);
        new Thread(() -> {
            int n = -1;
            try {
                n = RobotExamBank.downloadAndParse(this, item.key, cnName);
            } catch (Exception ignore) {
            }
            final int fn = n;
            runOnUiThread(() -> batchDownload(selected, idx + 1, success + (fn >= 0 ? 1 : 0)));
        }).start();
    }
    /** 根据原始 docx 文件名生成中文文件名：2026.6.CIE.Python.1 → 2026年6月CIE软件编程Python1级。 */
    private String buildSourceDocxName(String sourceName) {
        return RobotExamUpdater.buildChineseDocxName(sourceName);
    }
    /** 点击列表项：进入答题。只要解析入库了题目即可刷题，不要求下载原文件。 */
    private void onItemClick(Item item) {
        if (item.key == null) {
            Toast.makeText(this, "该套真题数据异常，请重新导入", Toast.LENGTH_SHORT).show();
            return;
        }
        if (countDone(item.key) <= 0) {
            if (item.downloaded) {
                Toast.makeText(this, "该套真题未解析到题目，可点「已下载」重新下载，或打开目录查看原文件",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "该套真题尚未解析到题目，请先点右侧「下载」获取原卷",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        startQuiz(item.key, item.title);
    }
    /** 下载按钮：未下载→直接下载；已下载→提示打开目录/再次下载。 */
    private void onDownload(Item item) {
        if (item.downloading) {
            Toast.makeText(this, "正在下载中，请稍候…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.downloaded) {
            // 自定义弹窗：AlertDialog 同时用 setItems + setMessage 会导致列表项被消息覆盖
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(20), dp(4), dp(20), dp(4));
            TextView info = new TextView(this);
            info.setText("原文件已保存到：\n" + RobotExamBank.examDirPath(this));
            info.setTextSize(13);
            info.setTextColor(getColor(R.color.text_sub));
            box.addView(info);
            final String[] actions = {"打开目录", "再次下载", "同步知识点"};
            final androidx.appcompat.app.AlertDialog dialog =
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("该真题已下载过")
                            .setView(box)
                            .setNegativeButton("取消", null)
                            .create();
            for (int i = 0; i < actions.length; i++) {
                final int which = i;
                TextView row = new TextView(this);
                row.setText(actions[i]);
                row.setTextSize(16);
                row.setTextColor(getColor(i == 2 ? R.color.primary : R.color.text_main));
                row.setPadding(0, dp(14), 0, dp(14));
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (which == 0) DataStore.openExamDir(this, this);
                    else if (which == 1) doDownload(item);
                    else syncKnowledge(item);
                });
                box.addView(row);
            }
            dialog.show();
            return;
        }
        doDownload(item);
    }

    /** dp → px（弹窗内边距用）。 */
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 从仓库 _meta/knowledge_map 同步该卷知识点（回填题目 + 写入本地缓存）。 */
    private void syncKnowledge(final Item item) {
        if (item.key == null) return;
        Toast.makeText(this, "正在同步知识点…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final int n = RobotExamBank.applyKnowledgeMap(this, item.key);
            runOnUiThread(() -> {
                if (n > 0) {
                    Toast.makeText(this, "已同步 " + n + " 题的知识点", Toast.LENGTH_LONG).show();
                } else if (n == 0) {
                    Toast.makeText(this, "该卷知识点映射为空", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "同步失败：无网络，或该卷尚未收录知识点映射", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
    /**
     * 自动补齐尚未下载的真题：导入后回到本页即自动抓取 GitHub 原卷并解析入库。
     * 逐个串行下载（避免并发过多），每完成一套即刷新列表；同一会话内失败不反复重试。
     */
    private void autoDownloadMissing() {
        if (autoDownloadRunning) return;
        // 设置里关闭「自动下载原题」后，不再自动抓取原卷（题目仍可用，只是不保存原文件）
        if (!DataStore.isAutoDownloadDocx(this)) return;
        final List<Item> pending = new ArrayList<>();
        for (Item it : items) {
            if (it.key != null && !it.downloaded && !it.downloading
                    && it.sourceUrl != null && !it.sourceUrl.isEmpty()
                    && !autoAttempted.contains(it.key)) {
                pending.add(it);
            }
        }
        if (pending.isEmpty()) return;
        autoDownloadRunning = true;
        for (Item it : pending) autoAttempted.add(it.key);
        Toast.makeText(this, "开始自动下载 " + pending.size() + " 套真题原卷…", Toast.LENGTH_SHORT).show();
        autoDownloadNext(pending, 0, 0);
    }

    private void autoDownloadNext(final List<Item> pending, final int idx, final int okCount) {
        if (idx >= pending.size() || isFinishing()) {
            autoDownloadRunning = false;
            if (okCount > 0) {
                Toast.makeText(this, "已自动下载并解析 " + okCount + " 套真题", Toast.LENGTH_LONG).show();
            }
            return;
        }
        final Item item = pending.get(idx);
        item.downloading = true;
        item.parsing = false;
        item.percent = 0;
        adapter.notifyDataSetChanged();
        final String cnName = buildSourceDocxName(item.sourceName);
        new Thread(() -> {
            int n = -1;
            try {
                n = RobotExamBank.downloadAndParse(ExamListActivity.this, item.key, cnName,
                        new RobotExamBank.ImportProgress() {
                            @Override public void onDownloadPercent(int percent) {
                                runOnUiThread(() -> {
                                    item.percent = percent;
                                    adapter.notifyDataSetChanged();
                                });
                            }
                            @Override public void onParsing() {
                                runOnUiThread(() -> {
                                    item.parsing = true;
                                    adapter.notifyDataSetChanged();
                                });
                            }
                        });
            } catch (Exception ignore) {
            }
            final int fn = n;
            runOnUiThread(() -> {
                item.downloading = false;
                item.parsing = false;
                item.downloaded = fn >= 0;
                adapter.notifyDataSetChanged();
                autoDownloadNext(pending, idx + 1, okCount + (fn >= 0 ? 1 : 0));
            });
        }).start();
    }

    private void doDownload(Item item) {
        if (item.key == null) {
            Toast.makeText(this, "该套真题数据异常，请重新导入", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item.sourceUrl == null || item.sourceUrl.isEmpty()) {
            Toast.makeText(this, "暂无原始文件地址，请重新导入该套真题", Toast.LENGTH_SHORT).show();
            return;
        }
        // 立即进入下载态，列表项上实时显示 下载中 x% → 解析中
        item.downloading = true;
        item.parsing = false;
        item.percent = 0;
        adapter.notifyDataSetChanged();
        final String cnName = buildSourceDocxName(item.sourceName);
        new Thread(() -> {
            int n = -1;
            String err = null;
            try {
                n = RobotExamBank.downloadAndParse(this, item.key, cnName,
                        new RobotExamBank.ImportProgress() {
                            @Override public void onDownloadPercent(int percent) {
                                runOnUiThread(() -> {
                                    item.percent = percent;
                                    adapter.notifyDataSetChanged();
                                });
                            }
                            @Override public void onParsing() {
                                runOnUiThread(() -> {
                                    item.parsing = true;
                                    adapter.notifyDataSetChanged();
                                });
                            }
                        });
            } catch (Exception e) {
                err = e.getMessage();
            }
            final int fn = n;
            final String ferr = err;
            runOnUiThread(() -> {
                item.downloading = false;
                item.parsing = false;
                if (ferr != null) {
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "下载失败：" + ferr, Toast.LENGTH_LONG).show();
                    return;
                }
                loadCached();
                if (fn > 0) {
                    Toast.makeText(this, "已下载并导入 " + fn + " 题", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "原卷已下载，但未解析到题目，可打开目录查看原文件",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ============ 标题与字段解析 ============

    private String subjectName(String subject) {
        if (subject == null) return "机器人等级考试";
        if (subject.equals("py")) return "软件编程 Python";
        if (subject.equals("cpp")) return "软件编程 C++";
        if (subject.equals("c")) return "软件编程 C语言";
        if (subject.equals("gx")) return "图形化 Scratch";
        return "机器人等级考试";
    }

    /** 统一命名模板：20xx年xx考试xx科目x月真题（优先自定义标题）。 */
    private String buildExamTitle(RobotExamBank.Paper p) {
        String custom = RobotExamBank.getCustomTitle(this, p.key);
        if (custom != null && !custom.trim().isEmpty()) return custom.trim();
        int year = parseYearFromPeriod(p.period);
        int month = parseMonthFromPeriod(p.period);
        String subject = subjectName(p.subject);
        int level = p.level;
        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(String.format("%04d年", year));
        if (month > 0) sb.append(String.format("%02d月", month));
        sb.append(subject);
        if (level > 0) sb.append(level).append("级");
        sb.append("真题");
        return sb.toString();
    }
    private String buildDifficulty(RobotExamBank.Paper p) {
        int level = p.level;
        return "难度：" + level + " 级";
    }

    /** 从 period（2026_06）解析年份。 */
    private int parseYearFromPeriod(String period) {
        if (period == null) return 0;
        String[] parts = period.split("_");
        if (parts.length == 0) return 0;
        try { return Integer.parseInt(parts[0]); } catch (Exception e) { return 0; }
    }

    /** 从 period（2026_06）解析月份。 */
    private int parseMonthFromPeriod(String period) {
        if (period == null) return 0;
        String[] parts = period.split("_");
        if (parts.length < 2) return 0;
        try { return Integer.parseInt(parts[1]); } catch (Exception e) { return 0; }
    }

    private void startQuiz(String key, String title) {
        Intent intent = new Intent(this, QuizActivity.class);
        intent.putExtra("source", "paper:" + key);
        intent.putExtra("random", false);
        intent.putExtra("paperTitle", title);
        startActivity(intent);
    }
}
