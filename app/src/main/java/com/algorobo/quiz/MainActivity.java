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
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import com.algorobo.quiz.ExamCategoryCatalog.Category;
import com.algorobo.quiz.ExamCategoryCatalog.ExamType;
import com.algorobo.quiz.ExamCategoryCatalog.TreeNode;

public class MainActivity extends AppCompatActivity {

    private int currentTypeIndex = 0;
    private int currentCategoryIndex = 0;
    private final Set<String> expandedPaths = new HashSet<>();

    private TextView tvExamTypeName;
    private LinearLayout llCategories;
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
        llCategories = findViewById(R.id.llCategories);
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

        findViewById(R.id.fabSettings).setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            PageTitle.put(i, v.getContentDescription());
            startActivity(i);
        });

        bindBadges();
        bindResume();

        // 初始化考试类型与类目
        renderExamType(currentTypeIndex);
        renderCategories();
        renderGreenContainer();
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

    // ===== 类目标签（红框） =====
    private void renderCategories() {
        ExamType t = ExamCategoryCatalog.getByIndex(currentTypeIndex);
        llCategories.removeAllViews();
        for (int i = 0; i < t.categories.size(); i++) {
            final int idx = i;
            Category c = t.categories.get(i);
            final TextView tag = new TextView(this);
            tag.setText((c.icon != null && !c.icon.isEmpty() ? c.icon + " " : "") + c.name);
            tag.setTextSize(14);
            tag.setPadding(dp(16), dp(8), dp(16), dp(8));
            tag.setGravity(Gravity.CENTER);
            applyCategoryStyle(tag, i == currentCategoryIndex);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            tag.setLayoutParams(lp);
            tag.setOnClickListener(v -> {
                currentCategoryIndex = idx;
                refreshCategoryStyles();
                renderGreenContainer();
            });
            llCategories.addView(tag);
        }
    }

    private void refreshCategoryStyles() {
        for (int i = 0; i < llCategories.getChildCount(); i++) {
            applyCategoryStyle((TextView) llCategories.getChildAt(i), i == currentCategoryIndex);
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

    // ===== 底部绿框三级容器 =====
    private void renderGreenContainer() {
        ExamType t = ExamCategoryCatalog.getByIndex(currentTypeIndex);
        llGreenContainer.removeAllViews();
        if (t.categories.isEmpty() || currentCategoryIndex >= t.categories.size()) {
            return;
        }
        Category c = t.categories.get(currentCategoryIndex);

        // 统一三级树：所有类目（普通 + 机器人）都映射为 TreeNode 树
        List<TreeNode> roots = ExamCategoryCatalog.buildTree(c);
        if (roots.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无目录，待完善");
            empty.setTextSize(13);
            empty.setTextColor(getColor(R.color.text_sub));
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            llGreenContainer.addView(empty);
            return;
        }

        for (int i = 0; i < roots.size(); i++) {
            llGreenContainer.addView(buildTreeNode(roots.get(i), String.valueOf(i), 0));
        }
    }

    /**
     * 递归构建三级目录节点卡片。
     * @param node  当前节点
     * @param path  当前节点路径（如 "0"、"0/1"、"0/1/2"），作为展开状态 key
     * @param depth 层级深度：0=一级，1=二级，2=三级叶子
     */
    private View buildTreeNode(TreeNode node, String path, int depth) {
        final boolean hasChildren = node.children != null && !node.children.isEmpty();
        final boolean expanded = expandedPaths.contains(path);
        final boolean isLeaf = !hasChildren; // 三级叶子（知识点）

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(depth * 8), 0, 0, dp(8));
        card.setLayoutParams(lp);

        // 标题行：箭头 + 标题 + 进度（叶子）
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView arrow = new TextView(this);
        if (hasChildren) {
            arrow.setText(expanded ? "\u25be" : "\u25b8");
        } else {
            arrow.setText("\u00b7"); // 叶子用小圆点
        }
        arrow.setTextSize(15);
        arrow.setTextColor(getColor(R.color.primary));
        arrow.setPadding(0, 0, dp(6), 0);
        header.addView(arrow);

        TextView title = new TextView(this);
        title.setText(node.title);
        title.setTextSize(depth == 0 ? 15 : (depth == 1 ? 14 : 13));
        title.setTextColor(depth == 0 ? getColor(R.color.primary) : getColor(R.color.text_main));
        if (depth == 0 || depth == 1) {
            title.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        header.addView(title, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 叶子节点右侧显示刷题进度
        if (isLeaf && node.progress != null && !node.progress.isEmpty()) {
            TextView prog = new TextView(this);
            prog.setText(node.progress);
            prog.setTextSize(12);
            prog.setTextColor(getColor(R.color.text_sub));
            header.addView(prog);
        }

        card.addView(header);

        // 点击交互：有子节点→展开/折叠；叶子→跳转知识点刷题（预留）
        header.setOnClickListener(v -> {
            if (hasChildren) {
                if (expandedPaths.contains(path)) {
                    expandedPaths.remove(path);
                } else {
                    expandedPaths.add(path);
                }
                renderGreenContainer();
            } else {
                onLeafClick(node, path);
            }
        });

        // 展开时递归渲染子节点
        if (expanded && hasChildren) {
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(0, dp(4), 0, 0);
            card.addView(body);
            for (int i = 0; i < node.children.size(); i++) {
                body.addView(buildTreeNode(node.children.get(i),
                    path + "/" + i, depth + 1));
            }
        }

        return card;
    }

    /** 三级叶子节点点击：后期用于知识点分类刷题跳转 */
    private void onLeafClick(TreeNode node, String path) {
        Toast.makeText(this, "知识点：「" + node.title + "」", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindBadges();
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
        final int h = box.getHeight();
        final boolean increase = newCount > oldCount;
        // 1) 复制一份「旧数字」用于滑出（动画结束移除）
        if (oldCount > 0) {
            TextView outgoing = makeBadgeNumber(tv, oldCount, h);
            box.addView(outgoing);
            outgoing.animate().translationY(increase ? h : -h)
                    .setDuration(animDuration())
                    .withEndAction(() -> box.removeView(outgoing))
                    .start();
        }
        // 2) 常驻 TextView 换成新数字，从对侧滑入到原位
        tv.setText(String.valueOf(newCount));
        tv.setTranslationY(increase ? -h : h);
        tv.animate().translationY(0f).setDuration(animDuration()).start();
    }

    /** 克隆角标数字样式，用于动画中的临时视图。 */
    private TextView makeBadgeNumber(TextView src, int value, int heightPx) {
        TextView v = new TextView(this);
        v.setText(String.valueOf(value));
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, src.getTextSize());
        v.setTextColor(src.getCurrentTextColor());
        v.setTypeface(src.getTypeface());
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        v.setPadding(src.getPaddingLeft(), 0, src.getPaddingRight(), 0);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, heightPx);
        lp.gravity = Gravity.CENTER;
        v.setLayoutParams(lp);
        return v;
    }

    /** 角标动画时长：跟随设置里的「角标数字变化速度」。 */
    private int animDuration() {
        return Math.max(80, DataStore.getBadgeAnimDuration(this));
    }

    private void bindResume() {
        DataStore.ResumeState resume = DataStore.getResume(this);
        LinearLayout btnResume = findViewById(R.id.btnResume);
        if (resume == null) {
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
