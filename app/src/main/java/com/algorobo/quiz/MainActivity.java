package com.algorobo.quiz;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import com.algorobo.quiz.ExamCategoryCatalog.Category;
import com.algorobo.quiz.ExamCategoryCatalog.ExamType;
import com.algorobo.quiz.ExamCategoryCatalog.TreeNode;

public class MainActivity extends AppCompatActivity {

    private int currentTypeIndex = 0;
    private int currentCategoryIndex = 0;
    private String currentCategoryName;
    private final Set<String> expandedPaths = new HashSet<>();

    private TextView tvExamTypeName;
    private RecyclerView rvCategories;
    private LinearLayout llGreenContainer;
    /** 角标上次显示的数量（-1 表示尚未初始化）：用于判断变大/变小以播放滑动动画。 */
    private int lastWrongCount = -1;
    private int lastFavoriteCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 刘海屏适配：状态栏延伸/染色（首页无主题色顶栏，仅适配刘海）
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopInset(this, R.id.headerContainer);

        tvExamTypeName = findViewById(R.id.tvExamTypeName);
        rvCategories = findViewById(R.id.rvCategories);
        llGreenContainer = findViewById(R.id.llGreenContainer);

        // 考试类型下拉选择
        findViewById(R.id.rowExamType).setOnClickListener(v -> showExamTypePopup());

        // 功能卡片：进入页面的标题与该入口控件的名称保持一致（PageTitle 动态传递）
        findViewById(R.id.cardPractice).setOnClickListener(v ->
            openPage(ExamListActivity.class, R.id.labelPractice));
        findViewById(R.id.modeSequence).setOnClickListener(v ->
            Toast.makeText(this, "题库练习待完善：用于真题与自定义题目混用刷题", Toast.LENGTH_SHORT).show());
        findViewById(R.id.modeRandom).setOnClickListener(v ->
            Toast.makeText(this, "随机练习待完善：选择知识点后随机刷题", Toast.LENGTH_SHORT).show());

        findViewById(R.id.cardWrongBook).setOnClickListener(v ->
            startQuizBySource("wrong", R.id.labelWrongBook));
        findViewById(R.id.cardFavorite).setOnClickListener(v ->
            startQuizBySource("favorite", R.id.labelFavorite));
        findViewById(R.id.cardStatistics).setOnClickListener(v ->
            openPage(StatisticsActivity.class, R.id.labelStatistics));
        findViewById(R.id.cardCalendar).setOnClickListener(v ->
            openPage(CalendarActivity.class, R.id.labelCalendar));

        findViewById(R.id.cardCustomBank).setOnClickListener(v ->
            openPage(CustomBankActivity.class, R.id.labelCustomBank));

        findViewById(R.id.cardKnowledge).setOnClickListener(v ->
            openPage(KnowledgeActivity.class, R.id.labelKnowledge));

        // 设置：进入设置页
        findViewById(R.id.cardFabSettings).setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            PageTitle.put(i, "设置");
            startActivity(i);
        });

        bindBadges();
        bindResume();

        // 初始化考试类型与类目
        ExamType t0 = ExamCategoryCatalog.getByIndex(currentTypeIndex);
        currentCategoryName = t0.categories.isEmpty() ? "" : t0.categories.get(0).name;
        renderExamType(currentTypeIndex);
        renderCategories();
        renderGreenContainer();
        // 后台刷新《机器人等级考试备考目录.md》（题库仓库 _meta 下），目录更新后自动重新分级
        RobotCatalog.refreshAsync(this, changed -> {
            if (changed) runOnUiThread(this::renderGreenContainer);
        });
    }

    // ===== 顶部考试类型下拉框 =====
    private void showExamTypePopup() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(6), dp(6), dp(6), dp(6));
        menu.setBackgroundResource(R.drawable.bg_popup_menu);

        for (int i = 0; i < ExamCategoryCatalog.TYPES.size(); i++) {
            final int idx = i;
            ExamType t = ExamCategoryCatalog.TYPES.get(i);
            TextView item = new TextView(this);
            item.setText(t.name);
            item.setTextSize(15);
            item.setTextColor(getColor(R.color.text_main));
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            item.setGravity(Gravity.CENTER_VERTICAL);
            if (i == currentTypeIndex) {
                item.setTextColor(getColor(R.color.primary));
                item.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            item.setOnClickListener(v -> {
                currentTypeIndex = idx;
                currentCategoryIndex = 0;
                renderExamType(idx);
                renderCategories();
                renderGreenContainer();
                if (popup != null) popup.dismiss();
            });
            menu.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        int width = dp(260);
        popup = new PopupWindow(menu, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(getDrawable(R.drawable.bg_popup_menu));
        popup.showAsDropDown(findViewById(R.id.rowExamType), 0, dp(4));
    }
    private PopupWindow popup;

    private void renderExamType(int index) {
        ExamType t = ExamCategoryCatalog.getByIndex(index);
        tvExamTypeName.setText(t.name);
    }

    // ===== 类目标签（横向滚动，可长按拖拽排序） =====
    private void renderCategories() {
        ExamType t = ExamCategoryCatalog.getByIndex(currentTypeIndex);
        List<Category> categories = new ArrayList<>(t.categories);
        List<String> savedOrder = DataStore.getCategoryOrder(this, t.name);
        if (!savedOrder.isEmpty()) {
            categories.sort((a, b) -> {
                int ia = savedOrder.indexOf(a.name);
                int ib = savedOrder.indexOf(b.name);
                if (ia < 0) ia = categories.indexOf(a);
                if (ib < 0) ib = categories.indexOf(b);
                return Integer.compare(ia, ib);
            });
        }
        final List<Category> ordered = categories;
        currentCategoryName = ordered.get(Math.min(currentCategoryIndex, ordered.size() - 1)).name;
        CategoryAdapter adapter = new CategoryAdapter(ordered);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
            }
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                int from = source.getAdapterPosition();
                int to = target.getAdapterPosition();
                Category cat = ordered.remove(from);
                ordered.add(to, cat);
                adapter.notifyItemMoved(from, to);
if (currentCategoryIndex == from) { currentCategoryIndex = to; currentCategoryName = ordered.get(to).name; }
                else if (currentCategoryIndex == to) { currentCategoryIndex = from; currentCategoryName = ordered.get(from).name; }
                return true;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            @Override
            public boolean isLongPressDragEnabled() { return true; }
            @Override
            public boolean isItemViewSwipeEnabled() { return false; }
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                List<String> names = new ArrayList<>();
                for (Category c : ordered) names.add(c.name);
                DataStore.setCategoryOrder(MainActivity.this, t.name, names);
                refreshCategoryStyles();
                renderGreenContainer();
            }
        }).attachToRecyclerView(rvCategories);
    }

    private void refreshCategoryStyles() {
        if (rvCategories.getAdapter() instanceof CategoryAdapter) {
            ((CategoryAdapter) rvCategories.getAdapter()).refreshSelection(currentCategoryName);
        }
    }

    private void applyCategoryStyle(TextView tv, boolean selected) {
        if (selected) {
            tv.setBackgroundResource(R.drawable.bg_category_chalk_selected);
            tv.setTextColor(getColor(R.color.primary));
        } else {
            tv.setBackgroundResource(R.drawable.bg_category_chalk_normal);
            tv.setTextColor(getColor(R.color.white));
        }
    }

    // ===== 类目标签 RecyclerView 适配器 =====
    private class CategoryAdapter extends RecyclerView.Adapter<CategoryViewHolder> {
        private final List<Category> items;
        private int selectedIndex = 0;

        CategoryAdapter(List<Category> items) {
            this.items = items;
        }

        void refreshSelection(String categoryName) {
            int idx = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).name.equals(categoryName)) { idx = i; break; }
            }
            selectedIndex = idx;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(MainActivity.this);
            tv.setTextSize(14);
            tv.setPadding(dp(16), dp(8), dp(16), dp(8));
            tv.setGravity(Gravity.CENTER);
            return new CategoryViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
            Category c = items.get(position);
            holder.tv.setText((c.icon != null && !c.icon.isEmpty() ? c.icon + " " : "") + c.name);
            applyCategoryStyle(holder.tv, position == selectedIndex);
            holder.tv.setOnClickListener(v -> {
                currentCategoryIndex = position;
                currentCategoryName = items.get(position).name;
                refreshCategoryStyles();
                renderGreenContainer();
            });
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView tv;
        CategoryViewHolder(TextView v) { super(v); tv = v; }
    }

    // ===== 底部绿框：机器人按「备考目录.md」分级；其他科目按题目标签聚合 =====
    private void renderGreenContainer() {
        ExamType t = ExamCategoryCatalog.getByIndex(currentTypeIndex);
        llGreenContainer.removeAllViews();
        llGreenContainer.setBackgroundResource(R.drawable.bg_card);
        if (t.categories.isEmpty() || currentCategoryIndex >= t.categories.size()) {
            return;
        }
        Category c = null;
        for (Category cat : t.categories) {
            if (cat.name.equals(currentCategoryName)) { c = cat; break; }
        }
        if (c == null) return;
        boolean isRobot = c.name != null && c.name.contains("机器人");

        List<Question> qs = collectKnowledgeQuestions(c.name);
        kpAnswered = new HashSet<>();
        for (Question q : qs) {
            int[] st = DataStore.getQuestionStat(this, q.uniqueKey());
            if (st != null && st[0] > 0) kpAnswered.add(q.uniqueKey());
        }

        List<RobotCatalog.KpNode> roots;
        if (isRobot) {
            // 机器人：层级完全由 assets/在线刷新的《机器人等级考试备考目录.md》决定
            roots = RobotCatalog.buildCatalogTree(this, qs);
        } else if (qs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("该科目暂无知识点：请先在「真题练习」导入真题（题目需带知识点标签）\n\n点此去真题导入 →");
            empty.setTextSize(13);
            empty.setTextColor(getColor(R.color.text_sub));
            empty.setPadding(dp(12), dp(16), dp(12), dp(16));
            empty.setOnClickListener(v -> startActivity(PageTitle.intent(this, ExamListActivity.class,
                    findViewById(R.id.labelPractice))));
            llGreenContainer.addView(empty);
            return;
        } else {
            roots = RobotCatalog.buildTagTree(qs);
        }

        if (roots.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无目录，待完善");
            empty.setTextSize(13);
            empty.setTextColor(getColor(R.color.text_sub));
            empty.setPadding(dp(12), dp(16), dp(12), dp(16));
            llGreenContainer.addView(empty);
            return;
        }
        for (int i = 0; i < roots.size(); i++) {
            addKpNode(roots.get(i), 0);
        }
    }

    private Set<String> kpAnswered = new HashSet<>();

    /** 科目名 → 真题 subject 缩写（真题 key 形如 2024_12_robot_1）。 */
    private String subjectOfCategory(String name) {
        if (name == null) return null;
        if (name.contains("机器人")) return "robot";
        if (name.contains("Python") || name.contains("python")) return "py";
        if (name.contains("图形化")) return "gx";
        if (name.contains("C")) return "c";
        return null;
    }

    /** 收集该科目已导入、且带知识点的题目。 */
    private List<Question> collectKnowledgeQuestions(String categoryName) {
        String subject = subjectOfCategory(categoryName);
        List<Question> out = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RobotExamBank.Paper p : RobotExamBank.getCachedPapersList(this)) {
            if (p.key == null || p.questions == null) continue;
            if (subject != null && !p.key.contains("_" + subject + "_")) continue;
            for (Question q : p.questions) {
                if (q.knowledgePoints == null || q.knowledgePoints.length == 0) continue;
                if (seen.add(q.uniqueKey())) out.add(q);
            }
        }
        return out;
    }

    private int kpDoneCount(RobotCatalog.KpNode n) {
        int c = 0;
        for (String uid : n.uids) if (kpAnswered.contains(uid)) c++;
        return c;
    }

    /** 平铺渲染节点与其已展开的子节点（逐行展开，按层级阶梯缩进）。 */
    private void addKpNode(RobotCatalog.KpNode node, int depth) {
        View row = buildKpRow(node, depth);
        row.setTag(node.path);
        llGreenContainer.addView(row);
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        divider.setBackgroundColor(getColor(R.color.divider));
        divider.setTag(node.path);
        llGreenContainer.addView(divider);
        if (!node.children.isEmpty() && expandedPaths.contains(node.path)) {
            for (RobotCatalog.KpNode ch : node.children) addKpNode(ch, depth + 1);
        }
    }

    /** 展开/收起节点：展开时子行淡入下滑，收起时子行淡出右移，随后重绘。 */
    private void toggleKpNode(RobotCatalog.KpNode node) {
        if (expandedPaths.contains(node.path)) {
            final List<View> kids = kpRowsUnder(node.path);
            if (kids.isEmpty()) {
                expandedPaths.remove(node.path);
                renderGreenContainer();
                return;
            }
            final int n = kids.size();
            for (int i = 0; i < n; i++) {
                final boolean last = (i == n - 1);
                kids.get(i).animate()
                        .alpha(0f).translationX(dp(16))
                        .setDuration(120).setStartDelay(i * 10L)
                        .withEndAction(() -> {
                            if (last) {
                                expandedPaths.remove(node.path);
                                renderGreenContainer();
                            }
                        })
                        .start();
            }
        } else {
            expandedPaths.add(node.path);
            renderGreenContainer();
            List<View> kids = kpRowsUnder(node.path);
            for (int i = 0; i < kids.size(); i++) {
                View v = kids.get(i);
                v.setAlpha(0f);
                v.setTranslationY(-dp(8));
                v.animate().alpha(1f).translationY(0f)
                        .setDuration(160).setStartDelay(i * 12L).start();
            }
        }
    }

    /** 收集当前界面上属于该节点子树的行/分隔线（按渲染时打的 path tag）。 */
    private List<View> kpRowsUnder(String path) {
        List<View> out = new java.util.ArrayList<>();
        String prefix = path + "/";
        for (int i = 0; i < llGreenContainer.getChildCount(); i++) {
            View v = llGreenContainer.getChildAt(i);
            Object tag = v.getTag();
            if (tag instanceof String && ((String) tag).startsWith(prefix)) out.add(v);
        }
        return out;
    }

    /** 单行：状态圆点 + 名称/花瓣进度 + 右侧箭头。 */
    private View buildKpRow(RobotCatalog.KpNode node, int depth) {
        final boolean hasChildren = !node.children.isEmpty();
        final boolean expanded = expandedPaths.contains(node.path);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12 + depth * 16), dp(12), dp(12), dp(12));

        TextView dot = new TextView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(24), dp(24));
        dot.setLayoutParams(dlp);
        dot.setGravity(Gravity.CENTER);
        dot.setTextSize(12);
        if (!hasChildren) {
            dot.setText("\u00b7");
            dot.setBackgroundResource(R.drawable.bg_kp_circle_outline);
            dot.setTextColor(getColor(R.color.text_sub));
        } else if (expanded) {
            dot.setText("\u25b4");
            dot.setBackgroundResource(R.drawable.bg_kp_circle_filled);
            dot.setTextColor(getColor(R.color.white));
        } else {
            dot.setText("\u25be");
            dot.setBackgroundResource(R.drawable.bg_kp_circle_outline);
            dot.setTextColor(getColor(R.color.text_sub));
        }
        row.addView(dot);

        // 名称 + 花瓣进度
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(12);
        col.setLayoutParams(clp);

        TextView title = new TextView(this);
        title.setText(node.title);
        title.setTextSize(depth <= 0 ? 16 : (depth == 1 ? 15 : 14));
        title.setTextColor(depth == 0 ? getColor(R.color.text_main) : getColor(R.color.text_sub));
        if (depth == 0) title.setTypeface(null, android.graphics.Typeface.BOLD);
        col.addView(title);

        int total = node.uids.size();
        int done = kpDoneCount(node);
        LinearLayout prog = new LinearLayout(this);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(6);
        prog.setLayoutParams(plp);
        int filled = done > 0 ? Math.max(1, Math.round(6f * done / Math.max(1, total))) : 0;
        for (int i = 0; i < 6; i++) {
            android.widget.ImageView petal = new android.widget.ImageView(this);
            LinearLayout.LayoutParams petalLp = new LinearLayout.LayoutParams(dp(14), dp(14));
            petalLp.rightMargin = dp(3);
            petal.setLayoutParams(petalLp);
            petal.setImageResource(R.drawable.ic_kp_petal);
            petal.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(
                    i < filled ? R.color.primary : R.color.divider)));
            prog.addView(petal);
        }
        TextView cnt = new TextView(this);
        cnt.setText(done + "/" + total);
        cnt.setTextSize(12);
        cnt.setTextColor(getColor(R.color.text_sub));
        cnt.setPadding(dp(6), 0, 0, 0);
        prog.addView(cnt);
        col.addView(prog);
        row.addView(col);

        android.widget.ImageView arrow = new android.widget.ImageView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(16), dp(16));
        alp.leftMargin = dp(8);
        arrow.setLayoutParams(alp);
        arrow.setImageResource(R.drawable.ic_arrow_right);
        arrow.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.text_sub)));
        row.addView(arrow);

        row.setOnClickListener(v -> {
            if (hasChildren) {
                toggleKpNode(node);
            } else {
                startKnowledgeQuiz(node);
            }
        });
        return row;
    }

    /** 点知识点叶子：进入刷题页（复用 QuizActivity），题目仅该知识点下的。 */
    private void startKnowledgeQuiz(RobotCatalog.KpNode node) {
        java.util.ArrayList<String> uids = new java.util.ArrayList<>(node.uids);
        if (uids.isEmpty()) {
            Toast.makeText(this, "「" + node.title + "」暂无题目", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("source", "kpuids");
        i.putExtra("random", false);
        i.putStringArrayListExtra("kpuids", uids);
        PageTitle.put(i, node.title);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindBadges();
        // 交卷后进度已清除 / 中途退出则恢复：每次回首页都重新判断
        bindResume();
        // 导入/删除真题后，知识点聚合需要重新生成
        renderGreenContainer();
    }
    private void bindBadges() {
        int wrong = DataStore.getWrongIds(this).size();
        int favorite = DataStore.getFavoriteIds(this).size();
        android.widget.FrameLayout wrongBox = findViewById(R.id.badgeWrongBox);
        android.widget.FrameLayout favBox = findViewById(R.id.badgeFavoriteBox);
        TextView wrongTv = findViewById(R.id.badgeWrong);
        TextView favTv = findViewById(R.id.badgeFavorite);
        if (!DataStore.isBadgeVisible(this)) {
            hideBadge(wrongBox, wrongTv);
            hideBadge(favBox, favTv);
            lastWrongCount = wrong;
            lastFavoriteCount = favorite;
            return;
        }
        updateBadge(wrongBox, wrongTv, lastWrongCount, wrong);
        updateBadge(favBox, favTv, lastFavoriteCount, favorite);
        lastWrongCount = wrong;
        lastFavoriteCount = favorite;
    }

    /** 设置中关闭「显示数量角标」时：隐藏并清理动画残留。 */
    private void hideBadge(android.widget.FrameLayout box, TextView tv) {
        if (box == null || tv == null) return;
        for (int i = box.getChildCount() - 1; i >= 0; i--) {
            android.view.View child = box.getChildAt(i);
            if (child != tv) box.removeView(child);
        }
        tv.animate().cancel();
        tv.setTranslationY(0f);
        tv.setText("0");
        box.setVisibility(View.GONE);
    }

    /**
     * 更新数量角标，并在数量变化时播放「上下滑动顶替」动画：
     * 变大 → 新数字从上方滑入、旧数字向下滑出；变小 → 新数字从下方滑入、旧数字向上滑出。
     */
    private void updateBadge(final android.widget.FrameLayout box, final TextView tv,
                             int oldCount, final int newCount) {
        if (box == null || tv == null) return;
        // 清掉上一次动画遗留的临时视图
        for (int i = box.getChildCount() - 1; i >= 0; i--) {
            android.view.View child = box.getChildAt(i);
            if (child != tv) box.removeView(child);
        }
        if (newCount <= 0) {
            if (oldCount > 0 && box.getVisibility() == View.VISIBLE && box.getHeight() > 0) {
                // 归零：旧数字向上滑出后隐藏
                tv.animate().translationY(-box.getHeight()).setDuration(animDuration())
                        .withEndAction(() -> {
                            tv.setTranslationY(0f);
                            tv.setText("0");
                            box.setVisibility(View.GONE);
                        }).start();
            } else {
                tv.setTranslationY(0f);
                tv.setText("0");
                box.setVisibility(View.GONE);
            }
            return;
        }
        box.setVisibility(View.VISIBLE);
        // 首次显示 / 数字未变 / 尚未布局完成：直接赋值，不做动画
        if (oldCount < 0 || oldCount == newCount || box.getHeight() <= 0) {
            tv.setTranslationY(0f);
            tv.setText(String.valueOf(newCount));
            return;
        }
        // 复用统一的数字滑动动画（新值滑入、旧值滑出）
        String oldText = oldCount > 0 ? String.valueOf(oldCount) : "";
        CountSlideAnim.play(box, tv, oldText, String.valueOf(newCount), animDuration());
    }

    /** 角标动画时长：跟随设置里的「角标数字变化速度」。 */
    private int animDuration() {
        return Math.max(80, DataStore.getBadgeAnimDuration(this));
    }

    private void bindResume() {
        DataStore.ResumeState resume = DataStore.getResume(this);
        LinearLayout btnResume = findViewById(R.id.btnResume);
        if (resume == null || resume.uids == null || resume.uids.isEmpty()) {
            btnResume.setVisibility(View.GONE);
            return;
        }
        btnResume.setVisibility(View.VISIBLE);
        btnResume.setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra("source", resume.source);
            i.putExtra("random", resume.random);
            i.putExtra("resume", true);
            i.putExtra("resume_index", resume.index);
            i.putStringArrayListExtra("resume_uids", new java.util.ArrayList<>(resume.uids));
            startActivity(i);
        });
    }

    private void startQuiz(boolean random) {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("random", random);
        startActivity(i);
    }

    /** 打开子页面，并以入口控件的名称作为子页面标题。 */
    private void openPage(Class<?> target, int labelViewId) {
        TextView label = findViewById(labelViewId);
        startActivity(PageTitle.intent(this, target, label));
    }

    private void startQuizBySource(String source, int labelViewId) {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("source", source);
        i.putExtra("random", false);
        PageTitle.put(i, (TextView) findViewById(labelViewId));
        startActivity(i);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
