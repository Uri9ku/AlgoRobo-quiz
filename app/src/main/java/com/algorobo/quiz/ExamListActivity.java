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
        boolean downloaded; // 是否已缓存

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

    // 筛选状态（-1 或空表示未选择）
    private int filterYear = -1;
    private int filterMonth = -1;
    private String filterSubject = null;
    private int filterLevel = -1;

    // 在线导入题库：GitHub 仓库源（release 资产为题库文件）
    private static final String GITHUB_OWNER = "Uri9ku";
    private static final String GITHUB_REPO = "CIE-ETQ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_list);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

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
                if (item.downloaded) {
                    tvDoneCount.setVisibility(View.VISIBLE);
                    int cnt = countDone(item.key);
                    tvDoneCount.setText(cnt > 0 ? cnt + " 题" : "");
                } else {
                    tvDoneCount.setVisibility(View.GONE);
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
                        if (item.downloaded) {
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
                if (label.equals("在线导入真题")) {
                    importOnlineExams();
                } else if (label.equals("打开真题下载目录")) {
                    openExamDir();
                } else if (label.equals("清除全部真题")) {
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
    /** 从 GitHub release 抓取可导入真题列表，用户勾选后下载解析并入本地缓存。 */
    private void importOnlineExams() {
        Toast.makeText(this, "正在获取在线真题列表…", Toast.LENGTH_SHORT).show();
        final java.util.Map<String, RobotExamBank.Paper> existing =
                RobotExamUpdater.loadCache(this);
        new Thread(() -> {
            final java.util.List<RobotExamUpdater.ReleaseAsset> assets;
            try {
                assets = RobotExamUpdater.fetchGitHubReleases(this, GITHUB_OWNER, GITHUB_REPO);
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "获取在线列表失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            runOnUiThread(() -> {
                if (assets == null || assets.isEmpty()) {
                    Toast.makeText(this, "暂无可导入的在线真题", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 过滤掉已存在本地缓存的真题
                final java.util.List<RobotExamUpdater.ReleaseAsset> candidates = new ArrayList<>();
                for (RobotExamUpdater.ReleaseAsset ra : assets) {
                    if (existing != null && existing.containsKey(ra.key)) continue;
                    candidates.add(ra);
                }
                if (candidates.isEmpty()) {
                    Toast.makeText(this, "所有在线真题均已在本地缓存", Toast.LENGTH_SHORT).show();
                    return;
                }
                android.content.Intent it = new android.content.Intent(this, ImportOnlineActivity.class);
                it.putExtra("candidates", (java.io.Serializable) candidates);
                startActivityForResult(it, 1001);
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            loadCached(); // 导入成功，刷新列表
        }
    }

    /** 移除选中真题（从真题库中删除其缓存）。 */
    private void onRemoveSelected() {
        final List<String> keys = new ArrayList<>();
        for (Item it : filteredItems) {
            if (it.selected && it.downloaded && it.key != null) {
                keys.add(it.key);
            }
        }
        if (keys.isEmpty()) {
            Toast.makeText(this, "请先长按并勾选要移除的已下载真题", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("移除真题库")
                .setMessage("确定从真题库中移除选中的 " + keys.size() + " 套真题吗？此操作仅删除本地缓存，不影响网站数据。")
                .setPositiveButton("移除", (d, w) -> {
                    RobotExamBank.removePapersByKeys(this, keys);
                    // 从列表移除对应项
                    items.removeIf(it -> it.downloaded && it.key != null && keys.contains(it.key));
                    filteredItems.removeIf(it -> it.downloaded && it.key != null && keys.contains(it.key));
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
            it.downloaded = true;
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

    /** 搜索题目内容：遍历缓存 Paper 的 questions.stem。 */
    private boolean itemContainsKeyword(Item it, String kw) {
        if (it.downloaded && it.key != null) {
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
        labels[0] = "全部年份";
        for (int i = 0; i < ys.length; i++) labels[i + 1] = ys[i] + "年";
        showDropdown(btnFilterYear, labels, choice -> {
            filterYear = choice == 0 ? -1 : ys[choice - 1];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    private void showMonthPicker() {
        final String[] labels = {"全部月份", "3月", "6月", "9月", "12月", "1月", "2月", "4月", "5月", "7月", "8月", "10月", "11月"};
        final int[] months = {-1, 3, 6, 9, 12, 1, 2, 4, 5, 7, 8, 10, 11};
        showDropdown(btnFilterMonth, labels, choice -> {
            filterMonth = months[choice];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    private void showSubjectPicker() {
        final String[] codes = {null, "robot", "py", "c", "cpp", "gx"};
        final String[] labels = {"全部科目", "机器人等级考试", "软件编程 Python", "软件编程 C语言", "软件编程 C++", "图形化 Scratch"};
        showDropdown(btnFilterSubject, labels, choice -> {
            filterSubject = codes[choice];
            refreshFilterButtonLabels();
            applyFilterAndSearch();
        });
    }
    private void showLevelPicker() {
        final String[] labels = {"全部等级", "一级", "二级", "三级", "四级", "五级", "六级", "七级", "八级"};
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
        int width = Math.max(contentW, anchor.getWidth());

        dropdownPopup = new PopupWindow(lv, width, height, true);
        lv.setBackgroundResource(R.drawable.bg_popup_menu);
        dropdownPopup.setElevation(12f);
        dropdownPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        dropdownPopup.showAsDropDown(anchor, 0, 4);
    }
    private void refreshFilterButtonLabels() {
        btnFilterYear.setText(filterYear >= 0 ? filterYear + "年" : "年份");
        btnFilterMonth.setText(filterMonth >= 0 ? filterMonth + "月" : "月份");
        btnFilterSubject.setText(filterSubject != null ? subjectDisplayName(filterSubject) : "科目");
        btnFilterLevel.setText(filterLevel >= 0 ? filterLevel + "级" : "等级");
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
        // 解析下载地址：优先取已缓存 Paper 的 sourceUrl
        String url = item.sourceUrl;
        String name = item.sourceName;
        if (item.downloaded && item.key != null) {
            RobotExamBank.Paper p = RobotExamBank.getPaper(this, item.key);
            if (p != null) {
                url = p.sourceUrl != null ? p.sourceUrl : url;
                name = p.sourceName != null ? p.sourceName : name;
            }
        }
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "第 " + (idx + 1) + " 套暂无原始文件地址，已跳过", Toast.LENGTH_SHORT).show();
            batchDownload(selected, idx + 1, success);
            return;
        }
        final String fUrl = url;
        // 生成中文文件名（若失败则回退原始文件名）
        final String cnName = buildSourceDocxName(name);
        final String saveName = cnName != null ? cnName : name;
        new Thread(() -> {
            String saved = RobotExamBank.downloadSourceDocx(this, makePaper(fUrl, saveName));
            runOnUiThread(() -> batchDownload(selected, idx + 1, success + (saved != null ? 1 : 0)));
        }).start();
    }
    /** 根据原始 docx 文件名生成中文文件名：2026.6.CIE.Python.1 → 2026年6月CIE软件编程Python1级。 */
    private String buildSourceDocxName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) return null;
        // 去掉扩展名与路径
        String base = sourceName;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        // 以 . 切分段落：年 . 月 . CIE . 科目 . 等级
        String[] seg = base.split("\\.");
        if (seg.length < 2) return null;
        int year = 0, month = 0;
        try { year = Integer.parseInt(seg[0]); } catch (Exception e) { }
        try { month = seg.length >= 2 ? Integer.parseInt(seg[1]) : 0; } catch (Exception e) { }
        String subject = RobotExamUpdater.parseSubjectFromFileName(sourceName);
        int level = RobotExamUpdater.parseLevelFromFileName(sourceName);
        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(year).append("年");
        if (month > 0) sb.append(month).append("月");
        sb.append("CIE软件编程");
        if (subject == null) subject = "robot";
        switch (subject) {
            case "py": sb.append("Python"); break;
            case "c": sb.append("C语言"); break;
            case "cpp": sb.append("C++"); break;
            case "gx": sb.append("图形化"); break;
            case "robot": sb.append("机器人"); break;
            default: sb.append(subject); break;
        }
        if (level > 0) sb.append(level).append("级");
        return sb.toString();
    }
    /** 点击列表项：进入答题。 */
    private void onItemClick(Item item) {
        if (item.downloaded && item.key != null) {
            startQuiz(item.key, item.title);
            return;
        }
        Toast.makeText(this, "无法获取该期真题地址", Toast.LENGTH_SHORT).show();
    }
    /** 下载按钮：下载该期对应的原始 docx 文件。 */
    private void onDownload(Item item) {
        // 已下载的项再次点击弹二次确认
        if (item.downloaded) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("重新下载")
                    .setMessage("该真题已下载过，是否重新下载？")
                    .setPositiveButton("重新下载", (d, w) -> doDownload(item))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        doDownload(item);
    }
    private void doDownload(Item item) {
        String url = item.sourceUrl;
        String name = item.sourceName;
        if (item.downloaded && item.key != null) {
            RobotExamBank.Paper p = RobotExamBank.getPaper(this, item.key);
            if (p != null) {
                url = p.sourceUrl != null ? p.sourceUrl : url;
                name = p.sourceName != null ? p.sourceName : name;
            }
        }
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "暂无原始文件地址，请先点击进入该期真题", Toast.LENGTH_SHORT).show();
            return;
        }
        final String fUrl = url;
        final String fName = name;
        Toast.makeText(this, "开始下载" + (fName == null ? "" : " " + fName), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String saved = RobotExamBank.downloadSourceDocx(this, makePaper(fUrl, fName));
            runOnUiThread(() -> {
                if (saved != null) {
                    Toast.makeText(this, "已保存到下载目录", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private RobotExamBank.Paper makePaper(String url, String name) {
        RobotExamBank.Paper p = new RobotExamBank.Paper();
        p.sourceUrl = url;
        p.sourceName = name;
        return p;
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
