package com.algorobo.quiz;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.app.Dialog;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AllAnalysisActivity extends AppCompatActivity {
    private List<AnswerRecord> records = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

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
        RecyclerView rv = findViewById(R.id.rvAnalysis);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new AnalysisAdapter());
    }

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
                h.result.setText(correct ? "✓ 正确" : "✗ 错误");
                h.result.setTextColor(getColor(correct
                        ? R.color.answer_correct_text
                        : R.color.answer_wrong_text));
            }
            h.detail.setText(r.analysis == null || r.analysis.isEmpty() ? "（暂无解析）" : r.analysis);
            renderRecordImages(h, r);
        }
        private void renderRecordImages(Holder h, AnswerRecord r) {
            // 题干图片
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
            // 选项图片
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
        private android.graphics.Bitmap loadMediaBitmap(String fileName) {
            if (fileName == null || fileName.isEmpty()) return null;
            try {
                // 图片已由 RobotExamUpdater 提取到 filesDir 缓存目录，这里按文件路径直接读取。
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
        private int dp(int v) {
            return Math.round(v * getResources().getDisplayMetrics().density);
        }

        @Override
        public int getItemCount() {
            return records.size();
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

        class Holder extends RecyclerView.ViewHolder {
            TextView index, type, stem, options, user, correctAns, result, detail;
            ImageView ivStemImage;
            LinearLayout analysisOptionImages;
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
                ivStemImage = v.findViewById(R.id.ivAnalysisStemImage);
                analysisOptionImages = v.findViewById(R.id.analysisOptionImages);
            }
        }
    }
}
