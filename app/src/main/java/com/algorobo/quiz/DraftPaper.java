package com.algorobo.quiz;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 草稿纸弹窗：刷题页与全部解析页共用（图标与逻辑复用）。
 * initial 为要恢复的笔迹；onSave 在保存/关闭时回传当前笔迹（不需要持久化可传 null）。
 */
public final class DraftPaper {

    private DraftPaper() {
    }

    public interface OnSave {
        void onSave(List<DrawPadView.StrokeData> strokes);
    }

    public static void show(final Activity activity,
                            List<DrawPadView.StrokeData> initial,
                            final OnSave onSave) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_draft_paper, null);
        d.setContentView(v);

        final DrawPadView drawPad = v.findViewById(R.id.drawPad);
        TextView btnDraftClear = v.findViewById(R.id.btnDraftClear);
        TextView btnDraftSave = v.findViewById(R.id.btnDraftSave);
        TextView btnDraftClose = v.findViewById(R.id.btnDraftClose);
        View draftRoot = v.findViewById(R.id.draftRoot);
        SeekBar sbDraftAlpha = v.findViewById(R.id.sbDraftAlpha);
        TextView tvDraftAlphaValue = v.findViewById(R.id.tvDraftAlphaValue);

        // 打开时恢复上次设置的草稿纸透明度
        final int[] alphaPercent = {DataStore.getDraftAlpha(activity)};
        applyAlpha(draftRoot, tvDraftAlphaValue, sbDraftAlpha, alphaPercent[0]);
        sbDraftAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaPercent[0] = progress;
                applyAlpha(draftRoot, tvDraftAlphaValue, seekBar, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                DataStore.setDraftAlpha(activity, seekBar.getProgress());
            }
        });

        if (initial != null && !initial.isEmpty()) {
            drawPad.restoreStrokes(new ArrayList<>(initial));
        }
        btnDraftClear.setOnClickListener(x -> drawPad.clear());
        btnDraftSave.setOnClickListener(x -> {
            if (onSave != null) onSave.onSave(new ArrayList<>(drawPad.getStrokeData()));
            Toast.makeText(activity, "草稿已保存", Toast.LENGTH_SHORT).show();
        });
        btnDraftClose.setOnClickListener(x -> {
            if (onSave != null) onSave.onSave(new ArrayList<>(drawPad.getStrokeData()));
            DataStore.setDraftAlpha(activity, alphaPercent[0]);
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

    /** 按百分比设置草稿纸底色（白）的透明度：0% 全透明只见笔迹，100% 全不透明。 */
    private static void applyAlpha(View draftRoot, TextView valueView, SeekBar seekBar, int percent) {
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
}
