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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
/**
 * 在线导入真题：以完整页面列表形式展示 GitHub release 中可导入的真题，
 * 支持搜索、年份/月份/科目/等级筛选、勾选多选，底栏「导入所选 / 取消」。
 *
 * 加载逻辑：进入页面后先展示动态的「真题来源仓库」Tab（不加载数据），
 * 用户点击某个仓库后才异步抓取该仓库的 release 列表并填充到真题控件。
 */
public class ImportOnlineActivity extends AppCompatActivity {
    /** 候选条目：对应一个 GitHub release 资产。 */
    private static class Cand {
        RobotExamUpdater.ReleaseAsset asset;
        int year;
        int month;
        boolean selected;
        // 导入过程状态（仅内存态）
        int percent = -1;      // -1 未开始；0~100 下载进度
        boolean parsing;       // 正在解析
        boolean done;          // 解析完成
        int questionCount;
        String failMsg;        // 非空表示失败
        boolean active;        // 是否为当前正在下载的那一条
    }
    private final List<Cand> all = new ArrayList<>();
    /** 候选条目缓存（key -> Cand）：切换来源仓库/全部分类时保留下载进度、解析状态与勾选态。 */
    private final java.util.Map<String, Cand> candCache = new java.util.HashMap<>();
    private final List<Cand> shown = new ArrayList<>();
    private ListView lv;
    private BaseAdapter adapter;
    private EditText etSearch;
    private TextView btnYear, btnMonth, btnSubject, btnLevel, tvReset;
    private TextView tvCount, btnCancel, btnConfirm, btnSelectAll;
    private TextView tvEmpty;
    private PopupWindow dropdownPopup;
    private LinearLayout llImportBottom;
    private LinearLayout llRepos;
    private final List<TextView> repoTabViews = new ArrayList<>();
    private String currentRepo = null;   // 当前选中的具体仓库名，null 表示尚未选择任何仓库
    private boolean isAllMode = false;     // 是否处于「全部」模式（点击了「全部」Tab）
    private boolean loading = false;     // 是否正在加载某个仓库
    private boolean selectMode = false;  // 是否处于选择模式（勾选框与底栏可见）
    private boolean importing = false;   // 是否正在导入（导入期间隐藏勾选框与底栏）
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
        PageTitle.apply(this, R.id.tvPageTitle);

        lv = findViewById(R.id.listImportExam);
        etSearch = findViewById(R.id.etImportSearch);
        btnYear = findViewById(R.id.btnImportFilterYear);
        btnMonth = findViewById(R.id.btnImportFilterMonth);
        btnSubject = findViewById(R.id.btnImportFilterSubject);
        btnLevel = findViewById(R.id.btnImportFilterLevel);
        tvReset = findViewById(R.id.tvImportFilterReset);
        tvCount = findViewById(R.id.tvImportCount);
        tvEmpty = findViewById(R.id.tvImportEmpty);
        lv.setEmptyView(tvEmpty);
        btnCancel = findViewById(R.id.btnImportCancel);
        btnConfirm = findViewById(R.id.btnImportConfirm);
        btnSelectAll = findViewById(R.id.btnImportSelectAll);
        llImportBottom = findViewById(R.id.llImportBottom);
        findViewById(R.id.btnImportBack).setOnClickListener(v -> finish());
        llRepos = findViewById(R.id.llImportRepos);
        // 动态构建真题来源仓库 Tab（不加载数据）
        buildRepoTabs();

        // 导入进度回调（进程级管理器 → 刷新列表；导入在后台继续，切回页面自动同步状态）
        importing = ImportManager.isRunning();
        ImportManager.setListener(() -> runOnUiThread(() -> {
            boolean running = ImportManager.isRunning();
            boolean justFinished = importing && !running;
            importing = running;
            if (adapter != null) adapter.notifyDataSetChanged();
            updateCount();
            if (justFinished) {
                setResult(RESULT_OK);
                Toast.makeText(this, "真题导入完成", Toast.LENGTH_LONG).show();
            }
        }));
        // Android 13+ 需要通知权限才能显示导入进度通知
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
            } catch (Exception ignore) {
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
                TextView tvRepo = convertView.findViewById(R.id.tvImportRepo);
                CheckBox cb = convertView.findViewById(R.id.cbImportSelect);
                // 勾选框可见性由选择模式驱动：导入过程中也允许继续勾选（只是隐藏底部工具栏）
                cb.setVisibility(selectMode ? View.VISIBLE : View.GONE);
                tvTitle.setText(ra.title != null ? ra.title : ra.fileName);
                String sub = ra.fileName;
                tvSub.setText(sub != null ? sub : "");
                tvRepo.setText(ra.repo != null ? ra.repo : "");
                // 先解绑旧 listener，防止 ListView 复用 convertView 时，
                // 上一个 item 的 OnCheckedChangeListener 仍绑定在本 CheckBox 上，
                // 导致 setChecked 触发旧 listener 串改上一个 Cand 的 selected 状态（勾选丢失）。
                cb.setOnCheckedChangeListener(null);
                cb.setChecked(c.selected);
                cb.setOnCheckedChangeListener((btn, checked) -> {
                    c.selected = checked;
                    selectMode = true; // 勾选即进入选择模式
                    updateCount();
                    updateSelectAllState();
                });
                // 右下角状态：下载百分比 → 解析中 → 解析完成 / 失败 / 已导入真题库
                ImportManager.State st = ImportManager.state(ra.key);
                TextView tvStatus = convertView.findViewById(R.id.tvImportStatus);
                String status = null;
                int statusColor = getColor(R.color.primary);
                if (st.failMsg != null) {
                    status = st.failMsg;
                    statusColor = getColor(R.color.danger);
                } else if (st.done) {
                    status = st.questionCount > 0 ? "解析完成" : "解析完成（0 题）";
                } else if (st.parsing) {
                    status = "解析中";
                } else if (st.active && st.percent >= 0) {
                    status = st.percent + "%";
                } else if (st.inBank) {
                    status = "已导入真题库";
                    statusColor = getColor(R.color.text_sub);
                }
                if (status == null) {
                    tvStatus.setVisibility(View.GONE);
                } else {
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText(status);
                    tvStatus.setTextColor(statusColor);
                }
                return convertView;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            Cand c = shown.get(position);
            c.selected = !c.selected;
            selectMode = true; // 条目点击勾选即进入选择模式
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

        btnCancel.setOnClickListener(v -> {
            for (Cand c : all) c.selected = false;
            selectMode = false; // 退出选择模式：隐藏勾选框和底栏
            adapter.notifyDataSetChanged();
            updateCount();
        });
        btnConfirm.setOnClickListener(v -> confirmImport());

        // 全选 / 取消全选 切换按钮
        btnSelectAll.setOnClickListener(v -> {
            boolean allSelected = !shown.isEmpty();
            for (Cand c : shown) {
                if (!c.selected) { allSelected = false; break; }
            }
            boolean target = !allSelected;
            for (Cand c : shown) c.selected = target;
            selectMode = true; // 进入选择模式：勾选框与底栏保持可见
            updateSelectAllState();
            adapter.notifyDataSetChanged();
            updateCount();
        });

        apply();
    }

    /** 动态构建真题来源仓库 Tab（只展示，不加载数据）。 */
    private void buildRepoTabs() {
        if (llRepos == null) return;
        llRepos.removeAllViews();
        repoTabViews.clear();
        // 「全部」Tab 置首，点击一次性加载所有仓库
        TextView allTab = new TextView(this);
        allTab.setText("全部");
        allTab.setTextSize(13);
        allTab.setGravity(Gravity.CENTER);
        allTab.setPadding(dp(16), dp(7), dp(16), dp(7));
        LinearLayout.LayoutParams lpAll = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAll.setMarginStart(dp(6));
        allTab.setLayoutParams(lpAll);
        styleRepoTab(allTab, false);
        allTab.setOnClickListener(v -> loadAllRepos());
        llRepos.addView(allTab);
        repoTabViews.add(allTab);
        for (String repo : RobotExamUpdater.GITHUB_REPOS) {
            TextView tab = new TextView(this);
            tab.setText(RobotExamUpdater.repoDisplayName(repo));
            tab.setTextSize(13);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(16), dp(7), dp(16), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(6));
            tab.setLayoutParams(lp);
            repoTabViews.add(tab);
            styleRepoTab(tab, false);
            tab.setOnClickListener(v -> loadRepo(repo));
            llRepos.addView(tab);
        }
        updateRepoSelection();
    }

    /** 设置仓库 Tab 的选中/未选中样式。 */
    private void styleRepoTab(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_category_selected);
            tab.setTextColor(0xFFFFFFFF);
        } else {
            tab.setBackgroundResource(R.drawable.bg_category_normal);
            tab.setTextColor(0xFF666666);
        }
    }

    /** 刷新仓库 Tab 选中态。 */
    private void updateRepoSelection() {
        // 索引0为「全部」Tab，其余对应 GITHUB_REPOS 各仓库
        for (int i = 0; i < repoTabViews.size(); i++) {
            boolean selected;
            if (i == 0) {
                selected = isAllMode;
            } else {
                String repo = RobotExamUpdater.GITHUB_REPOS[i - 1];
                selected = repo.equals(currentRepo);
            }
            styleRepoTab(repoTabViews.get(i), selected);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ImportManager.setListener(null);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 点击「全部」后一次性加载所有仓库的 release 列表并合并展示。 */
    private void loadAllRepos() {
        if (loading) return;
        isAllMode = true;
        currentRepo = null;
        updateRepoSelection();
        loading = true;
        showLoadingHint("正在加载全部仓库…");
        new Thread(() -> {
            final List<RobotExamUpdater.ReleaseAsset> allAssets = new ArrayList<>();
            String firstErr = null;
            for (String repo : RobotExamUpdater.GITHUB_REPOS) {
                try {
                    List<RobotExamUpdater.ReleaseAsset> assets =
                            RobotExamUpdater.fetchGitHubReleases(this, RobotExamUpdater.GITHUB_OWNER, repo);
                    if (assets != null) allAssets.addAll(assets);
                } catch (Exception e) {
                    if (firstErr == null) firstErr = e.getMessage();
                }
            }
            final String errMsg = firstErr;
            runOnUiThread(() -> {
                loading = false;
                if (allAssets.isEmpty()) {
                    all.clear();
                    showLoadingHint(errMsg != null ? ("加载失败：" + errMsg) : "暂无可导入的在线真题");
                    apply();
                    return;
                }
                java.util.Map<String, RobotExamBank.Paper> existing = RobotExamUpdater.loadCache(this);
                all.clear();
                for (RobotExamUpdater.ReleaseAsset ra : allAssets) {
                    if (existing != null && existing.containsKey(ra.key)) continue;
                    all.add(candFor(ra));
                }
                if (all.isEmpty()) {
                    showLoadingHint("所有在线真题均已在本地缓存");
                    apply();
                    return;
                }
                apply();
            });
        }).start();
    }

    /** 取得（或复用）候选条目：切换仓库/全部分类时保留下载进度、解析状态与勾选态。 */
    private Cand candFor(RobotExamUpdater.ReleaseAsset ra) {
        String key = ra.key != null ? ra.key : ra.fileName;
        Cand c = candCache.get(key);
        if (c == null) {
            c = new Cand();
            candCache.put(key, c);
        }
        c.asset = ra;
        c.year = parseYearFromPeriod(ra.period);
        c.month = parseMonthFromPeriod(ra.period);
        return c;
    }

    /** 点击仓库后异步抓取该仓库的 release 列表并展示。 */
    private void loadRepo(final String repo) {
        if (repo == null || loading) return;
        // 若已加载过同一仓库，则仅切换选中态，不重复抓取
        if (repo.equals(currentRepo)) return;
        currentRepo = repo;
        isAllMode = false;
        updateRepoSelection();
        loading = true;
        showLoadingHint("正在加载「" + RobotExamUpdater.repoDisplayName(repo) + "」仓库…");
        new Thread(() -> {
            final List<RobotExamUpdater.ReleaseAsset> assets;
            try {
                assets = RobotExamUpdater.fetchGitHubReleases(this, RobotExamUpdater.GITHUB_OWNER, repo);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    showLoadingHint("加载失败：" + e.getMessage());
                });
                return;
            }
            runOnUiThread(() -> {
                loading = false;
                if (assets == null || assets.isEmpty()) {
                    all.clear();
                    showLoadingHint("该仓库暂无可导入的在线真题");
                    apply();
                    return;
                }
                // 已在本地的也保留展示，右下角标「已导入真题库」
                java.util.Map<String, RobotExamBank.Paper> existing = RobotExamUpdater.loadCache(this);
                all.clear();
                for (RobotExamUpdater.ReleaseAsset ra : assets) {
                    Cand c = candFor(ra);
                    RobotExamBank.Paper p = existing == null ? null : existing.get(ra.key);
                    if (p != null && p.questions != null && !p.questions.isEmpty()) {
                        ImportManager.state(ra.key).inBank = true;
                    }
                    all.add(c);
                }
                apply();
            });
        }).start();
    }

    /** 在列表区显示加载/提示文案（列表为空时覆盖显示）。 */
    private void showLoadingHint(String msg) {
        if (tvEmpty != null) tvEmpty.setText(msg);
        if (adapter != null) adapter.notifyDataSetChanged();
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
        tvCount.setText("共 " + shown.size() + " 套\n已选 " + sel + " 套");
        // 底栏可见性由选择模式驱动：取消全选后仍显示，点击「取消」后隐藏；导入期间隐藏
        if (llImportBottom != null)
            llImportBottom.setVisibility((selectMode && !importing) ? View.VISIBLE : View.GONE);
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
        if (ImportManager.isRunning()) return;
        final List<RobotExamUpdater.ReleaseAsset> chosen = new ArrayList<>();
        for (Cand c : all) if (c.selected) chosen.add(c.asset);
        if (chosen.isEmpty()) {
            Toast.makeText(this, "请先勾选要导入的真题", Toast.LENGTH_SHORT).show();
            return;
        }
        // 导入期间：底部工具栏隐藏（避免重复触发导入），但列表仍可勾选（可先选好下一批）
        importing = true;
        for (RobotExamUpdater.ReleaseAsset ra : chosen) {
            ImportManager.State st = ImportManager.state(ra.key);
            st.percent = 0;
            st.parsing = false;
            st.done = false;
            st.active = true;
            st.failMsg = null;
            st.questionCount = 0;
        }
        adapter.notifyDataSetChanged();
        updateCount();
        Toast.makeText(this, "开始导入 " + chosen.size() + " 套真题（并发下载解析）…", Toast.LENGTH_SHORT).show();
        ImportManager.start(this, chosen, DataStore.isAutoDownloadDocx(this));
    }

    // ===== 筛选下拉 =====
    private void showYearPicker() {
        Set<Integer> years = new TreeSet<>();
        for (Cand c : all) if (c.year > 0) years.add(c.year);
        // 需求2：即使无年份数据也允许下拉，仅显示「全部」（或「无年份数据」提示项）
        final Integer[] ys = years.toArray(new Integer[0]);
        final String[] labels;
        if (ys.length == 0) {
            labels = new String[]{"全部"};
        } else {
            labels = new String[ys.length + 1];
            labels[0] = "全部";
            for (int i = 0; i < ys.length; i++) labels[i + 1] = String.valueOf(ys[i]);
        }
        showDropdown(btnYear, labels, choice -> {
            filterYear = (ys.length == 0 || choice == 0) ? -1 : ys[choice - 1];
            refreshFilterLabels(); apply();
        });
    }
    private void showMonthPicker() {
        final String[] labels = {"全部", "3", "6", "9", "12", "1", "2", "4", "5", "7", "8", "10", "11"};
        final int[] months = {-1, 3, 6, 9, 12, 1, 2, 4, 5, 7, 8, 10, 11};
        showDropdown(btnMonth, labels, choice -> {
            filterMonth = months[choice];
            refreshFilterLabels(); apply();
        });
    }
    private void showSubjectPicker() {
        final String[] codes = {null, "robot", "py", "c", "cpp", "gx"};
        final String[] labels = {"全部", "机器人", "Python", "C", "C++", "图形化"};
        showDropdown(btnSubject, labels, choice -> {
            filterSubject = codes[choice];
            refreshFilterLabels(); apply();
        });
    }
    private void showLevelPicker() {
        final String[] labels = {"全部", "1", "2", "3", "4", "5", "6", "7", "8"};
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
        float density = getResources().getDisplayMetrics().density;
        int textW = maxTextW + (int) (32 * density + 0.5f); // 左右 padding 各 16dp
        int anchorW = anchor.getWidth();
        int width = Math.max(anchorW, Math.max(textW, (int) (120 * density + 0.5f)));
        dropdownPopup = new PopupWindow(dlv, width, height, true);
        dlv.setBackgroundResource(R.drawable.bg_popup_menu);
        dropdownPopup.setElevation(12f);
        dropdownPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        dropdownPopup.showAsDropDown(anchor, 0, 4);
    }
    private void refreshFilterLabels() {
        btnYear.setText(filterYear >= 0 ? String.valueOf(filterYear) : "年份");
        btnMonth.setText(filterMonth >= 0 ? String.valueOf(filterMonth) : "月份");
        btnSubject.setText(filterSubject != null ? subjectDisplayName(filterSubject) : "科目");
        btnLevel.setText(filterLevel >= 0 ? String.valueOf(filterLevel) : "等级");
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
