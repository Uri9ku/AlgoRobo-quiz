package com.algorobo.quiz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线真题导入管理器（进程级）：
 * - 多套真题并发下载+解析（最多 {@link #MAX_PARALLEL} 路）；
 * - 切到后台仍继续（线程不随 Activity 销毁而中断；进程被回收才会中断）；
 * - 导入过程中在通知栏显示进度；全部结束自动移除通知。
 */
public final class ImportManager {

    private static final int MAX_PARALLEL = 3;
    private static final String CHANNEL_ID = "import_exam";
    private static final int NOTIFY_ID = 1001;

    private ImportManager() {
    }

    /** 单套真题的导入状态（按 paper key 存放，页面重建后仍可读取）。 */
    public static class State {
        public volatile int percent = -1;      // -1 未开始；0~100 下载进度
        public volatile boolean parsing;
        public volatile boolean done;
        public volatile boolean active;
        public volatile int questionCount;
        public volatile String failMsg;
        /** 该套真题是否已在本地题库（用于列表右下角「已导入真题库」）。 */
        public volatile boolean inBank;
    }

    private static final Map<String, State> STATES = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(MAX_PARALLEL);
    private static final AtomicInteger RUNNING = new AtomicInteger(0);
    private static volatile Runnable listener;
    private static Context appContext;

    public static State state(String key) {
        String k = key == null ? "" : key;
        State s = STATES.get(k);
        if (s == null) {
            s = new State();
            STATES.put(k, s);
        }
        return s;
    }

    public static boolean isRunning() {
        return RUNNING.get() > 0;
    }

    /** 页面注册 UI 刷新回调（在 onDestroy 里传 null 取消）。 */
    public static void setListener(Runnable r) {
        listener = r;
    }

    private static void fireUpdate() {
        Runnable r = listener;
        if (r != null) r.run();
    }

    /** 标记某套真题已在题库（进入页面时按缓存判断）。 */
    public static void markInBank(List<RobotExamBank.Paper> papers) {
        if (papers == null) return;
        for (RobotExamBank.Paper p : papers) {
            if (p == null || p.key == null) continue;
            if (p.questions != null && !p.questions.isEmpty()) state(p.key).inBank = true;
        }
    }

    /**
     * 并发导入多套真题。assets 中的每套都会立即置为「等待中」，随后各自下载+解析。
     */
    public static void start(Context c, List<RobotExamUpdater.ReleaseAsset> assets, boolean saveDocx) {
        if (assets == null || assets.isEmpty()) return;
        appContext = c.getApplicationContext();
        final int total = assets.size();
        RUNNING.addAndGet(total);
        ensureChannel(appContext);
        notifyProgress("正在导入真题", "共 " + total + " 套 · 0/" + total, 0, total);

        final AtomicInteger finished = new AtomicInteger(0);
        for (final RobotExamUpdater.ReleaseAsset ra : assets) {
            final State st = state(ra.key);
            st.percent = 0;
            st.parsing = false;
            st.done = false;
            st.failMsg = null;
            st.active = true;
            POOL.execute(() -> {
                String err = null;
                int n = -1;
                try {
                    String cnName = RobotExamUpdater.buildChineseDocxName(ra.fileName);
                    RobotExamBank.Paper p = RobotExamBank.importFromGitHub(appContext, ra, cnName, saveDocx,
                            new RobotExamBank.ImportProgress() {
                                @Override
                                public void onDownloadPercent(int percent) {
                                    st.percent = percent;
                                    fireUpdate();
                                }

                                @Override
                                public void onParsing() {
                                    st.parsing = true;
                                    fireUpdate();
                                }
                            });
                    n = (p == null || p.questions == null) ? 0 : p.questions.size();
                } catch (Exception e) {
                    err = e.getMessage() == null ? "未知错误" : e.getMessage();
                }
                st.active = false;
                st.parsing = false;
                if (err != null) {
                    st.percent = -1;
                    st.failMsg = err.contains("解析") ? "解析失败" : "下载失败";
                } else {
                    st.done = true;
                    st.questionCount = n;
                    st.inBank = true;
                }
                int f = finished.incrementAndGet();
                RUNNING.decrementAndGet();
                String name = ra.title == null ? ra.fileName : ra.title;
                notifyProgress("正在导入真题", name + "（" + f + "/" + total + "）", f, total);
                fireUpdate();
                if (f >= total) {
                    cancelNotification();
                    fireUpdate();
                }
            });
        }
    }

    // ===================== 通知栏进度 =====================

    private static void ensureChannel(Context c) {
        if (c == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "真题导入",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("在线导入真题的下载与解析进度");
        nm.createNotificationChannel(ch);
    }

    private static void notifyProgress(String title, String text, int progress, int max) {
        Context c = appContext;
        if (c == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            androidx.core.app.NotificationCompat.Builder b =
                    new androidx.core.app.NotificationCompat.Builder(c, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_grid_exam)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setOnlyAlertOnce(true)
                            .setOngoing(true)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                            .setProgress(Math.max(1, max), progress, false);
            nm.notify(NOTIFY_ID, b.build());
        } catch (Exception ignore) {
        }
    }

    private static void cancelNotification() {
        Context c = appContext;
        if (c == null) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFY_ID);
        } catch (Exception ignore) {
        }
    }
}
