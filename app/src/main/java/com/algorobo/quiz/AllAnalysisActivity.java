package com.algorobo.quiz;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AllAnalysisActivity extends AppCompatActivity {
    private List<AnswerRecord> records = new ArrayList<>();
    /** Markdown 渲染（AI 解析文本复用刷题页的渲染方式）。 */
    private MarkdownUtil markdownRenderer;
    private RecyclerView rv;
    private AnalysisAdapter adapter;
    private LinearLayout indexContainer;
    private HorizontalScrollView indexScroll;
    private TextView btnAnalysisAllAi, btnAnalysisExpandAll;
    /** 解析区全局展开状态（工具栏「全部收起/全部展开」控制）。 */
    private boolean allExpanded = true;
    private int currentIndexPos = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);
        PageTitle.apply(this, R.id.tvAnalysisTitle);
        markdownRenderer = new MarkdownUtil(getColor(R.color.text_main), getColor(R.color.accent));

        java.io.Serializable data = getIntent().getSerializableExtra("records");
        if (data instanceof List) {
            List<?> raw = (List<?>) data;
            for (Object o : raw) {
                if (o instanceof AnswerRecord) records.add((AnswerRecord) o);
            }
        }
        TextView tvCount = findViewById(R.id.tvAnalysisCount);
        tvCount.setText("共 " + records.size() + " 题");
        findViewById(R.id.btnAnalysisBack).setOnClickListener(v -> finish());

        indexContainer = findViewById(R.id.analysisIndexContainer);
        indexScroll = findViewById(R.id.analysisIndexScroll);
        btnAnalysisAllAi = findViewById(R.id.btnAnalysisAllAi);
        btnAnalysisExpandAll = findViewById(R.id.btnAnalysisExpandAll);

        rv = findViewById(R.id.rvAnalysis);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AnalysisAdapter();
        rv.setAdapter(adapter);

        btnAnalysisAllAi.setOnClickListener(v -> analyzeAll());
        btnAnalysisExpandAll.setOnClickListener(v -> toggleAllExpand());
        updateExpandButton();

        renderAnalysisIndex();
        currentIndexPos = 0;
        highlightIndex(0);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateIndexHighlight();
            }
        });
        indexScroll.post(() -> scrollIndexToCurrent(0));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ===================== 题号索引条 =====================

    private boolean hasAiCache(AnswerRecord r) {
        if (r.uid == null) return false;
        String c = DataStore.getAiAnalysis(this, r.uid);
        return c != null && !c.trim().isEmpty();
    }

    private boolean hasAnyAnalysis(AnswerRecord r) {
        return (r.analysis != null && !r.analysis.trim().isEmpty()) || hasAiCache(r);
    }

    private void renderAnalysisIndex() {
        indexContainer.removeAllViews();
        int n = records.size();
        int dotSize = dp(28);
        int margin = dp(3);
        for (int i = 0; i < n; i++) {
            final int pos = i;
            TextView dot = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            dot.setLayoutParams(lp);
            dot.setGravity(Gravity.CENTER);
            dot.setText(String.valueOf(i + 1));
            dot.setTextSize(11);
            dot.setTextColor(getColor(R.color.text_main));
            boolean has = hasAnyAnalysis(records.get(i));
            dot.setBackgroundResource(has ? R.drawable.bg_index_done : R.drawable.bg_index_blank);
            if (has) dot.setTextColor(getColor(R.color.accent));
            dot.setOnClickListener(v -> {
                rv.scrollToPosition(pos);
                highlightIndex(pos);
                scrollIndexToCurrent(pos);
            });
            indexContainer.addView(dot);
        }
    }

    private void highlightIndex(int pos) {
        if (pos < 0 || pos >= indexContainer.getChildCount()) return;
        for (int i = 0; i < indexContainer.getChildCount(); i++) {
            TextView dot = (TextView) indexContainer.getChildAt(i);
            boolean has = hasAnyAnalysis(records.get(i));
            if (i == pos) {
                dot.setBackgroundResource(R.drawable.bg_index_current);
                dot.setTextColor(getColor(android.R.color.white));
            } else {
                dot.setBackgroundResource(has ? R.drawable.bg_index_done : R.drawable.bg_index_blank);
                dot.setTextColor(has ? getColor(R.color.accent) : getColor(R.color.text_main));
            }
        }
    }

    private void scrollIndexToCurrent(int pos) {
        if (indexContainer == null || indexScroll == null) return;
        View child = indexContainer.getChildAt(pos);
        if (child == null) return;
        int targetX = child.getLeft() - (indexScroll.getWidth() - child.getWidth()) / 2;
        if (targetX < 0) targetX = 0;
        indexScroll.scrollTo(targetX, 0);
    }

    private void updateIndexHighlight() {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        if (lm == null) return;
        int pos = lm.findFirstVisibleItemPosition();
        if (pos < 0 || pos == currentIndexPos) return;
        currentIndexPos = pos;
        highlightIndex(pos);
        scrollIndexToCurrent(pos);
    }

    // ===================== 工具栏 =====================

    /** 全部解析：对所有「没有正式解析且没有 AI 缓存」的题目批量生成 AI 解析。 */
    private void analyzeAll() {
        final List<AnswerRecord> need = new ArrayList<>();
        for (AnswerRecord r : records) {
            if (r.analysis != null && !r.analysis.trim().isEmpty()) continue;
            if (hasAiCache(r)) continue;
            need.add(r);
        }
        if (need.isEmpty()) {
            toast("所有题目都已有解析");
            return;
        }
        AiApi.Config cfg = DataStore.getCurrentAiConfig(this);
        if (cfg == null || !cfg.isComplete()) {
            toast("请先在「AI 模型配置」里配置模型和 API Key");
            return;
        }
        final int total = need.size();
        toast("开始 AI 解析 " + total + " 道题…");
        new Thread(() -> {
            int ok = 0;
            for (int i = 0; i < need.size(); i++) {
                AnswerRecord r = need.get(i);
                Question q = toQuestion(r);
                String ua = formatUserAnswer(r);
                AiApi.Result res = AiApi.analyzeQuestion(AllAnalysisActivity.this, cfg, q, ua, r.isCorrect());
                if (res != null && res.ok && r.uid != null) {
                    DataStore.setAiAnalysis(AllAnalysisActivity.this, r.uid, res.text);
                    ok++;
                }
                final int fi = i + 1;
                runOnUiThread(() -> {
                    if (fi % 5 == 0 || fi == total) toast("AI 解析 " + fi + "/" + total);
                });
            }
            final int okFinal = ok;
            runOnUiThread(() -> {
                adapter.refreshVisibleAi();
                renderAnalysisIndex();
                highlightIndex(currentIndexPos);
                toast("AI 解析完成：成功 " + okFinal + "/" + total);
            });
        }).start();
    }

    private void toggleAllExpand() {
        allExpanded = !allExpanded;
        updateExpandButton();
        adapter.applyAllExpanded(allExpanded);
    }

    private void updateExpandButton() {
        if (btnAnalysisExpandAll == null) return;
        btnAnalysisExpandAll.setText(allExpanded ? "全部收起" : "全部展开");
        btnAnalysisExpandAll.setCompoundDrawablesRelativeWithIntrinsicBounds(
                allExpanded ? R.drawable.ic_tool_collapse : R.drawable.ic_tool_expand, 0, 0, 0);
    }

    // ===================== 工具方法（供 Adapter 与工具栏共用） =====================

    private Question toQuestion(AnswerRecord r) {
        Question q = new Question();
        q.id = r.id;
        q.type = r.type;
        q.stem = r.stem;
        q.options = r.options;
        q.answerIndex = r.answerIndex;
        q.analysis = r.analysis;
        q.stemImg = r.stemImg;
        q.optionImgs = r.optionImgs;
        q.answerIndexes = r.answerIndexes;
        q.judgeAnswer = r.judgeAnswer;
        q.hasAnswer = r.hasAnswer;
        q.difficulty = r.difficulty;
        q.uid = r.uid;
        q.knowledgePoints = r.knowledgePoints;
        return q;
    }

    private String formatUserAnswer(AnswerRecord r) {
        if (!r.isAnswered()) return "未作答";
        if (Question.TYPE_JUDGE.equals(r.type)) {
            return r.userAnswer == 0 ? "正确" : "错误";
        }
        if (r.userAnswerIndexes != null && r.userAnswerIndexes.length > 0) {
            return indexesToLetters(r.userAnswerIndexes);
        }
        return letter(r.userAnswer);
    }

    private String formatCorrectAnswer(AnswerRecord r) {
        if (Question.TYPE_JUDGE.equals(r.type)) {
            return r.judgeAnswer == null ? "" : r.judgeAnswer;
        }
        if (r.answerIndexes != null && r.answerIndexes.length > 0) {
            return stringIndexesToLetters(r.answerIndexes);
        }
        return letter(r.answerIndex);
    }

    private String indexesToLetters(int[] idxs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idxs.length; i++) {
            sb.append((char) ('A' + idxs[i]));
            if (i < idxs.length - 1) sb.append('、');
        }
        return sb.toString();
    }

    private String stringIndexesToLetters(String[] idxs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idxs.length; i++) {
            try {
                int v = Integer.parseInt(idxs[i].trim());
                sb.append((char) ('A' + v));
            } catch (NumberFormatException e) {
                sb.append(idxs[i]);
            }
            if (i < idxs.length - 1) sb.append('、');
        }
        return sb.toString();
    }

    private String letter(int idx) {
        if (idx < 0) return "未作答";
        return String.valueOf((char) ('A' + idx));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private android.graphics.Bitmap loadMediaBitmap(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        try {
            java.io.File f = new java.io.File(fileName);
            if (!f.isAbsolute()) f = new java.io.File(getFilesDir(), fileName);
            if (!f.exists()) return null;
            java.io.InputStream is = new java.io.FileInputStream(f);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== Adapter =====================

    private class AnalysisAdapter extends RecyclerView.Adapter<AnalysisAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_analysis, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            AnswerRecord r = records.get(position);
            h.expanded = allExpanded;
            h.index.setText("第 " + (position + 1) + " 题");
            h.type.setText(r.type == null ? "" : r.type);
            h.stem.setText(r.stem == null ? "" : r.stem);

            StringBuilder opt = new StringBuilder();
            if (r.options != null && r.options.length > 0) {
                for (int i = 0; i < r.options.length; i++) {
                    char letter = (char) ('A' + i);
                    opt.append(letter).append(". ").append(r.options[i]);
                    if (i < r.options.length - 1) opt.append('\n');
                }
            }
            h.options.setText(opt.toString());

            h.user.setText("你的答案：" + formatUserAnswer(r));
            h.correctAns.setText("正确答案：" + formatCorrectAnswer(r));

            boolean correct = r.isCorrect();
            if (r.type != null && (Question.TYPE_PRACTICAL.equals(r.type)
                    || Question.TYPE_SHORT.equals(r.type)
                    || Question.TYPE_ATTACH.equals(r.type))) {
                h.result.setText("实务题（人工评分）");
                h.result.setTextColor(getColor(R.color.text_sub));
            } else {
                h.result.setText(correct ? "回答正确" : "回答错误");
                h.result.setTextColor(getColor(correct
                        ? R.color.answer_correct_text
                        : R.color.answer_wrong_text));
            }
            h.detail.setText(r.analysis == null || r.analysis.isEmpty() ? "（暂无解析）" : r.analysis);
            bindKnowledge(h, r);
            bindAiAnalysis(h, r);
            applyAnalysisVisibility(h);
            bindActions(h, r);
            renderRecordImages(h, r);
        }

        /** 题号行右侧：收藏 / 错题本（复用刷题页的数据与图标）。 */
        private void bindActions(final Holder h, final AnswerRecord r) {
            updateActionIcons(h, r);
            h.btnFav.setOnClickListener(v -> {
                DataStore.toggleFavorite(AllAnalysisActivity.this, r.uid);
                boolean fav = DataStore.isFavorite(AllAnalysisActivity.this, r.uid);
                toast(fav ? "已收藏" : "已取消收藏");
                updateActionIcons(h, r);
            });
            h.btnWrong.setOnClickListener(v -> {
                boolean in = DataStore.getWrongIds(AllAnalysisActivity.this).contains(r.uid);
                if (in) DataStore.removeWrong(AllAnalysisActivity.this, r.uid);
                else DataStore.addWrong(AllAnalysisActivity.this, r.uid);
                toast(in ? "已移出错题本" : "已加入错题本");
                updateActionIcons(h, r);
            });
        }

        private void updateActionIcons(Holder h, AnswerRecord r) {
            boolean fav = r.uid != null && DataStore.isFavorite(AllAnalysisActivity.this, r.uid);
            h.btnFav.setImageResource(fav ? R.drawable.ic_tool_favorite_filled : R.drawable.ic_tool_favorite);
            h.btnFav.setImageTintList(android.content.res.ColorStateList.valueOf(
                    getColor(fav ? R.color.answer_wrong_text : R.color.text_sub)));
            boolean in = r.uid != null && DataStore.getWrongIds(AllAnalysisActivity.this).contains(r.uid);
            h.btnWrong.setImageResource(in ? R.drawable.ic_tool_wrongbook_remove : R.drawable.ic_tool_wrongbook);
            h.btnWrong.setImageTintList(android.content.res.ColorStateList.valueOf(
                    getColor(in ? R.color.answer_wrong_text : R.color.text_sub)));
        }

        private void bindKnowledge(Holder h, AnswerRecord r) {
            List<String> list = new ArrayList<>();
            if (r.knowledgePoints != null) {
                for (String s : r.knowledgePoints) {
                    if (s != null && !s.trim().isEmpty()) list.add(s.trim());
                }
            }
            if (list.isEmpty()) {
                h.knowledge.setVisibility(View.GONE);
                return;
            }
            String first = list.get(0);
            int slash = first.lastIndexOf(" / ");
            h.knowledge.setText(slash >= 0 ? first.substring(slash + 3) : first);
            h.knowledge.setVisibility(View.VISIBLE);
            final String all = list.size() == 1 ? first : android.text.TextUtils.join("\n· ", list);
            h.knowledge.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(AllAnalysisActivity.this)
                    .setTitle("本题知识点")
                    .setMessage(all.contains("\n") ? "· " + all : all)
                    .setPositiveButton("知道了", null)
                    .show());
        }

        /** AI 解析：优先缓存；无正式解析时用 AI 内容替换「暂无解析」。 */
        private void bindAiAnalysis(Holder h, final AnswerRecord r) {
            applyCachedAi(h, r);
            h.btnAi.setOnClickListener(v -> triggerAi(h, r));
        }

        /** 从缓存读取 AI 解析并展示：有正式解析→填 AI 区；无→替换「暂无解析」。 */
        private void applyCachedAi(Holder h, AnswerRecord r) {
            String cached = r.uid == null ? null : DataStore.getAiAnalysis(AllAnalysisActivity.this, r.uid);
            boolean hasReal = r.analysis != null && !r.analysis.trim().isEmpty();
            if (cached != null && !cached.trim().isEmpty()) {
                if (hasReal) {
                    h.aiDetail.setText(markdownRenderer.render("AI 解析，酌情参考：\n" + cached.trim()));
                    h.aiDetail.setVisibility(View.VISIBLE);
                } else {
                    h.detail.setText(markdownRenderer.render("AI 解析，酌情参考：\n" + cached.trim()));
                    h.aiDetail.setVisibility(View.GONE);
                }
            } else {
                h.aiDetail.setVisibility(View.GONE);
            }
        }

        private void triggerAi(final Holder h, final AnswerRecord r) {
            String cached = r.uid == null ? null : DataStore.getAiAnalysis(AllAnalysisActivity.this, r.uid);
            if (cached != null && !cached.trim().isEmpty()) {
                applyCachedAi(h, r);
                h.expanded = true;
                applyAnalysisVisibility(h);
                return;
            }
            AiApi.Config cfg = DataStore.getCurrentAiConfig(AllAnalysisActivity.this);
            if (cfg == null || !cfg.isComplete()) {
                toast("请先在「AI 模型配置」里配置模型和 API Key");
                return;
            }
            final Question q = toQuestion(r);
            final String ua = formatUserAnswer(r);
            final boolean hasReal = r.analysis != null && !r.analysis.trim().isEmpty();
            if (hasReal) {
                h.aiDetail.setVisibility(View.VISIBLE);
                h.aiDetail.setText("AI 正在解析中…");
            } else {
                h.detail.setText("AI 正在解析中…");
            }
            new Thread(() -> {
                final AiApi.Result res = AiApi.analyzeQuestion(AllAnalysisActivity.this, cfg, q, ua, r.isCorrect());
                runOnUiThread(() -> {
                    if (res != null && res.ok) {
                        if (r.uid != null) DataStore.setAiAnalysis(AllAnalysisActivity.this, r.uid, res.text);
                        applyCachedAi(h, r);
                    } else {
                        String err = "AI 解析失败：" + (res == null ? "未知错误" : res.text);
                        if (hasReal) {
                            h.aiDetail.setText(err);
                            h.aiDetail.setVisibility(View.VISIBLE);
                        } else {
                            h.detail.setText(err);
                        }
                    }
                    h.expanded = true;
                    applyAnalysisVisibility(h);
                });
            }).start();
        }

        /** 收起/展开：控制「解析」与「AI 解析」两块（一并折叠）。 */
        private void applyAnalysisVisibility(Holder h) {
            h.detail.setVisibility(h.expanded ? View.VISIBLE : View.GONE);
            boolean aiHas = h.aiDetail.getText() != null && h.aiDetail.getText().length() > 0;
            h.aiDetail.setVisibility((h.expanded && aiHas) ? View.VISIBLE : View.GONE);
            h.btnToggle.setText(h.expanded ? "收起" : "展开");
        }

        private void bindToggle(Holder h) {
            h.btnToggle.setOnClickListener(v -> {
                h.expanded = !h.expanded;
                applyAnalysisVisibility(h);
            });
        }

        /** 批量 AI 解析后刷新当前可见项。 */
        void refreshVisibleAi() {
            for (int i = 0; i < rv.getChildCount(); i++) {
                RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
                if (vh instanceof Holder) {
                    Holder h = (Holder) vh;
                    int pos = h.getAdapterPosition();
                    if (pos >= 0 && pos < records.size()) {
                        applyCachedAi(h, records.get(pos));
                        applyAnalysisVisibility(h);
                    }
                }
            }
        }

        /** 工具栏「全部收起/展开」应用到当前可见项。 */
        void applyAllExpanded(boolean exp) {
            for (int i = 0; i < rv.getChildCount(); i++) {
                RecyclerView.ViewHolder vh = rv.getChildViewHolder(rv.getChildAt(i));
                if (vh instanceof Holder) {
                    Holder h = (Holder) vh;
                    h.expanded = exp;
                    applyAnalysisVisibility(h);
                }
            }
        }

        private void renderRecordImages(Holder h, AnswerRecord r) {
            if (r.stemImg != null && !r.stemImg.isEmpty()) {
                final android.graphics.Bitmap bmp = loadMediaBitmap(r.stemImg);
                if (bmp != null) {
                    h.ivStemImage.setVisibility(View.VISIBLE);
                    h.ivStemImage.setImageBitmap(bmp);
                    h.ivStemImage.setOnClickListener(v -> showImageDialog(bmp));
                } else {
                    h.ivStemImage.setVisibility(View.GONE);
                }
            } else {
                h.ivStemImage.setVisibility(View.GONE);
            }
            h.analysisOptionImages.removeAllViews();
            boolean hasOpt = r.optionImgs != null;
            if (hasOpt) {
                int n = r.options == null ? 0 : r.options.length;
                int count = 0;
                for (int i = 0; i < n; i++) {
                    String imgName = (r.optionImgs != null && i < r.optionImgs.length) ? r.optionImgs[i] : null;
                    if (imgName == null || imgName.isEmpty()) continue;
                    android.graphics.Bitmap bmp = loadMediaBitmap(imgName);
                    if (bmp != null) {
                        h.analysisOptionImages.addView(makeAnalysisImage(bmp, String.valueOf((char) ('A' + i))));
                        count++;
                    }
                }
                h.analysisOptionImages.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            } else {
                h.analysisOptionImages.setVisibility(View.GONE);
            }
        }

        private View makeAnalysisImage(final android.graphics.Bitmap bmp, String label) {
            LinearLayout row = new LinearLayout(AllAnalysisActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(8);
            row.setLayoutParams(rlp);
            if (label != null) {
                TextView tv = new TextView(AllAnalysisActivity.this);
                tv.setText(label);
                tv.setGravity(Gravity.CENTER);
                tv.setTextColor(getColor(R.color.text_main));
                tv.setTextSize(14);
                tv.setBackgroundResource(R.drawable.bg_tag_gray);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(28), dp(28));
                tlp.rightMargin = dp(12);
                row.addView(tv, tlp);
            }
            ImageView iv = new ImageView(AllAnalysisActivity.this);
            iv.setImageBitmap(bmp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(dp(240));
            iv.setContentDescription("解析图片");
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(iv, ilp);
            iv.setOnClickListener(v -> showImageDialog(bmp));
            return row;
        }

        private void showImageDialog(android.graphics.Bitmap bmp) {
            if (bmp == null) return;
            Dialog d = new Dialog(AllAnalysisActivity.this);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ImageView iv = new ImageView(AllAnalysisActivity.this);
            iv.setImageBitmap(bmp);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundColor(0xFF000000);
            iv.setOnClickListener(v -> d.dismiss());
            d.setContentView(iv);
            Window w = d.getWindow();
            if (w != null) {
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000));
            }
            d.show();
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView index, type, stem, options, user, correctAns, result, detail;
            TextView knowledge, btnAi, btnToggle, aiDetail;
            ImageView ivStemImage, btnFav, btnWrong;
            LinearLayout analysisOptionImages;
            boolean expanded = true;

            Holder(View v) {
                super(v);
                index = v.findViewById(R.id.tvAnalysisIndex);
                type = v.findViewById(R.id.tvAnalysisType);
                stem = v.findViewById(R.id.tvAnalysisStem);
                options = v.findViewById(R.id.tvAnalysisOptions);
                user = v.findViewById(R.id.tvAnalysisUser);
                correctAns = v.findViewById(R.id.tvAnalysisCorrectAns);
                result = v.findViewById(R.id.tvAnalysisResult);
                detail = v.findViewById(R.id.tvAnalysisDetail);
                knowledge = v.findViewById(R.id.tvAnalysisKnowledge);
                btnAi = v.findViewById(R.id.btnAnalysisAi);
                btnToggle = v.findViewById(R.id.btnAnalysisToggle);
                aiDetail = v.findViewById(R.id.tvAnalysisAiDetail);
                ivStemImage = v.findViewById(R.id.ivAnalysisStemImage);
                analysisOptionImages = v.findViewById(R.id.analysisOptionImages);
                btnFav = v.findViewById(R.id.btnAnalysisFavorite);
                btnWrong = v.findViewById(R.id.btnAnalysisWrongbook);
                bindToggle(this);
            }
        }
    }
}
