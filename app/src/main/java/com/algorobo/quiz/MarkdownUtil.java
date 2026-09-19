package com.algorobo.quiz;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量级 Markdown 转 Spanned 渲染器（零依赖）。
 * 支持：标题 #、粗体 **text**、斜体 *text*、行内代码 `code`、
 * 无序列表 -/*、有序列表 1.、引用 >、代码块 ```、水平线 ---、换行。
 *
 * 实现要点：行内样式采用「先收集匹配区间、再倒序删除标记」的策略，
 * 避免边删除边匹配导致的索引错乱与越界崩溃。
 */
public class MarkdownUtil {

    private final int codeColor;

    public MarkdownUtil(int textColor, int codeColor) {
        // textColor 保留语义；正文本体颜色由 TextView 自身 textColor 控制
        this.codeColor = codeColor;
    }

    /** 将 Markdown 文本渲染为 Spanned。 */
    public Spanned render(String markdown) {
        if (markdown == null) return new SpannableStringBuilder("");
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = markdown.split("\n", -1);
        List<String> block = new ArrayList<>();
        boolean inCodeBlock = false;
        StringBuilder codeBuf = new StringBuilder();

        for (String rawLine : lines) {
            if (inCodeBlock) {
                if (rawLine.trim().startsWith("```")) {
                    inCodeBlock = false;
                    appendCodeBlock(out, codeBuf.toString());
                    codeBuf.setLength(0);
                } else {
                    codeBuf.append(rawLine).append('\n');
                }
                continue;
            }
            String trimmed = rawLine.trim();
            if (trimmed.startsWith("```")) {
                inCodeBlock = true;
                codeBuf.setLength(0);
                continue;
            }
            // 水平线
            if (trimmed.matches("^[-*_]{3,}$")) {
                flushBlock(out, block);
                out.append("────────────\n");
                continue;
            }
            // 标题
            Matcher h = Pattern.compile("^(#{1,6})\\s+(.*)$").matcher(trimmed);
            if (h.matches()) {
                flushBlock(out, block);
                int level = h.group(1).length();
                float scale = level == 1 ? 1.35f : level == 2 ? 1.25f
                        : level == 3 ? 1.15f : 1.05f;
                int start = out.length();
                appendInline(out, h.group(2));
                out.setSpan(new RelativeSizeSpan(scale), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
                continue;
            }
            // 空行作为段落分隔
            if (trimmed.isEmpty()) {
                flushBlock(out, block);
                continue;
            }
            block.add(rawLine);
        }
        flushBlock(out, block);
        if (inCodeBlock && codeBuf.length() > 0) {
            appendCodeBlock(out, codeBuf.toString());
        }
        return out;
    }

    /** 将累积的普通文本块（含列表/引用）渲染并追加。 */
    private void flushBlock(SpannableStringBuilder out, List<String> block) {
        if (block.isEmpty()) return;
        for (String line : block) {
            String trimmed = line.trim();
            Matcher ul = Pattern.compile("^[-*]\\s+(.*)$").matcher(trimmed);
            Matcher ol = Pattern.compile("^(\\d+)[.)]\\s+(.*)$").matcher(trimmed);
            Matcher quote = Pattern.compile("^>\\s?(.*)$").matcher(trimmed);

            if (ul.matches()) {
                appendLine(out, "• ", ul.group(1));
            } else if (ol.matches()) {
                appendLine(out, ol.group(1) + ". ", ol.group(2));
            } else if (quote.matches()) {
                appendLine(out, "┃ ", quote.group(1));
            } else {
                appendLine(out, "", line);
            }
        }
        out.append('\n');
        block.clear();
    }

    /** 追加一行：写入前缀 + 内容（内容经过行内样式处理）。 */
    private void appendLine(SpannableStringBuilder out, String prefix, String content) {
        out.append(prefix);
        int contentStart = out.length();
        appendInline(out, content);
        out.append('\n');
        applyInlineSpans(out, contentStart);
    }

    private void appendCodeBlock(SpannableStringBuilder out, String code) {
        if (code == null || code.isEmpty()) return;
        int start = out.length();
        String c = code.replaceAll("\n+$", "");
        out.append(c);
        out.setSpan(new ForegroundColorSpan(codeColor), start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new TypefaceSpan("monospace"), start, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append('\n');
    }

    private void appendInline(SpannableStringBuilder out, String text) {
        out.append(text);
    }

    /** 在 [start, end) 范围内处理粗体、斜体、行内代码。 */
    private void applyInlineSpans(SpannableStringBuilder sb, int start) {
        int end = sb.length();
        applyInline(sb, start, end, "\\*\\*(.+?)\\*\\*", 2, true, false, false);
        end = sb.length();
        applyInline(sb, start, end, "(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)", 1, false, true, false);
        end = sb.length();
        applyInline(sb, start, end, "`([^`\\n]+)`", 1, false, false, true);
    }

    /**
     * 在 sb 的 [start, end) 区间内查找符合 pattern 的片段，应用样式并删除两侧标记。
     *
     * @param markerLen 片段两侧标记的字符数（** 为 2，* 和 ` 为 1）
     * @param bold      是否加粗
     * @param italic    是否斜体
     * @param code      是否作为行内代码（等宽 + 前景色）
     */
    private void applyInline(SpannableStringBuilder sb, int start, int end, String regex,
                             int markerLen, boolean bold, boolean italic, boolean code) {
        Matcher m = Pattern.compile(regex).matcher(sb);
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            int ms = m.start();
            int me = m.end();
            if (ms >= start && me <= end) {
                spans.add(new int[]{ms, me});
            }
        }
        // 从后往前处理，避免删除字符导致后续索引偏移
        for (int i = spans.size() - 1; i >= 0; i--) {
            int ms = spans.get(i)[0];
            int me = spans.get(i)[1];
            // 先删除尾部标记，再删除首部标记
            sb.delete(me - markerLen, me);
            sb.delete(ms, ms + markerLen);
            int contentStart = ms;
            int contentEnd = me - 2 * markerLen;
            // 防御性钳制，避免因负索引或边界导致 setSpan 越界
            if (contentStart < 0) contentStart = 0;
            if (contentEnd > sb.length()) contentEnd = sb.length();
            if (contentEnd <= contentStart) continue;
            if (bold) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), contentStart, contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (italic) {
                sb.setSpan(new StyleSpan(Typeface.ITALIC), contentStart, contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (code) {
                sb.setSpan(new ForegroundColorSpan(codeColor), contentStart, contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new TypefaceSpan("monospace"), contentStart, contentEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}
