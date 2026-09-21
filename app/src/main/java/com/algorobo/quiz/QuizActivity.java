package com.algorobo.quiz;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
public class QuizActivity extends AppCompatActivity {
    private List<Question> questions = new ArrayList<>();
    private int[] userAnswers; // -1 未答；单选/判断用此（判断：0=正确,1=错误）
    private int[][] multiAnswers; // 多选题选中的选项索引
    private boolean[] answered;
    private int index = 0;
    private boolean random;
    private String source = "all"; // all / wrong / custom / paper:<key>

    private TextView tvQuestionType, tvQuestion, tvKnowledgeTag, tvScore;
    private TextView btnStat;
    private ImageView ivStemImage;
    /** 题干混排容器（文字/图片按 docx 顺序）。 */
    private LinearLayout stemContainer;
    /** 混排容器中动态创建的文字块，随字号设置同步缩放。 */
    private final List<TextView> stemTextViews = new ArrayList<>();
    private LinearLayout optionsContainer;
    private LinearLayout actionContainer;
    private android.widget.ProgressBar pbProgress;
    private android.widget.ScrollView scrollQuestion;
    private GestureDetector gestureDetector;
    /** 上次返回键/返回按钮的时间，用于「再按一次返回」的二次确认。 */
    private long lastBackPressedTime;
    private static final long BACK_CONFIRM_INTERVAL = 2000L;
    /** 本次触摸起始于可横向滚动区域，需保留其自身滚动，不判为切题滑动。 */
    private boolean swipeGestureBlocked;
    private LinearLayout questionContent;

    private ImageView btnToolTime, btnToolWrongbook, btnToolDownload, btnToolSheet, btnToolMore;

    private LinearLayout analysisPanel;
    private TextView tvResultText, tvAnswerText, tvAnalysisText;
    private TextView btnShowAnalysis;
    private TextView btnAiAnalysis;
    private TextView tvAiAnalysis;
    private MarkdownUtil markdownRenderer;
    private LinearLayout analysisImagesContainer;
    private TextView tvPaperTitle;
    private LinearLayout indexContainer;
    private android.widget.HorizontalScrollView indexScroll;
    private long perQuestionStart;
    private long[] questionStartTimes; // 每题首次展示时的已耗时基准
    private long[] questionDurations;  // 每题真实作答用时（首次作答时记录）

    private boolean nightMode = false; // 会话内夜间/日间切换
    private boolean brushMode = false; // false=背题模式（点选项立即显示答案）；true=刷题模式（交卷前不显示答案）
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime;
    private Runnable timerRunnable;
    private Runnable moreTimeRunnable;
    private long elapsed = 0;
    private final HashMap<Integer, ArrayList<DrawPadView.StrokeData>> draftByQuestion = new HashMap<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        ThemeManager.applyStatusBar(this);
        ThemeManager.applyTopBarColor(this, R.id.topBar);

        random = getIntent().getBooleanExtra("random", false);
        source = getIntent().getStringExtra("source");
        if (source == null) source = "all";
        boolean resume = getIntent().getBooleanExtra("resume", false);
        int resumeIndex = getIntent().getIntExtra("resume_index", -1);
        java.util.ArrayList<String> resumeUids = getIntent().getStringArrayListExtra("resume_uids");
        questions = loadQuestions(source);
        // 恢复上次进度：按 uid 重新排序题目，并定位到上次作答的索引
        if (resume && resumeUids != null && !resumeUids.isEmpty()) {
            questions = reorderByUids(questions, resumeUids);
            random = false; // 恢复时沿用快照顺序，不再重新随机
        }
        if (questions.isEmpty()) {
            // 区分「某套真题没解析到题目」与「当前没有可练的题」，便于用户重新导入
            showToast(source != null && source.startsWith("paper:")
                    ? "该套真题没有可用题目，请重新导入" : "暂无题目");
            finish();
            return;
        }
        maybeSyncKnowledge();
        if (random) Collections.shuffle(questions);
        if (resume && resumeIndex >= 0 && resumeIndex < questions.size()) {
            index = resumeIndex;
        }
        userAnswers = new int[questions.size()];
        multiAnswers = new int[questions.size()][];
        answered = new boolean[questions.size()];
        questionStartTimes = new long[questions.size()];
        questionDurations = new long[questions.size()];
        for (int i = 0; i < userAnswers.length; i++) userAnswers[i] = -1;

        tvQuestionType = findViewById(R.id.tvQuestionType);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvKnowledgeTag = findViewById(R.id.tvKnowledgeTag);
        tvScore = findViewById(R.id.tvScore);
        btnStat = findViewById(R.id.btnStat);
        btnStat.setOnClickListener(v -> showQuestionStat());
        ivStemImage = findViewById(R.id.ivStemImage);
        stemContainer = findViewById(R.id.stemContainer);
        optionsContainer = findViewById(R.id.optionsContainer);
        pbProgress = findViewById(R.id.pbProgress);
        questionContent = findViewById(R.id.questionContent);
        View btnBack = findViewById(R.id.btnBack);

        analysisPanel = findViewById(R.id.analysisPanel);
        tvResultText = findViewById(R.id.tvResultText);
        tvAnswerText = findViewById(R.id.tvAnswerText);
        tvAnalysisText = findViewById(R.id.tvAnalysisText);
        btnShowAnalysis = findViewById(R.id.btnShowAnalysis);
        btnAiAnalysis = findViewById(R.id.btnAiAnalysis);
        tvAiAnalysis = findViewById(R.id.tvAiAnalysis);
        markdownRenderer = new MarkdownUtil(
                getColor(R.color.text_main), getColor(R.color.accent));
        btnAiAnalysis.setOnClickListener(v -> onAiAnalysisClick());

        analysisImagesContainer = findViewById(R.id.analysisImagesContainer);

        tvPaperTitle = findViewById(R.id.tvPaperTitle);
        applyPaperTitle();
        indexContainer = findViewById(R.id.indexContainer);
        indexScroll = findViewById(R.id.indexScroll);
        btnToolTime = findViewById(R.id.btnToolTime);
        btnToolWrongbook = findViewById(R.id.btnToolWrongbook);
        btnToolDownload = findViewById(R.id.btnToolDownload);
        btnToolSheet = findViewById(R.id.btnToolSheet);
        btnToolMore = findViewById(R.id.btnToolMore);

        // 操作容器：用于放置判断按钮 / 多选确认按钮
        actionContainer = new LinearLayout(this);
        actionContainer.setOrientation(LinearLayout.VERTICAL);
        actionContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout) questionContent).addView(actionContainer, questionContent.indexOfChild(optionsContainer) + 1);

        pbProgress.setMax(questions.size());

        btnBack.setOnClickListener(v -> onBackPressed());
        btnToolTime.setOnClickListener(v -> toggleFavorite());
        btnToolWrongbook.setOnClickListener(v -> addToWrongBook());
        btnToolDownload.setOnClickListener(v -> showDraftPaper());
        btnToolSheet.setOnClickListener(v -> showAnswerSheet());
        btnToolMore.setOnClickListener(v -> showMorePanel());

        scrollQuestion = findViewById(R.id.scrollQuestion);
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY = 100;
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY) {
                    if (diffX < 0 && index < questions.size() - 1) { index++; renderWithAnimation(true); }
                    else if (diffX > 0 && index > 0) { index--; renderWithAnimation(false); }
                    return true;
                }
                return false;
            }
        });

        startTimer();
        applyFontScale();
        render();
    }

    /**
     * 在 Activity 层统一接收触摸事件并交给手势识别：
     * 图片、选项行、按钮等可点击子控件会独占触摸目标，导致原先挂在 ScrollView 上的
     * 手势监听收不到事件——在图片上左右滑动因此无效（还会被识别成轻点放大）。
     * 改为在此处统一分发，无论手指落在哪里都能识别左右滑动切题，
     * 同时轻点图片仍走原有点击逻辑放大查看。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                swipeGestureBlocked = isInHorizontalScroller(ev);
            }
            if (!swipeGestureBlocked) {
                boolean swiped = gestureDetector.onTouchEvent(ev);
                if (swiped && action == MotionEvent.ACTION_UP) {
                    // 已判定为左右滑动切题：吞掉抬起事件并给子控件补发取消，
                    // 避免图片等子控件同时触发“点击放大”的误触。
                    cancelChildGesture(ev);
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 滑动切题生效时，向被打断的子控件补发 ACTION_CANCEL，清除其按压状态。 */
    private void cancelChildGesture(MotionEvent up) {
        MotionEvent cancel = MotionEvent.obtain(up);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    /** 判断触点是否落在题号索引条等横向滚动区域内（该区域保留自身横向滚动）。 */
    private boolean isInHorizontalScroller(MotionEvent ev) {
        android.view.View bar = findViewById(R.id.indexScroll);
        if (bar == null || bar.getVisibility() != android.view.View.VISIBLE) {
            return false;
        }
        android.graphics.Rect rect = new android.graphics.Rect();
        if (!bar.getGlobalVisibleRect(rect)) {
            return false;
        }
        float x = ev.getRawX();
        float y = ev.getRawY();
        return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
    }

    /**
     * 纸卷若尚无知识点，后台从仓库 _meta/knowledge_map 同步一次，成功后只刷新当前题的标签
     * （不动题目列表与作答状态，避免索引错位）。
     */
    private void maybeSyncKnowledge() {
        if (source == null || !source.startsWith("paper:")) return;
        for (Question q : questions) {
            if (q.knowledgePoints != null && q.knowledgePoints.length > 0) return;
        }
        final String key = source.substring("paper:".length());
        new Thread(() -> {
            final int n = RobotExamBank.applyKnowledgeMap(this, key);
            if (n <= 0 || isFinishing()) return;
            runOnUiThread(() -> {
                java.util.List<Question> fresh = RobotExamBank.getQuestions(this, key);
                java.util.Map<String, String[]> byUid = new java.util.HashMap<>();
                for (Question q : fresh) {
                    if (q.knowledgePoints != null && q.knowledgePoints.length > 0) {
                        byUid.put(q.uniqueKey(), q.knowledgePoints);
                    }
                }
                if (byUid.isEmpty()) return;
                boolean touched = false;
                for (Question q : questions) {
                    String[] kps = byUid.get(q.uniqueKey());
                    if (kps != null) {
                        q.knowledgePoints = kps;
                        touched = true;
                    }
                }
                if (touched && index >= 0 && index < questions.size()) {
                    renderKnowledgeTag(questions.get(index));
                    showToast("已同步本题知识点");
                }
            });
        }).start();
    }

    private List<Question> loadQuestions(String src) {
        if ("custom".equals(src)) {
            return new ArrayList<>(DataStore.getCustomQuestions(this));
        }
        if (src != null && src.startsWith("paper:")) {
            String key = src.substring("paper:".length());
            return new ArrayList<>(RobotExamBank.getQuestions(this, key));
        }
        List<Question> all = QuestionBank.getAll();
        if ("wrong".equals(src) || "favorite".equals(src)) {
            java.util.Set<String> ids = "wrong".equals(src)
                    ? DataStore.getWrongIds(this)
                    : DataStore.getFavoriteIds(this);
            List<Question> result = new ArrayList<>();
            for (Question q : all) if (ids.contains(q.uniqueKey())) result.add(q);
            // 收集真题缓存（RobotExamBank）中的错题/收藏题
            for (RobotExamBank.Paper p : RobotExamBank.getCachedPapersList(this)) {
                for (Question q : p.questions) {
                    if (ids.contains(q.uniqueKey())) result.add(q);
                }
            }
            return result;
        }
        return new ArrayList<>(all);
    }

    // 根据来源设置顶栏标题（试卷名 / 错题本 / 收藏 / 自定义 / 全部）
    private void applyPaperTitle() {
        String title = null;
        if (source != null && source.startsWith("paper:")) {
            String key = source.substring("paper:".length());
            title = getIntent().getStringExtra("paperTitle");
            if (title == null || title.isEmpty()) {
                title = RobotExamBank.getCustomTitle(this, key);
            }
            if (title == null || title.isEmpty()) {
                RobotExamBank.Paper p = RobotExamBank.getPaper(this, key);
                if (p != null) title = p.title;
            }
        }
        if (title == null || title.isEmpty()) {
            // 来源入口控件名称优先（由上游页面通过 PageTitle 传入）
            title = getIntent().getStringExtra(PageTitle.EXTRA);
        }
        if (title == null || title.isEmpty()) {
            if ("wrong".equals(source)) title = "错题本";
            else if ("favorite".equals(source)) title = "收藏题";
            else if ("custom".equals(source)) title = "自定义题库";
            else title = "全部真题";
        }
        if (tvPaperTitle != null) tvPaperTitle.setText(title);
    }
    // 按 uid 快照顺序重排题目（恢复上次进度用）；缺失的题目忽略，未出现在快照中的题追加在末尾
    private List<Question> reorderByUids(List<Question> all, List<String> uids) {
        java.util.Map<String, Question> byUid = new java.util.HashMap<>();
        for (Question q : all) byUid.put(q.uniqueKey(), q);
        List<Question> ordered = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (String uid : uids) {
            Question q = byUid.get(uid);
            if (q != null && used.add(uid)) ordered.add(q);
        }
        for (Question q : all) if (!used.contains(q.uniqueKey())) ordered.add(q);
        return ordered;
    }
    // 保存当前进度快照（uid 列表 + 当前索引 + source + random）
    private void saveResumeNow() {
        List<String> uids = new ArrayList<>();
        for (Question q : questions) uids.add(q.uniqueKey());
        DataStore.saveResume(this, uids, index, source, random);
    }

    private void startTimer() {
        startTime = SystemClock.elapsedRealtime();
        timerRunnable = new Runnable() {
            @Override public void run() {
                elapsed = SystemClock.elapsedRealtime() - startTime;
                handler.postDelayed(this, 500);
            }
        };
        handler.post(timerRunnable);
    }

    private void render() {
        perQuestionStart = elapsed;
        // 首次展示该题时记录起始时间基准（已答题的起始时间在作答时固定，此处不覆盖）
        if (questionStartTimes[index] == 0) {
            questionStartTimes[index] = elapsed;
        }
        Question q = questions.get(index);
        pbProgress.setProgress(index + 1);
        renderIndexBar();
        // 题型标签：优先显示 docx 中的原始题型名称（如「编程题」），否则回退到内部题型
        tvQuestionType.setText(q.typeLabel != null && !q.typeLabel.isEmpty() ? q.typeLabel : q.type);
        renderKnowledgeTag(q);
        renderScore(q);
        tvQuestion.setText((index + 1) + ". " + q.stem);
        renderStemImage(q);
        renderStemBlocks(q);

        optionsContainer.removeAllViews();
        actionContainer.removeAllViews();

        if (q.isJudge()) {
            renderJudge(q);
        } else if (q.isPracticalOnly()) {
            renderPractical(q);
        } else {
            renderOptions(q);
        }

        if (answered[index]) {
            if (brushMode) {
                highlightChosenOnly(q);
                analysisPanel.setVisibility(View.GONE);
            } else {
                highlightOptions(q);
                showAnalysis(q, isCurrentCorrect());
            }
        } else {
            analysisPanel.setVisibility(View.GONE);
        }

        updateToolbarIcons();
        saveResumeNow();
    }
    // 渲染跳题索引条：水平排列题号圆点，区分「当前/已答/未答」，点击跳转
    private void renderIndexBar() {
        indexContainer.removeAllViews();
        final int n = questions.size();
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
            int bg;
            if (i == index) {
                bg = R.drawable.bg_index_current;
                dot.setTextColor(getColor(android.R.color.white));
            } else if (answered[i]) {
                bg = R.drawable.bg_index_done;
                dot.setTextColor(getColor(R.color.accent));
            } else {
                bg = R.drawable.bg_index_blank;
            }
            dot.setBackgroundResource(bg);
            dot.setOnClickListener(v -> {
                index = pos;
                render();
                scrollIndexToCurrent();
            });
            indexContainer.addView(dot);
        }
        indexContainer.post(this::scrollIndexToCurrent);
    }
    // 将当前题号圆点滚动到可视区中央
    private void scrollIndexToCurrent() {
        if (indexContainer == null || indexScroll == null) return;
        View child = indexContainer.getChildAt(index);
        if (child == null) return;
        int targetX = child.getLeft() - (indexScroll.getWidth() - child.getWidth()) / 2;
        if (targetX < 0) targetX = 0;
        indexScroll.smoothScrollTo(targetX, 0);
    }
    // 展示题目知识点标签：多知识点用「/」连接；无知识点则隐藏
    private void renderKnowledgeTag(Question q) {
        if (!DataStore.isKnowledgeTagVisible(this)) {
            tvKnowledgeTag.setVisibility(View.GONE);
            return;
        }
        String[] kps = q.knowledgePoints;
        if (kps == null || kps.length == 0) {
            tvKnowledgeTag.setVisibility(View.GONE);
            return;
        }
        // 注意：知识点名本身可能含「/」（如「简单机械原理 / 杠杆」），
        // 因此数量按数组计，不能用字符串拆分统计。
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : kps) {
            if (s != null && !s.trim().isEmpty()) list.add(s.trim());
        }
        if (list.isEmpty()) {
            tvKnowledgeTag.setVisibility(View.GONE);
            return;
        }
        // 分值旁只显示首要知识点（取叶子名，避免「父 / 子」长串被截断），点击查看全部
        String first = list.get(0);
        int slash = first.lastIndexOf(" / ");
        tvKnowledgeTag.setText(slash >= 0 ? first.substring(slash + 3) : first);
        tvKnowledgeTag.setVisibility(View.VISIBLE);
        final String all = list.size() == 1 ? list.get(0)
                : android.text.TextUtils.join("\n· ", list);
        tvKnowledgeTag.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("本题知识点")
                        .setMessage(all.startsWith("·") || !all.contains("\n") ? all : "· " + all)
                        .setPositiveButton("知道了", null)
                        .show());
    }
    // 展示题目分值：score > 0 时显示"分值：N分"，否则隐藏
    private void renderScore(Question q) {
        if (q.score > 0) {
            tvScore.setText(q.score + "分");
            tvScore.setVisibility(View.VISIBLE);
        } else {
            tvScore.setVisibility(View.GONE);
        }
    }
    private void renderOptions(Question q) {
        int n = q.options == null ? 0 : q.options.length;
        for (int i = 0; i < n; i++) {
            final int opt = i;
            View row = getLayoutInflater().inflate(R.layout.item_option, optionsContainer, false);
            TextView label = row.findViewById(R.id.tvOptionLabel);
            TextView text = row.findViewById(R.id.tvOptionText);
            ImageView img = row.findViewById(R.id.ivOptionImage);
            label.setText(String.valueOf((char) ('A' + i)));

            String imgName = (q.optionImgs != null && i < q.optionImgs.length) ? q.optionImgs[i] : null;
            boolean hasImg = imgName != null && !imgName.isEmpty();
            if (hasImg) {
                text.setVisibility(View.GONE);
                img.setVisibility(View.VISIBLE);
                final android.graphics.Bitmap bmp = loadMediaBitmap(imgName);
                if (bmp != null) {
                    img.setImageBitmap(bmp);
                    img.setOnClickListener(v -> showImageDialog(bmp));
                }
            } else {
                text.setVisibility(View.VISIBLE);
                img.setVisibility(View.GONE);
                text.setText(q.options[i]);
            }

            row.setOnClickListener(v -> onOptionClick(opt));
            optionsContainer.addView(row);
        }

        // 多选题：显示"确认答案"按钮
        if (q.isMulti() && !answered[index]) {
            TextView btnConfirm = new TextView(this);
            btnConfirm.setText("确认答案");
            btnConfirm.setTextSize(16);
            btnConfirm.setTextColor(getColor(android.R.color.white));
            btnConfirm.setGravity(Gravity.CENTER);
            btnConfirm.setPadding(0, dp(12), 0, dp(12));
            btnConfirm.setBackgroundResource(R.drawable.bg_option_correct);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(16);
            btnConfirm.setLayoutParams(lp);
            btnConfirm.setOnClickListener(v -> confirmMultiAnswer());
            actionContainer.addView(btnConfirm);
        }
    }

    private void renderJudge(Question q) {
        // 判断题为两个按钮：正确 / 错误
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView btnTrue = makeJudgeButton("正确", 0);
        TextView btnFalse = makeJudgeButton("错误", 1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(8);
        btnTrue.setLayoutParams(lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(8);
        btnFalse.setLayoutParams(lp2);
        row.addView(btnTrue);
        row.addView(btnFalse);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(16);
        row.setLayoutParams(rlp);
        actionContainer.addView(row);
    }

    private void renderPractical(Question q) {
        // 实操题：无客观选项，显示「标记完成」按钮作为作答入口
        if (answered[index]) return;
        TextView btnDone = new TextView(this);
        btnDone.setText("标记完成");
        btnDone.setTextSize(16);
        btnDone.setTextColor(getColor(android.R.color.white));
        btnDone.setGravity(Gravity.CENTER);
        btnDone.setPadding(0, dp(14), 0, dp(14));
        btnDone.setBackgroundResource(R.drawable.bg_option_correct);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        btnDone.setLayoutParams(lp);
        btnDone.setOnClickListener(v -> markPracticalDone());
        actionContainer.addView(btnDone);
    }
    private void markPracticalDone() {
        Question q = questions.get(index);
        if (answered[index]) return;
        userAnswers[index] = -1;
        answered[index] = true;
        questionDurations[index] = elapsed - questionStartTimes[index];
        boolean correct = isCurrentCorrect();
        DataStore.recordAnswer(this, q.uniqueKey(), correct, q.type);
        DataStore.setFirstAnswer(this, q.uniqueKey(), -1);
        if (correct) handleCorrectAnswer(q); else handleWrongAnswer(q);
        if (brushMode) {
            highlightChosenOnly(q);
        } else {
            showAnalysis(q, correct);
        }
        if (correct && !brushMode && DataStore.isAutoNext(this) && index < questions.size() - 1) {
            final int curIndex = index;
            handler.postDelayed(() -> {
                if (index == curIndex && !isFinishing()) { index++; renderWithAnimation(true); }
            }, Math.max(600, DataStore.getAnimDuration(this) + 400));
        }
    }
    private TextView makeJudgeButton(String text, final int val) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(16);
        btn.setTextColor(getColor(R.color.text_main));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(14), 0, dp(14));
        btn.setBackgroundResource(R.drawable.bg_option_normal);
        btn.setOnClickListener(v -> onJudgeClick(val));
        return btn;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void onOptionClick(int opt) {
        Question q = questions.get(index);
        if (answered[index]) return;

        if (q.isMulti()) {
            toggleMultiSelect(opt);
            return;
        }

        // 单选 / 实操等
        userAnswers[index] = opt;
        answered[index] = true;
        questionDurations[index] = elapsed - questionStartTimes[index];
        boolean correct = isCurrentCorrect();
        DataStore.recordAnswer(this, q.uniqueKey(), correct, q.type);
        DataStore.setFirstAnswer(this, q.uniqueKey(), opt);
        if (correct) handleCorrectAnswer(q); else handleWrongAnswer(q);
        if (brushMode) {
            highlightChosenOnly(q);
        } else {
            highlightOptions(q);
            showAnalysis(q, correct);
        }
        if (correct && !brushMode && DataStore.isAutoNext(this) && index < questions.size() - 1) {
            final int curIndex = index;
            handler.postDelayed(() -> {
                if (index == curIndex && !isFinishing()) { index++; renderWithAnimation(true); }
            }, Math.max(600, DataStore.getAnimDuration(this) + 400));
        }
    }

    private void toggleMultiSelect(int opt) {
        Question q = questions.get(index);
        int[] cur = multiAnswers[index];
        java.util.Set<Integer> set = new java.util.HashSet<>();
        if (cur != null) for (int v : cur) set.add(v);
        if (set.contains(opt)) set.remove(opt); else set.add(opt);
        java.util.List<Integer> sorted = new ArrayList<>(set);
        Collections.sort(sorted);
        int[] arr = new int[sorted.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = sorted.get(i);
        multiAnswers[index] = arr;
        highlightMultiSelection(q);
    }

    private void confirmMultiAnswer() {
        Question q = questions.get(index);
        int[] sel = multiAnswers[index];
        if (sel == null || sel.length == 0) {
            showToast("请先选择答案");
            return;
        }
        answered[index] = true;
        questionDurations[index] = elapsed - questionStartTimes[index];
        boolean correct = isCurrentCorrect();
        DataStore.recordAnswer(this, q.uniqueKey(), correct, q.type);
        if (correct) handleCorrectAnswer(q); else handleWrongAnswer(q);
        if (brushMode) {
            highlightChosenOnly(q);
        } else {
            highlightOptions(q);
            showAnalysis(q, correct);
        }
    }

    private void onJudgeClick(int val) {
        Question q = questions.get(index);
        if (answered[index]) return;
        userAnswers[index] = val;
        answered[index] = true;
        questionDurations[index] = elapsed - questionStartTimes[index];
        boolean correct = isCurrentCorrect();
        DataStore.recordAnswer(this, q.uniqueKey(), correct, q.type);
        DataStore.setFirstAnswer(this, q.uniqueKey(), val);
        if (correct) handleCorrectAnswer(q); else handleWrongAnswer(q);
        if (brushMode) {
            highlightChosenOnly(q);
        } else {
            highlightOptions(q);
            showAnalysis(q, correct);
        }
    }

    private boolean isCurrentCorrect() {
        Question q = questions.get(index);
        if (!q.hasAnswer) return true;
        if (q.isJudge()) {
            String expect = q.judgeAnswer == null ? "" : q.judgeAnswer;
            String got = userAnswers[index] == 0 ? "正确" : (userAnswers[index] == 1 ? "错误" : "");
            return expect.equals(got);
        }
        if (q.isMulti()) {
            int[] sel = multiAnswers[index];
            String[] expect = q.answerIndexes;
            if (sel == null || expect == null) return false;
            if (sel.length != expect.length) return false;
            java.util.Set<String> exp = new java.util.HashSet<>();
            for (String s : expect) exp.add(s.trim());
            for (int v : sel) if (!exp.contains(String.valueOf(v))) return false;
            return true;
        }
        return userAnswers[index] == q.answerIndex;
    }

    private void highlightOptions(Question q) {
        if (q.isJudge()) {
            highlightJudge(q);
            return;
        }
        int colorDefault = getColor(R.color.text_main);
        int colorCorrect = getColor(R.color.answer_correct_text);
        int colorWrong = getColor(R.color.answer_wrong_text);

        java.util.Set<Integer> correctIdx = new java.util.HashSet<>();
        if (q.isMulti()) {
            if (q.answerIndexes != null) for (String s : q.answerIndexes) {
                try { correctIdx.add(Integer.parseInt(s.trim())); } catch (Exception e) {}
            }
        } else {
            correctIdx.add(q.answerIndex);
        }
        java.util.Set<Integer> chosenIdx = new java.util.HashSet<>();
        if (q.isMulti()) {
            if (multiAnswers[index] != null) for (int v : multiAnswers[index]) chosenIdx.add(v);
        } else {
            chosenIdx.add(userAnswers[index]);
        }

        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            LinearLayout root = row.findViewById(R.id.optionRoot);
            TextView label = row.findViewById(R.id.tvOptionLabel);
            TextView text = row.findViewById(R.id.tvOptionText);
            ImageView img = row.findViewById(R.id.ivOptionImage);
            int bg;
            int textColor;
            if (correctIdx.contains(i)) {
                bg = R.drawable.bg_option_correct;
                textColor = colorCorrect;
            } else if (chosenIdx.contains(i)) {
                bg = R.drawable.bg_option_wrong;
                textColor = colorWrong;
            } else {
                bg = R.drawable.bg_option_normal;
                textColor = colorDefault;
            }
            if (root != null) root.setBackgroundResource(bg);
            else row.setBackgroundResource(bg);
            label.setTextColor(textColor);
            text.setTextColor(textColor);
        }
    }

    /** 刷题模式下高亮：仅标记用户已选，不区分对错、不显示正确答案。 */
    private void highlightChosenOnly(Question q) {
        if (q.isJudge()) {
            int chosenVal = userAnswers[index];
            for (int i = 0; i < actionContainer.getChildCount(); i++) {
                View child = actionContainer.getChildAt(i);
                if (child instanceof LinearLayout) {
                    LinearLayout row = (LinearLayout) child;
                    for (int j = 0; j < row.getChildCount(); j++) {
                        View btn = row.getChildAt(j);
                        if (btn instanceof TextView) {
                            TextView tv = (TextView) btn;
                            boolean isTrueBtn = "正确".equals(tv.getText().toString());
                            int thisVal = isTrueBtn ? 0 : 1;
                            if (thisVal == chosenVal) {
                                tv.setBackgroundResource(R.drawable.bg_option_correct);
                                tv.setTextColor(getColor(R.color.primary));
                            } else {
                                tv.setBackgroundResource(R.drawable.bg_option_normal);
                                tv.setTextColor(getColor(R.color.text_main));
                            }
                        }
                    }
                }
            }
            return;
        }
        int colorSelected = getColor(R.color.primary);
        int colorDefault = getColor(R.color.text_main);
        java.util.Set<Integer> sel = new java.util.HashSet<>();
        if (q.isMulti()) {
            if (multiAnswers[index] != null) for (int v : multiAnswers[index]) sel.add(v);
        } else {
            sel.add(userAnswers[index]);
        }
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            LinearLayout root = row.findViewById(R.id.optionRoot);
            TextView label = row.findViewById(R.id.tvOptionLabel);
            TextView text = row.findViewById(R.id.tvOptionText);
            if (sel.contains(i)) {
                if (root != null) root.setBackgroundResource(R.drawable.bg_option_correct);
                label.setTextColor(colorSelected);
                text.setTextColor(colorSelected);
            } else {
                if (root != null) root.setBackgroundResource(R.drawable.bg_option_normal);
                label.setTextColor(colorDefault);
                text.setTextColor(colorDefault);
            }
        }
    }
    private void highlightMultiSelection(Question q) {
        // 多选作答前的高亮：仅标记选中项，不判分
        int colorSelected = getColor(R.color.primary);
        int colorDefault = getColor(R.color.text_main);
        java.util.Set<Integer> sel = new java.util.HashSet<>();
        if (multiAnswers[index] != null) for (int v : multiAnswers[index]) sel.add(v);
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            View row = optionsContainer.getChildAt(i);
            LinearLayout root = row.findViewById(R.id.optionRoot);
            TextView label = row.findViewById(R.id.tvOptionLabel);
            TextView text = row.findViewById(R.id.tvOptionText);
            if (sel.contains(i)) {
                if (root != null) root.setBackgroundResource(R.drawable.bg_option_correct);
                label.setTextColor(colorSelected);
                text.setTextColor(colorSelected);
            } else {
                if (root != null) root.setBackgroundResource(R.drawable.bg_option_normal);
                label.setTextColor(colorDefault);
                text.setTextColor(colorDefault);
            }
        }
    }

    private void highlightJudge(Question q) {
        // 判断按钮高亮：正确的答案按钮标绿，用户选的标红
        String expect = q.judgeAnswer == null ? "" : q.judgeAnswer;
        int expectVal = "正确".equals(expect) ? 0 : ("错误".equals(expect) ? 1 : -1);
        int chosenVal = userAnswers[index];
        for (int i = 0; i < actionContainer.getChildCount(); i++) {
            View child = actionContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View btn = row.getChildAt(j);
                    if (btn instanceof TextView) {
                        TextView tv = (TextView) btn;
                        boolean isTrueBtn = "正确".equals(tv.getText().toString());
                        int thisVal = isTrueBtn ? 0 : 1;
                        int bg;
                        int tc;
                        if (thisVal == expectVal) {
                            bg = R.drawable.bg_option_correct;
                            tc = getColor(R.color.answer_correct_text);
                        } else if (thisVal == chosenVal) {
                            bg = R.drawable.bg_option_wrong;
                            tc = getColor(R.color.answer_wrong_text);
                        } else {
                            bg = R.drawable.bg_option_normal;
                            tc = getColor(R.color.text_main);
                        }
                        tv.setBackgroundResource(bg);
                        tv.setTextColor(tc);
                    }
                }
            }
        }
    }

    private void showAnalysis(Question q, boolean correct) {
        analysisPanel.setVisibility(View.VISIBLE);
        String correctStr = formatCorrectAnswer(q);
        tvResultText.setText(correct ? "回答正确" : "回答错误");
        tvResultText.setTextColor(correct
                ? getColor(R.color.answer_correct_text)
                : getColor(R.color.answer_wrong_text));
        tvAnswerText.setText("正确答案：" + correctStr);
        // 解析文本默认折叠：显示「解析」按钮，点击展开/收起
        final String analysisText = q.analysis == null ? "" : q.analysis.trim();
        tvAnalysisText.setText(markdownRenderer.render(analysisText));
        tvAnalysisText.setVisibility(View.GONE);
        if (analysisText.isEmpty()) {
            btnShowAnalysis.setVisibility(View.GONE);
        } else {
            btnShowAnalysis.setVisibility(View.VISIBLE);
            btnShowAnalysis.setText("解析");
            btnShowAnalysis.setOnClickListener(v -> {
                boolean show = tvAnalysisText.getVisibility() != View.VISIBLE;
                tvAnalysisText.setVisibility(show ? View.VISIBLE : View.GONE);
                btnShowAnalysis.setText(show ? "收起解析" : "解析");
            });
        }
        // 答题框内不再重复展示题目图片（图片只在题干区显示一次）
        if (analysisImagesContainer != null) {
            analysisImagesContainer.removeAllViews();
            analysisImagesContainer.setVisibility(View.GONE);
        }
        // AI 解析区：重置 + 缓存优先恢复 + 自动触发判断
        resetAiAnalysis();
        if (hasAiAnalysisCached(q)) {
            showAiAnalysis(DataStore.getAiAnalysis(this, q.uniqueKey()));
        }
        maybeAutoAiAnalysis(q, correct);
    }
    // 重置 AI 解析区显示（不删除缓存）
    private void resetAiAnalysis() {
        tvAiAnalysis.setVisibility(View.GONE);
        tvAiAnalysis.setText("");
    }
    private boolean hasAiAnalysisCached(Question q) {
        return DataStore.hasAiAnalysis(this, q.uniqueKey());
    }
    // 显示 AI 解析结果（含提示文案「AI 解析，酌情参考」）
    private void showAiAnalysis(String text) {
        if (text == null || text.trim().isEmpty()) {
            tvAiAnalysis.setVisibility(View.GONE);
            tvAiAnalysis.setText("");
            return;
        }
        tvAiAnalysis.setText(markdownRenderer.render("AI 解析，酌情参考：\n" + text.trim()));
        tvAiAnalysis.setVisibility(View.VISIBLE);
    }
    // 自动解析：根据设置开关与模式决定是否自动触发
    private void maybeAutoAiAnalysis(Question q, boolean correct) {
        if (!DataStore.getAutoAiAnalysis(this)) return;
        String mode = DataStore.getAutoAiAnalysisMode(this);
        if ("wrong".equals(mode) && correct) return; // 只对错题解析时，答对不触发
        triggerAiAnalysis(q, correct);
    }
    // 手动触发（按钮点击）
    private void onAiAnalysisClick() {
        Question q = questions.get(index);
        boolean correct = isCurrentCorrect();
        triggerAiAnalysis(q, correct);
    }
    // 触发 AI 解析：缓存优先，无线程阻塞地后台调用
    private void triggerAiAnalysis(Question q, boolean correct) {
        // 缓存优先：已生成过则直接展示
        if (hasAiAnalysisCached(q)) {
            showAiAnalysis(DataStore.getAiAnalysis(this, q.uniqueKey()));
            return;
        }
        AiApi.Config cfg = DataStore.getCurrentAiConfig(this);
        if (cfg == null || !cfg.isComplete()) {
            showToast("请先在「AI 模型配置」中设置模型和 API Key");
            return;
        }
        String userAnswerText = formatUserAnswer(q);
        tvAiAnalysis.setVisibility(View.VISIBLE);
        tvAiAnalysis.setText("AI 解析生成中…");
        new Thread(() -> {
            AiApi.Result r = AiApi.analyzeQuestion(this, cfg, q, userAnswerText, correct);
            runOnUiThread(() -> {
                if (r != null && r.ok) {
                    DataStore.setAiAnalysis(this, q.uniqueKey(), r.text);
                    showAiAnalysis(r.text);
                } else {
                    tvAiAnalysis.setText("AI 解析失败：" + (r == null ? "未知错误" : r.text));
                    tvAiAnalysis.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }
    // 格式化用户答案为可读文本（用于 prompt）
    private String formatUserAnswer(Question q) {
        if (q.isJudge()) {
            int v = userAnswers[index];
            return v == 0 ? "正确" : (v == 1 ? "错误" : "未作答");
        }
        if (q.isMulti()) {
            int[] sel = multiAnswers[index];
            if (sel == null || sel.length == 0) return "未作答";
            StringBuilder sb = new StringBuilder();
            for (int v : sel) {
                if (sb.length() > 0) sb.append("、");
                sb.append((char) ('A' + v));
            }
            return sb.toString();
        }
        int v = userAnswers[index];
        if (v < 0) return "未作答";
        return String.valueOf((char) ('A' + v));
    }
    private void renderAnalysisImages(Question q) {
        // 答案解析区：展示题目/选项中的图片，便于回顾
        analysisImagesContainer.removeAllViews();
        boolean hasStem = q.stemImg != null && !q.stemImg.isEmpty();
        boolean hasOpt = q.hasOptionImages();
        if (!hasStem && !hasOpt) {
            analysisImagesContainer.setVisibility(View.GONE);
            return;
        }
        analysisImagesContainer.setVisibility(View.VISIBLE);
        if (hasStem) {
            android.graphics.Bitmap bmp = loadMediaBitmap(q.stemImg);
            if (bmp != null) {
                analysisImagesContainer.addView(makeAnalysisImage(bmp, null));
            }
        }
        if (hasOpt) {
            int n = q.options == null ? 0 : q.options.length;
            for (int i = 0; i < n; i++) {
                String imgName = (q.optionImgs != null && i < q.optionImgs.length) ? q.optionImgs[i] : null;
                if (imgName == null || imgName.isEmpty()) continue;
                android.graphics.Bitmap bmp = loadMediaBitmap(imgName);
                if (bmp != null) {
                    analysisImagesContainer.addView(makeAnalysisImage(bmp, String.valueOf((char) ('A' + i))));
                }
            }
        }
    }
    private View makeAnalysisImage(final android.graphics.Bitmap bmp, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);
        if (label != null) {
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(getColor(R.color.text_main));
            tv.setTextSize(14);
            tv.setBackgroundResource(R.drawable.bg_tag_gray);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(28), dp(28));
            tlp.rightMargin = dp(12);
            row.addView(tv, tlp);
        }
        ImageView iv = new ImageView(this);
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
    private String formatCorrectAnswer(Question q) {
        if (!q.hasAnswer) return "—";
        if (q.isJudge()) return q.judgeAnswer == null ? "" : q.judgeAnswer;
        if (q.isMulti()) {
            if (q.answerIndexes == null) return "";
            StringBuilder sb = new StringBuilder();
            for (String s : q.answerIndexes) {
                int idx = 0;
                try { idx = Integer.parseInt(s.trim()); } catch (Exception e) {}
                if (sb.length() > 0) sb.append("、");
                sb.append((char) ('A' + idx));
            }
            return sb.toString();
        }
        return String.valueOf((char) ('A' + q.answerIndex));
    }

    private void addToWrongBook() {
        Question q = questions.get(index);
        java.util.Set<String> wrongIds = DataStore.getWrongIds(this);
        String id = q.uniqueKey();
        if (wrongIds.contains(id)) {
            DataStore.removeWrong(this, q.uniqueKey());
            DataStore.resetCorrectCount(this, q.uniqueKey());
            showToast("已从错题本移除");
        } else {
            DataStore.addWrong(this, q.uniqueKey());
            showToast("已加入错题本");
        }
        updateToolbarIcons();
    }

    // 答对时的错题本处理：递增做对次数，达到阈值后自动移除并复位计数（仅对已在错题本中的题生效）
    private void handleCorrectAnswer(Question q) {
        if (!DataStore.getWrongIds(this).contains(q.uniqueKey())) return;
        DataStore.incCorrectCount(this, q.uniqueKey());
        if (DataStore.getCorrectCount(this, q.uniqueKey()) >= DataStore.getWrongThreshold(this)) {
            DataStore.removeWrong(this, q.uniqueKey());
            DataStore.resetCorrectCount(this, q.uniqueKey());
        }
    }

    // 答错时的错题本处理：检查「自动加入」开关，开启时将题加入错题本并重置做对次数
    private void handleWrongAnswer(Question q) {
        if (!DataStore.isAutoWrong(this)) return;
        DataStore.addWrong(this, q.uniqueKey());
        DataStore.resetCorrectCount(this, q.uniqueKey());
    }

    private void renderWithAnimation(boolean toNext) {
        int inAnim = toNext ? R.anim.slide_in_right : R.anim.slide_in_left;
        int duration = DataStore.getAnimDuration(this);
        // 先同步渲染新题目内容，再播放入屏动画，避免出屏结束到 render 完成之间的空白帧，实现无缝切换
        render();
        Animation in = AnimationUtils.loadAnimation(this, inAnim);
        in.setDuration(duration);
        questionContent.startAnimation(in);
    }

    private void showToast(String msg) {
        int duration = DataStore.getToastDuration(this);
        android.widget.Toast.makeText(this, msg,
                duration >= 3500 ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT).show();
    }

    private void toggleFavorite() {
        Question q = questions.get(index);
        boolean wasFavorite = DataStore.isFavorite(this, q.uniqueKey());
        DataStore.toggleFavorite(this, q.uniqueKey());
        showToast(wasFavorite ? "已取消收藏" : "已收藏本题");
        updateToolbarIcons();
    }

    private void showQuestionStat() {
        Question q = questions.get(index);
        int[] st = DataStore.getQuestionStat(this, q.uniqueKey());
        int total = st[0], right = st[1], wrong = total - right;
        TextView tv = new TextView(this);
        tv.setText("已做 " + total + " 次，答对 " + right + " 次，答错 " + wrong + " 次");
        tv.setTextSize(13);
        tv.setTextColor(getColor(R.color.text_main));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.card_bg));
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), getColor(R.color.divider));
        tv.setBackground(bg);
        PopupWindow pw = new PopupWindow(tv, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setFocusable(true);
        pw.showAsDropDown(btnStat, 0, dp(6));
    }
    private void showDraftPaper() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = getLayoutInflater().inflate(R.layout.dialog_draft_paper, null);
        d.setContentView(v);
        DrawPadView drawPad = v.findViewById(R.id.drawPad);
        TextView btnDraftClear = v.findViewById(R.id.btnDraftClear);
        TextView btnDraftSave = v.findViewById(R.id.btnDraftSave);
        TextView btnDraftClose = v.findViewById(R.id.btnDraftClose);
        View draftRoot = v.findViewById(R.id.draftRoot);
        SeekBar sbDraftAlpha = v.findViewById(R.id.sbDraftAlpha);
        TextView tvDraftAlphaValue = v.findViewById(R.id.tvDraftAlphaValue);
        // 打开时恢复上次设置的草稿纸透明度
        final int[] alphaPercent = {DataStore.getDraftAlpha(this)};
        applyDraftAlpha(draftRoot, tvDraftAlphaValue, sbDraftAlpha, alphaPercent[0]);
        sbDraftAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaPercent[0] = progress;
                applyDraftAlpha(draftRoot, tvDraftAlphaValue, seekBar, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                DataStore.setDraftAlpha(QuizActivity.this, seekBar.getProgress());
            }
        });
        // 打开时恢复本题已有草稿
        ArrayList<DrawPadView.StrokeData> saved = draftByQuestion.get(index);
        if (saved != null) {
            drawPad.restoreStrokes(saved);
        }
        btnDraftClear.setOnClickListener(x -> drawPad.clear());
        btnDraftSave.setOnClickListener(x -> {
            draftByQuestion.put(index, drawPad.getStrokeData());
            showToast("草稿已保存");
        });
        btnDraftClose.setOnClickListener(x -> {
            // 关闭时自动保存本题草稿
            draftByQuestion.put(index, drawPad.getStrokeData());
            DataStore.setDraftAlpha(this, alphaPercent[0]);
            d.dismiss();
        });
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        d.show();
    }

    /** 按百分比设置草稿纸底色不透明度：0% 完全透明（只看笔迹），100% 完全不透明。 */
    private void applyDraftAlpha(View draftRoot, TextView valueView, SeekBar seekBar, int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        int alpha = Math.round(percent * 255f / 100f);
        if (draftRoot != null) {
            draftRoot.setBackgroundColor(android.graphics.Color.argb(alpha, 255, 255, 255));
        }
        if (valueView != null) {
            valueView.setText(percent + "%");
        }
        if (seekBar != null && seekBar.getProgress() != percent) {
            seekBar.setProgress(percent);
        }
    }

    /** 返回上一页需要二次确认：首次返回给出 toast 提示，2 秒内再次返回才真正退出。 */
    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBackPressedTime > BACK_CONFIRM_INTERVAL) {
            lastBackPressedTime = now;
            showToast("再按一次返回上一页");
            return;
        }
        super.onBackPressed();
    }
    private void showMorePanel() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = getLayoutInflater().inflate(R.layout.dialog_more_panel, null);
        d.setContentView(v);

        TextView btnFontSmall = v.findViewById(R.id.btnFontSmall);
        TextView btnFontMedium = v.findViewById(R.id.btnFontMedium);
        TextView btnFontLarge = v.findViewById(R.id.btnFontLarge);
        TextView btnThemeDay = v.findViewById(R.id.btnThemeDay);
        TextView btnThemeNight = v.findViewById(R.id.btnThemeNight);
        TextView btnModeRecite = v.findViewById(R.id.btnModeRecite);
        TextView btnModeBrush = v.findViewById(R.id.btnModeBrush);
        TextView tvTotalTime = v.findViewById(R.id.tvTotalTime);
        TextView btnMoreClose = v.findViewById(R.id.btnMoreClose);
        btnFontSmall.setOnClickListener(x -> { DataStore.setQuestionFontSp(this, 15f); applyFontScale(); updateFontButtons(btnFontSmall, btnFontMedium, btnFontLarge); });
        btnFontMedium.setOnClickListener(x -> { DataStore.setQuestionFontSp(this, 18f); applyFontScale(); updateFontButtons(btnFontSmall, btnFontMedium, btnFontLarge); });
        btnFontLarge.setOnClickListener(x -> { DataStore.setQuestionFontSp(this, 22f); applyFontScale(); updateFontButtons(btnFontSmall, btnFontMedium, btnFontLarge); });
        updateFontButtons(btnFontSmall, btnFontMedium, btnFontLarge);
        btnThemeDay.setOnClickListener(x -> { DataStore.setDarkMode(this, 1); AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); });
        btnThemeNight.setOnClickListener(x -> { DataStore.setDarkMode(this, 2); AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); });
        updateThemeButtons(btnThemeDay, btnThemeNight);
        btnModeRecite.setOnClickListener(x -> { brushMode = false; updateModeButtons(btnModeRecite, btnModeBrush); showToast("已切换为背题模式，点击选项后立即显示答案"); render(); });
        btnModeBrush.setOnClickListener(x -> { brushMode = true; updateModeButtons(btnModeRecite, btnModeBrush); showToast("已切换为刷题模式，交卷前不显示答案"); render(); });
        updateModeButtons(btnModeRecite, btnModeBrush);
        // 知识点标签显示/隐藏
        androidx.appcompat.widget.SwitchCompat swKnowledgeTag = v.findViewById(R.id.swKnowledgeTag);
        swKnowledgeTag.setChecked(DataStore.isKnowledgeTagVisible(this));
        swKnowledgeTag.setOnCheckedChangeListener((b, checked) -> {
            DataStore.setKnowledgeTagVisible(this, checked);
            renderKnowledgeTag(questions.get(index));
            showToast(checked ? "已显示知识点标签" : "已隐藏知识点标签");
        });
        // 做题用时实时刷新
        if (moreTimeRunnable != null) handler.removeCallbacks(moreTimeRunnable);
        moreTimeRunnable = new Runnable() {
            @Override public void run() {
                long sec = elapsed / 1000;
                long m = sec / 60, s = sec % 60;
                tvTotalTime.setText(String.format("%02d:%02d", m, s));
                handler.postDelayed(this, 500);
            }
        };
        handler.post(moreTimeRunnable);
        btnMoreClose.setOnClickListener(x -> d.dismiss());
        d.setOnDismissListener(x -> { if (moreTimeRunnable != null) handler.removeCallbacks(moreTimeRunnable); });

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        d.show();
    }

    private void updateToolbarIcons() {
        Question q = questions.get(index);
        btnToolTime.setImageResource(DataStore.isFavorite(this, q.uniqueKey())
                ? R.drawable.ic_tool_favorite_filled : R.drawable.ic_tool_favorite);
        boolean inWrong = DataStore.getWrongIds(this).contains(q.uniqueKey());
        // 未加入时显示「加入错题本」图标，已加入时显示「移出错题本」图标，与点击后的行为一致
        btnToolWrongbook.setImageResource(inWrong
                ? R.drawable.ic_tool_wrongbook_remove : R.drawable.ic_tool_wrongbook);
    }

    private void applyFontScale() {
        float q = DataStore.getQuestionFontSp(this);
        float a = q - 4f;
        if (a < 11f) a = 11f;
        tvQuestion.setTextSize(q);
        tvAnalysisText.setTextSize(a);
        for (TextView t : stemTextViews) {
            t.setTextSize(q);
        }
    }
    private void updateThemeButtons(TextView btnThemeDay, TextView btnThemeNight) {
        int mode = DataStore.getDarkMode(this);
        btnThemeDay.setBackgroundResource(mode == 1 ? R.drawable.bg_option_correct : R.drawable.bg_option_normal);
        btnThemeNight.setBackgroundResource(mode == 2 ? R.drawable.bg_option_correct : R.drawable.bg_option_normal);
    }
    private void updateFontButtons(TextView btnFontSmall, TextView btnFontMedium, TextView btnFontLarge) {
        float cur = DataStore.getQuestionFontSp(this);
        int idx = cur >= 20f ? 2 : (cur >= 16.5f ? 1 : 0);
        TextView[] arr = {btnFontSmall, btnFontMedium, btnFontLarge};
        for (int i = 0; i < arr.length; i++) {
            if (i == idx) arr[i].setBackgroundResource(R.drawable.bg_option_correct);
            else arr[i].setBackgroundResource(R.drawable.bg_option_normal);
            arr[i].setTextColor(i == idx ? getColor(android.R.color.white) : getColor(R.color.text_main));
        }
    }
    private void updateModeButtons(TextView btnModeRecite, TextView btnModeBrush) {
        btnModeRecite.setBackgroundResource(!brushMode ? R.drawable.bg_option_correct : R.drawable.bg_option_normal);
        btnModeBrush.setBackgroundResource(brushMode ? R.drawable.bg_option_correct : R.drawable.bg_option_normal);
        int selected = getColor(android.R.color.white);
        int normal = getColor(R.color.text_main);
        btnModeRecite.setTextColor(!brushMode ? selected : normal);
        btnModeBrush.setTextColor(brushMode ? selected : normal);
    }
    private void applyTheme() {
        View root = findViewById(android.R.id.content);
        View questionContentView = questionContent;
        if (nightMode) {
            root.setBackgroundColor(getColor(R.color.bg_page_dark));
            questionContentView.setBackgroundColor(getColor(R.color.bg_page_dark));
            tvQuestion.setTextColor(getColor(R.color.text_main_dark));
            analysisPanel.setBackgroundColor(getColor(R.color.card_bg_dark));
            tvAnalysisText.setTextColor(getColor(R.color.text_main_dark));
        } else {
            root.setBackgroundColor(getColor(R.color.bg_page));
            questionContentView.setBackgroundColor(getColor(R.color.bg_page));
            tvQuestion.setTextColor(getColor(R.color.text_main));
            analysisPanel.setBackgroundColor(getColor(R.color.card_bg));
            tvAnalysisText.setTextColor(getColor(R.color.text_main));
        }
    }


    private void showAnswerSheet() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = getLayoutInflater().inflate(R.layout.dialog_answer_sheet, null);
        d.setContentView(v);

        TextView progress = v.findViewById(R.id.tvSheetProgress);
        GridView grid = v.findViewById(R.id.gridSheet);
        TextView btnClose = v.findViewById(R.id.btnSheetClose);
        TextView btnSubmit = v.findViewById(R.id.btnSheetSubmit);

        int done = 0;
        for (boolean b : answered) if (b) done++;
        progress.setText("已答 " + done + "/" + questions.size() + " 题");

        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return questions.size(); }
            @Override public Object getItem(int p) { return null; }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View c, ViewGroup parent) {
                if (c == null) c = getLayoutInflater().inflate(R.layout.item_sheet_cell, parent, false);
                TextView cell = c.findViewById(R.id.tvSheetCell);
                cell.setText(String.valueOf(p + 1));
                int bg;
                boolean doneHere = answered[p];
                boolean wrongHere = doneHere && !isCorrectAt(p);
                if (p == index) bg = R.drawable.bg_sheet_current;
                else if (wrongHere) bg = R.drawable.bg_sheet_marked;
                else if (doneHere) bg = R.drawable.bg_sheet_done;
                else bg = R.drawable.bg_sheet_blank;
                cell.setBackgroundResource(bg);
                cell.setTextColor(doneHere
                        ? getColor(R.color.sheet_cell_text_done)
                        : getColor(R.color.sheet_cell_text_blank));
                return c;
            }
        });
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            index = pos;
            d.dismiss();
            render();
        });

        btnClose.setOnClickListener(x -> d.dismiss());
        btnSubmit.setOnClickListener(x -> { d.dismiss(); submit(); });

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        d.show();
    }

    private boolean isCorrectAt(int p) {
        Question q = questions.get(p);
        if (!answered[p]) return false;
        if (!q.hasAnswer) return true;
        if (q.isJudge()) {
            String expect = q.judgeAnswer == null ? "" : q.judgeAnswer;
            String got = userAnswers[p] == 0 ? "正确" : (userAnswers[p] == 1 ? "错误" : "");
            return expect.equals(got);
        }
        if (q.isMulti()) {
            int[] sel = multiAnswers[p];
            String[] expect = q.answerIndexes;
            if (sel == null || expect == null) return false;
            if (sel.length != expect.length) return false;
            java.util.Set<String> exp = new java.util.HashSet<>();
            for (String s : expect) exp.add(s.trim());
            for (int v : sel) if (!exp.contains(String.valueOf(v))) return false;
            return true;
        }
        return userAnswers[p] == q.answerIndex;
    }

    private void submit() {
        DataStore.clearResume(this); // 交卷后清除上次进度快照
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            if (isCorrectAt(i)) correct++;
        }
        long usedSec = elapsed / 1000;
        List<AnswerRecord> records = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            int ua = answered[i] ? userAnswers[i] : -1;
            AnswerRecord r = new AnswerRecord(q, ua);
            r.userAnswerIndexes = multiAnswers[i];
            records.add(r);
        }
        // 交卷后清除全部草稿
        draftByQuestion.clear();
        android.content.Intent intent = new android.content.Intent(this, ResultActivity.class);
        intent.putExtra("total", questions.size());
        intent.putExtra("correct", correct);
        intent.putExtra("duration_sec", usedSec);
        intent.putExtra("records", (java.io.Serializable) records);
        startActivity(intent);
        finish();
    }

    private void renderStemImage(Question q) {
        String imgName = q.stemImg;
        boolean hasImg = imgName != null && !imgName.isEmpty();
        if (hasImg) {
            final android.graphics.Bitmap bmp = loadMediaBitmap(imgName);
            if (bmp != null) {
                ivStemImage.setVisibility(View.VISIBLE);
                ivStemImage.setImageBitmap(bmp);
                ivStemImage.setOnClickListener(v -> showImageDialog(bmp));
            } else {
                ivStemImage.setVisibility(View.GONE);
            }
        } else {
            ivStemImage.setVisibility(View.GONE);
        }
    }

    /**
     * 按 docx 原始顺序渲染题干：文字块用 TextView、图片块用 ImageView，支持「一段文字 + 多张图片」混排。
     * 有 stemBlocks 时接管题干展示（隐藏 tvQuestion/ivStemImage），否则保持旧版「文字 + 单图」排版。
     */
    private void renderStemBlocks(Question q) {
        if (stemContainer == null) return;
        stemContainer.removeAllViews();
        stemTextViews.clear();
        List<Question.StemBlock> blocks = q.stemBlocks;
        if (blocks == null || blocks.isEmpty()) {
            stemContainer.setVisibility(View.GONE);
            // 恢复旧版「整段文字 + 单图」展示
            tvQuestion.setVisibility(View.VISIBLE);
            return;
        }
        stemContainer.setVisibility(View.VISIBLE);
        // 题干已由块容器接管，隐藏旧版的整段文本与单张图片
        tvQuestion.setVisibility(View.GONE);
        ivStemImage.setVisibility(View.GONE);

        boolean numbered = false;
        StringBuilder pending = new StringBuilder();
        for (Question.StemBlock b : blocks) {
            if (b == null) continue;
            if (b.kind == Question.StemBlock.KIND_TEXT) {
                if (b.text == null || b.text.isEmpty()) continue;
                // 连续的文字段落合并为一个 TextView（内部换行），保持 docx 的紧凑排版
                if (pending.length() > 0) pending.append('\n');
                pending.append(b.text);
                continue;
            }
            // 遇到图片：先输出前面累积的文字，再按原顺序插入图片
            numbered = flushStemText(pending, numbered);
            addStemImageBlock(b);
        }
        numbered = flushStemText(pending, numbered);
        // 全部块都无内容（如图片加载失败）时回退到纯文本展示，避免题干丢失
        if (stemContainer.getChildCount() == 0) {
            stemContainer.setVisibility(View.GONE);
            tvQuestion.setVisibility(View.VISIBLE);
            tvQuestion.setText((index + 1) + ". " + q.stem);
        }
    }

    /** 把累积的题干文字输出为一个 TextView（首个文字块带题号），返回是否已输出题号。 */
    private boolean flushStemText(StringBuilder pending, boolean numbered) {
        if (pending.length() == 0) return numbered;
        TextView tv = new TextView(this);
        tv.setTextSize(DataStore.getQuestionFontSp(this));
        tv.setTextColor(getColor(R.color.text_main));
        tv.setLineSpacing(dp(4), 1f);
        tv.setText(numbered ? pending.toString() : (index + 1) + ". " + pending);
        if (stemContainer.getChildCount() > 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            tv.setLayoutParams(lp);
        }
        stemTextViews.add(tv);
        stemContainer.addView(tv);
        pending.setLength(0);
        return true;
    }

    /** 追加一个题干图片块（点击可放大查看）。 */
    private void addStemImageBlock(Question.StemBlock b) {
        final android.graphics.Bitmap bmp = loadMediaBitmap(b.image);
        if (bmp == null) return;
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setMaxHeight(dp(240));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setContentDescription("题干图片");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        iv.setLayoutParams(lp);
        iv.setOnClickListener(v -> showImageDialog(bmp));
        stemContainer.addView(iv);
    }
    private void showImageDialog(android.graphics.Bitmap bmp) {
        if (bmp == null) return;
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF000000);
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        android.view.Window w = d.getWindow();
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

    @Override
    protected void onDestroy() {
        if (moreTimeRunnable != null) handler.removeCallbacks(moreTimeRunnable);
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
    }
}
