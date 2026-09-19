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
import com.algorobo.quiz.ExamCategoryCatalog.Node;

public class MainActivity extends AppCompatActivity {

    private int currentTypeIndex = 0;
    private int currentCategoryIndex = 0;
    private final Set<Integer> expandedLevels = new HashSet<>();

    private TextView tvExamTypeName;
    private TextView tvExamTypeSubtitle;
    private LinearLayout llCategories;
    private LinearLayout llGreenContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvExamTypeName = findViewById(R.id.tvExamTypeName);
        tvExamTypeSubtitle = findViewById(R.id.tvExamTypeSubtitle);
        llCategories = findViewById(R.id.llCategories);
        llGreenContainer = findViewById(R.id.llGreenContainer);

        // 考试类型下拉选择
        findViewById(R.id.rowExamType).setOnClickListener(v -> showExamTypePopup());

        // 功能卡片
        findViewById(R.id.cardPractice).setOnClickListener(v ->
            startActivity(new Intent(this, ExamListActivity.class)));
        findViewById(R.id.modeSequence).setOnClickListener(v -> startQuizBySource("custom"));
        findViewById(R.id.modeRandom).setOnClickListener(v -> startQuiz(true));

        findViewById(R.id.cardWrongBook).setOnClickListener(v -> startQuizBySource("wrong"));
        findViewById(R.id.cardFavorite).setOnClickListener(v -> startQuizBySource("favorite"));
        findViewById(R.id.cardStatistics).setOnClickListener(v ->
            startActivity(new Intent(this, StatisticsActivity.class)));
        findViewById(R.id.cardCalendar).setOnClickListener(v ->
            startActivity(new Intent(this, CalendarActivity.class)));

        findViewById(R.id.cardCustomBank).setOnClickListener(v ->
            startActivity(new Intent(this, CustomBankActivity.class)));

        findViewById(R.id.cardKnowledge).setOnClickListener(v ->
            startActivity(new Intent(this, KnowledgeActivity.class)));

        findViewById(R.id.fabSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));

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
        tvExamTypeSubtitle.setText(t.subtitle);
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
            tv.setBackgroundResource(R.drawable.bg_category_selected);
            tv.setTextColor(getColor(R.color.primary_light));
        } else {
            tv.setBackgroundResource(R.drawable.bg_category_normal);
            tv.setTextColor(getColor(R.color.text_main));
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

        // 机器人等级考试：多级折叠展开容器
        if (c.levels != null && !c.levels.isEmpty()) {
            for (int i = 0; i < c.levels.size(); i++) {
                llGreenContainer.addView(buildLevelCard(c.levels.get(i), i));
            }
            return;
        }

        if (c.nodes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无目录，待完善");
            empty.setTextSize(13);
            empty.setTextColor(getColor(R.color.text_sub));
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            llGreenContainer.addView(empty);
            return;
        }

        for (Node node : c.nodes) {
            llGreenContainer.addView(buildNodeCard(node));
        }
    }

    private View buildNodeCard(Node node) {
        // 紧凑卡片：二级标题 + 三级子项内联文本（自动换行，自适应美观）
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);

        // 二级标题（加粗带图标）
        TextView title = new TextView(this);
        title.setText("▸ " + node.title);
        title.setTextSize(14);
        title.setTextColor(getColor(R.color.primary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(4));
        card.addView(title);

        // 三级子项：内联逗号分隔，自动换行
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.children.size(); i++) {
            if (i > 0) sb.append("  ·  ");
            sb.append(node.children.get(i));
        }
        TextView items = new TextView(this);
        items.setText(sb.toString());
        items.setTextSize(13);
        items.setTextColor(getColor(R.color.text_sub));
        items.setLineSpacing(dp(2), 1f);
        card.addView(items);
        return card;
    }

    private View buildLevelCard(RobotLevel.Level level, final int index) {
        final boolean expanded = expandedLevels.contains(index);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText((expanded ? "\u25be " : "\u25b8 ") + level.name);
        title.setTextSize(15);
        title.setTextColor(getColor(R.color.primary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!expanded && !level.metas.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(level.metas.get(0));
            hint.setTextSize(11);
            hint.setTextColor(getColor(R.color.text_sub));
            hint.setMaxLines(1);
            hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(header);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(6), 0, 0);
        card.addView(body);

        header.setOnClickListener(v -> {
            if (expandedLevels.contains(index)) {
                expandedLevels.remove(index);
            } else {
                expandedLevels.add(index);
            }
            renderGreenContainer();
        });

        if (expanded) {
            for (String m : level.metas) {
                TextView mv = new TextView(this);
                mv.setText(m);
                mv.setTextSize(12);
                mv.setTextColor(getColor(R.color.text_sub));
                mv.setPadding(0, 0, 0, dp(2));
                body.addView(mv);
            }
            for (RobotLevel.Section sec : level.sections) {
                TextView st = new TextView(this);
                st.setText(sec.title);
                st.setTextSize(13);
                st.setTextColor(getColor(R.color.text_main));
                st.setTypeface(null, android.graphics.Typeface.BOLD);
                st.setPadding(0, dp(6), 0, dp(2));
                body.addView(st);

                for (RobotLevel.Item item : sec.items) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(item.text);
                    if (!item.subs.isEmpty()) {
                        sb.append("  \uff08");
                        for (int k = 0; k < item.subs.size(); k++) {
                            if (k > 0) sb.append("\u3001");
                            sb.append(item.subs.get(k));
                        }
                        sb.append("\uff09");
                    }
                    TextView iv = new TextView(this);
                    iv.setText(sb.toString());
                    iv.setTextSize(12);
                    iv.setTextColor(getColor(R.color.text_sub));
                    iv.setLineSpacing(dp(2), 1f);
                    iv.setPadding(dp(8), dp(1), 0, dp(1));
                    body.addView(iv);
                }
            }
        }

        return card;
    }

    private void bindBadges() {
        int wrong = DataStore.getWrongIds(this).size();
        int favorite = DataStore.getFavoriteIds(this).size();
        TextView badgeWrong = findViewById(R.id.badgeWrong);
        TextView badgeFavorite = findViewById(R.id.badgeFavorite);
        if (wrong > 0) { badgeWrong.setVisibility(View.VISIBLE); badgeWrong.setText(String.valueOf(wrong)); }
        else badgeWrong.setVisibility(View.GONE);
        if (favorite > 0) { badgeFavorite.setVisibility(View.VISIBLE); badgeFavorite.setText(String.valueOf(favorite)); }
        else badgeFavorite.setVisibility(View.GONE);
    }

    private void bindResume() {
        DataStore.ResumeState resume = DataStore.getResume(this);
        TextView btnResume = findViewById(R.id.btnResume);
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

    private void startQuizBySource(String source) {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("source", source);
        i.putExtra("random", false);
        startActivity(i);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
