package com.algorobo.quiz;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 真题 docx 解析器：将标准 OOXML(.docx) 解析为 {@link RobotExamBank.Paper}。
 *
 * 解析规则（基于 2026.6.CIE.RLE 系列样本实测）：
 *  - 大题标题行："一、单选题(共N题，共N分)" 等，用中文数字编号。
 *  - 题干：以 "N." 开头（N 为题号），其后为题干文本，可能内嵌图片。
 *  - 选项：以 "A." "B." "C." "D." 开头（文本或图片）；判断题选项固定为 "正确"/"错误"。
 *  - 元数据行（固定前缀）：试题编号：/ 试题类型：/ 标准答案：/ 试题难度：/ 试题解析：。
 *  - 答案格式：单选单个字母(C)；多选 "A|B|C"（| 分隔）；判断 "正确"/"错误"。
 *
 * 图片：docx 中图片以 r:embed="rIdN" 引用，rels 文件建立 rId -> media/imageN.jpeg 映射。
 * 解析时同步将图片解压到 mediaDir 目录，Question 中以文件名记录。
 */
public class DocxParser {
    private static final String TAG = "DocxParser";

    /**
     * 解析 docx 字节流为 Paper。mediaDir 用于存放提取出的图片（可传 null 表示不提取图片）。
     */
    public static RobotExamBank.Paper parse(byte[] docx, String key, File mediaDir) throws Exception {
        if (docx == null || docx.length == 0) throw new Exception("docx 数据为空");

        String documentXml = null;
        String relsXml = null;
        Map<String, byte[]> mediaImages = new HashMap<>();
        Map<String, String> relMap = new HashMap<>();

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(docx));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName().replace('\\', '/');
            if (entry.isDirectory()) { zis.closeEntry(); continue; }
            byte[] data = readAll(zis);
            if ("word/document.xml".equals(name)) {
                documentXml = new String(data, "UTF-8");
            } else if ("word/_rels/document.xml.rels".equals(name)) {
                relsXml = new String(data, "UTF-8");
            } else if (name.startsWith("word/media/") && isImageFile(name)) {
                mediaImages.put(name.substring("word/".length()), data);
            }
            zis.closeEntry();
        }
        zis.close();

        if (documentXml == null) throw new Exception("docx 缺少 word/document.xml");

        if (relsXml != null) {
            Matcher m = Pattern.compile("Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"").matcher(relsXml);
            while (m.find()) {
                String rid = m.group(1);
                String target = m.group(2);
                if (target.contains("media/")) relMap.put(rid, target);
            }
        }

        List<Para> paras = extractParagraphs(documentXml);

        RobotExamBank.Paper paper = new RobotExamBank.Paper();
        paper.key = key;
        paper.questions = new ArrayList<>();

        Question cur = null;
        List<String> curOptions = new ArrayList<>();
        List<String> curOptionImgs = new ArrayList<>();
        boolean inOptions = false;
        String curStemImg = null;
        StringBuilder curStem = new StringBuilder();
        // 题干混排块：按 docx 中文字/图片出现顺序记录，保证「文字 + 多图」的排版与原文一致。
        List<Question.StemBlock> curStemBlocks = new ArrayList<>();
        // 大题标题行解析出的「当前题型」与「每题分值」。真实 docx 没有逐题元数据行，
        // 题型与分值信息全部写在大题标题行（如「一、单选题（共30题，共60分）」）中。
        // sectionScore 为均分后的每题分值，在遇到新大题标题行时更新。
        String sectionType = null;
        String sectionTypeLabel = null;
        int sectionScore = 0;
        // 大题内的题数（来自标题「共N题」）与已建题数：用于防止大题正文里的分点被拆成新题。
        int sectionQuota = 0;
        int sectionStarted = 0;
        // 题号连续性判断：大题首题接受任意题号，其余必须等于期望题号（上一题号+1）。
        boolean sectionFresh = true;
        int expectedQNum = 0;
        // 是否已进入「参考答案」区块：该区块位于 docx 末尾，逐题列出标准答案，
        // 必须以题号回填到已解析的题目，而不是当作新题触发。
        boolean inAnswerSection = false;
        for (Para p : paras) {
            String text = p.text.replace('\u00a0', ' ').trim();
            String img = p.firstImageRid();
            // 「参考答案」标志：进入答案收集模式，后续仍是题号+答案，但不再新建题目。
            if (text.equals("参考答案") || text.startsWith("参考答案")) {
                inAnswerSection = true;
                continue;
            }
            // 大题标题行：识别「一、单选题（共N题，共M分）」等，解析题型与均分分值。
            // 「一、…」「二、…」在答案区块中也重复出现，仅作为分隔标题，不改变 sectionType/sectionScore 也不新建题目。
            SectionInfo section = parseSectionTitle(text);
            if (section != null) {
                if (!inAnswerSection) {
                    sectionType = section.type;
                    sectionTypeLabel = section.label;
                    sectionScore = section.score;
                    // 新大题开始：重置题号连续性判断与配额
                    sectionQuota = section.count;
                    sectionStarted = 0;
                    sectionFresh = true;
                    expectedQNum = 0;
                }
                continue;
            }
            // 参考答案区块结束：若后续出现非答案内容（理论上题目已全部给出），回退正常解析。
            // 主要目标是：题干解析阶段不处理答案；若题干末尾没有独立答案区块，则保持原逻辑。
            // 题号行触发新题。真实 docx 中「试题编号：」等元数据位于题目内容（题干+选项）之后，
            // 因此必须以题号行作为新题的唯一触发标志，而非「试题编号：」。
            // 题号有两种格式：
            //  A) 题号单独成段（如 "1."），题干在后续段落 —— 2026.6.CIE.RLE 系列。
            //  B) 题号与题干合并成段（如 "1.下列选项中……"）—— 2026.3.CIE.RLE 实操卷系列。
            String stripped = stripQuestionNumberPrefix(text);
            // 实操卷的评分项编号（如 "1.器件及器件连接（20分）"、"3.功能实现（60分）"）
            // 同样以「N.」开头，但不属于题目，必须排除，否则会多算题数。
            if (stripped != null && isScoringItemText(stripped)) stripped = null;
            // 大题正文里的分点（如编程题的「1.准备工作」「2.功能实现」）同样以「N.」开头，
            // 但不是新题：只有题号接得上（大题首题除外）且不是分点标题时才拆新题。
            int qnum = questionNumber(text);
            if (stripped != null && !inAnswerSection
                    && !isNewQuestionStart(qnum, stripped, expectedQNum,
                            sectionFresh, sectionQuota, sectionStarted)) {
                stripped = null;
            }
            if (stripped != null) {
                if (inAnswerSection) {
                    // 答案区块内的题号行：剥离题号后即为答案内容，回填到对应题目。
                    fillAnswer(paper, text, stripped);
                    continue;
                }
                if (cur != null) flush(paper, cur, curStem, curStemImg, curStemBlocks, curOptions, curOptionImgs);
                cur = new Question();
                cur.hasAnswer = true;
                // 题号连续性状态推进
                expectedQNum = qnum > 0 ? qnum + 1 : 0;
                sectionFresh = false;
                sectionStarted++;
                // 赋值当前大题题型（单选题/多选题/判断题/实操题），并保留 docx 原始题型名称用于展示
                if (sectionType != null) cur.type = sectionType;
                cur.typeLabel = sectionTypeLabel;
                if (sectionScore > 0) cur.score = sectionScore;
                curStem = new StringBuilder();
                curStemBlocks = new ArrayList<>();
                curOptions = new ArrayList<>();
                curOptionImgs = new ArrayList<>();
                curStemImg = null;
                inOptions = false;
                // 题号与题干合并成段时，剥离题号后剩余文本即题干首行。同时记录该段图片。
                // 若题干末尾附带分值后缀「（N分）」，则剥离并填充分值。
                if (stripped.length() > 0) {
                    ScoreStripped ss = stripTrailingScore(stripped);
                    appendLine(curStem, ss.text);
                    addStemText(curStemBlocks, ss.text);
                    if (ss.score > 0) cur.score = ss.score;
                }
                if (img != null) curStemImg = img;
                addStemImages(curStemBlocks, p.imageRids);
                continue;
            }
            if (cur == null) continue;
            if (inAnswerSection) continue;
            if (text.startsWith("试题编号：")) {
                // 试题编号只是元数据，仅设置 id，不触发新题。
                String idStr = text.substring("试题编号：".length()).trim();
                cur.id = parseId(idStr);
                continue;
            }
            if (text.startsWith("试题类型：")) {
                String raw = text.substring("试题类型：".length()).trim();
                cur.typeLabel = raw;
                String normalized = normalizeType(raw);
                if (normalized != null) cur.type = normalized;
                continue;
            }
            if (text.startsWith("标准答案：")) {
                setAnswer(cur, text.substring("标准答案：".length()).trim());
                continue;
            }
            if (text.startsWith("试题难度：")) {
                cur.difficulty = text.substring("试题难度：".length()).trim();
                continue;
            }
            if (text.startsWith("试题解析：")) {
                cur.analysis = text.substring("试题解析：".length()).trim();
                continue;
            }
            if (text.startsWith("分值：") || text.startsWith("试题分值：")) {
                String prefix = text.startsWith("分值：") ? "分值：" : "试题分值：";
                cur.score = parseScore(text.substring(prefix.length()).trim());
                continue;
            }
            if (text.startsWith("考生答案：") || text.startsWith("考生得分：")
                    || text.startsWith("是否评分：") || text.startsWith("评价描述：")) {
                continue;
            }
            if (isSectionTitle(text)) continue;

            if (isOptionStart(text)) {
                inOptions = true;
                curOptions.add(stripOptionPrefix(text));
                curOptionImgs.add(img);
                continue;
            }
            if (!inOptions) {
                // 题干及其续行（含题干图片）。若末段文本附带分值后缀「（N分）」，则剥离并填充分值。
                // 各段落之间保留换行，使实操题/编程题等分点描述的格式与 docx 保持一致。
                if (text.length() > 0) {
                    // 分值后缀只在「题干首行」生效：编程题正文里的评分细则（如「…；（1分）」）
                    // 也形如 （N分），若一并剥离会把题目分值覆盖成细则分值。
                    ScoreStripped ss = curStem.length() == 0
                            ? stripTrailingScore(text)
                            : new ScoreStripped(text, 0);
                    appendLine(curStem, ss.text);
                    addStemText(curStemBlocks, ss.text);
                    if (ss.score > 0) cur.score = ss.score;
                }
                if (img != null && curStemImg == null) curStemImg = img;
                // 同一题干的文字与多张图片都按出现顺序入块，渲染时逐块还原 docx 排版
                addStemImages(curStemBlocks, p.imageRids);
            } else {
                // 选项文本续行：真实 docx 中选项文字出现在选项字母（A.）的下一行，
                // 需拼接到最后一个选项上（保留换行）；若为图片则附加到对应选项。
                if (text.length() > 0 && curOptions.size() > 0) {
                    int last = curOptions.size() - 1;
                    String prev = curOptions.get(last);
                    curOptions.set(last, prev.isEmpty() ? text : prev + "\n" + text);
                }
                if (img != null && curOptions.size() > 0) {
                    int last = curOptions.size() - 1;
                    if (curOptionImgs.get(last) == null) curOptionImgs.set(last, img);
                }
            }
        }
        flush(paper, cur, curStem, curStemImg, curStemBlocks, curOptions, curOptionImgs);

        if (mediaDir != null && !mediaImages.isEmpty()) {
            mediaDir.mkdirs();
            for (Map.Entry<String, byte[]> e : mediaImages.entrySet()) {
                String relTarget = e.getKey();
                String fileName = relTarget.substring("media/".length());
                File out = new File(mediaDir, fileName);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(e.getValue());
                fos.close();
            }
            resolveImageRefs(paper, relMap);
        }

        return paper;
    }

    private static void flush(RobotExamBank.Paper paper, Question cur,
                              StringBuilder stem, String stemImg,
                              List<Question.StemBlock> stemBlocks,
                              List<String> options, List<String> optionImgs) {
        if (cur == null) return;
        cur.stem = stem.toString().trim();
        if (stemImg != null) cur.stemImg = stemImg;
        if (stemBlocks != null && !stemBlocks.isEmpty()) {
            cur.stemBlocks = stemBlocks;
        }
        if (!options.isEmpty()) {
            cur.options = options.toArray(new String[0]);
            boolean hasImg = false;
            for (String s : optionImgs) if (s != null && !s.isEmpty()) { hasImg = true; break; }
            if (hasImg) {
                String[] arr = new String[options.size()];
                for (int i = 0; i < options.size(); i++) arr[i] = optionImgs.get(i);
                cur.optionImgs = arr;
            }
        }
        // 题型兜底：未能从大题标题识别题型时，有选项按选择题、无选项按实操题处理，
        // 保证仍能渲染作答控件（未知题型名仍由 typeLabel 展示）。
        if (cur.type == null) {
            cur.type = options.isEmpty() ? Question.TYPE_PRACTICAL : Question.TYPE_SINGLE;
        }
        paper.questions.add(cur);
        // 唯一键按「卷 key + 卷内题号(1 起)」生成：docx 解析出的 id 恒为 0，
        // 若用 id 会导致同一套卷子所有题共用同一个 key（错题本/收藏/进度串题）。
        cur.uid = Question.makeUid(paper.key, paper.questions.size());
    }

    private static void setAnswer(Question q, String ans) {
        if (ans == null) return;
        ans = ans.trim();
        if (Question.TYPE_JUDGE.equals(q.type)) {
            q.judgeAnswer = ans;
            return;
        }
        if (Question.TYPE_MULTI.equals(q.type)) {
            String[] parts = ans.split("\\|");
            List<String> list = new ArrayList<>();
            List<Integer> idx = new ArrayList<>();
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                if (part.length() >= 1) {
                    char c = part.charAt(0);
                    if (c >= 'A' && c <= 'Z') {
                        list.add(part);
                        idx.add(c - 'A');
                    }
                }
            }
            if (!list.isEmpty()) {
                q.answerIndexes = list.toArray(new String[0]);
                if (!idx.isEmpty()) q.answerIndex = idx.get(0);
            }
            return;
        }
        if (ans.length() >= 1) {
            char c = ans.charAt(0);
            if (c >= 'A' && c <= 'Z') q.answerIndex = c - 'A';
        }
    }

    private static int parseId(String idStr) {
        try {
            long v = Long.parseLong(idStr);
            return (int) (v % Integer.MAX_VALUE);
        } catch (Exception e) {
            return idStr.hashCode() & 0x7fffffff;
        }
    }

    /**
     * 从分值字符串解析整数分值，支持 "5"、"5分"、"5.0" 等形式，解析失败返回 0。
     */
    private static int parseScore(String s) {
        if (s == null) return 0;
        s = s.replace("分", "").replace("（", "").replace("）", "").trim();
        if (s.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(s);
            return (int) Math.round(d);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 剥离文本末尾的分值后缀「（N分）」。返回剥离分值后的文本及分值。
     */
    private static ScoreStripped stripTrailingScore(String text) {
        if (text == null) return new ScoreStripped("", 0);
        java.util.regex.Pattern ptn = java.util.regex.Pattern.compile("^(.*?)[（(](\\d+(?:\\.\\d+)?)分[)）]\\s*$");
        java.util.regex.Matcher m = ptn.matcher(text);
        if (m.matches()) {
            int score = parseScore(m.group(2));
            return new ScoreStripped(m.group(1).trim(), score);
        }
        return new ScoreStripped(text, 0);
    }

    private static class ScoreStripped {
        final String text;
        final int score;
        ScoreStripped(String text, int score) { this.text = text; this.score = score; }
    }

    private static boolean isSectionTitle(String text) {
        // 兼容全角顿号「、」以及「第一部分」「单选题」等大题标题行。
        return text.matches("^[一二三四五六七八九十]+[、.]?.*(共\\d+题|共\\d+分).*")
                || text.matches("^第[一二三四五六七八九十]+部分.*");
    }

    /**
     * 解析大题标题行为结构化的题型与每题分值。
     * 目标格式：「一、单选题（共30题，共60分）」→ type=单选题, score=2；
     * 「一、模型认知（共4题，共20分）」→ type=多选题, score=5；
     * 「二、实际操作（共1题，共80分）」→ type=实操题, score=80。
     * 无法识别为非大题标题行时返回 null。
     */
    private static SectionInfo parseSectionTitle(String text) {
        if (text == null || text.isEmpty()) return null;
        // 仅匹配「(共N题,共M分)」或「(共N题,共M分)」的大题标题行
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^[一二三四五六七八九十]+[、.．]?\\s*(.{0,12}?)\\s*[（(]共\\s*(\\d+)\\s*题[，,]共\\s*(\\d+)\\s*分[)）]")
                .matcher(text);
        if (!m.matches()) return null;
        String title = m.group(1).trim();
        int count = 0, total = 0;
        try { count = Integer.parseInt(m.group(2)); } catch (Exception ignore) {}
        try { total = Integer.parseInt(m.group(3)); } catch (Exception ignore) {}
        // 题型归一化为内部逻辑类型；docx 原始名称（如「编程题」）单独作为展示用 label 保留
        String type = normalizeType(title);
        int perScore = 0;
        if (count > 0 && total >= count && total % count == 0) {
            perScore = total / count;
        } else if (count > 0) {
            perScore = total / count;
        }
        return new SectionInfo(type, title, perScore, count);
    }

    /**
     * 将 docx 中的题型名称归一化为内部题型（决定作答交互），未知时返回 null。
     * docx 中的原始名称由调用方单独保存用于展示，例如「编程题」按实操题处理但标签仍显示「编程题」。
     */
    private static String normalizeType(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        if (t.contains("单选")) return Question.TYPE_SINGLE;
        if (t.contains("多选") || t.contains("模型认知")) return Question.TYPE_MULTI;
        if (t.contains("判断")) return Question.TYPE_JUDGE;
        if (t.contains("简答")) return Question.TYPE_SHORT;
        if (t.contains("附件")) return Question.TYPE_ATTACH;
        if (t.contains("实操") || t.contains("操作") || t.contains("搭建") || t.contains("编程")
                || t.contains("设计") || t.contains("作图") || t.contains("编写")
                || t.contains("实训") || t.contains("任务")) return Question.TYPE_PRACTICAL;
        return null;
    }

    /** 追加一行文本：非空行之间以换行分隔，保持与 docx 一致的段落/分点换行格式。 */
    private static void appendLine(StringBuilder sb, String line) {
        if (sb == null || line == null || line.isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(line);
    }

    /** 题干文本块入队（空文本忽略）。 */
    private static void addStemText(List<Question.StemBlock> blocks, String text) {
        if (blocks == null || text == null || text.isEmpty()) return;
        blocks.add(new Question.StemBlock(Question.StemBlock.KIND_TEXT, text));
    }

    /** 题干图片块入队：一个段落中的多张图片全部按顺序加入，实现「多图就多图」。 */
    private static void addStemImages(List<Question.StemBlock> blocks, List<String> rids) {
        if (blocks == null || rids == null || rids.isEmpty()) return;
        for (String rid : rids) {
            if (rid == null || rid.isEmpty()) continue;
            blocks.add(new Question.StemBlock(Question.StemBlock.KIND_IMAGE, rid));
        }
    }

    private static class SectionInfo {
        final String type;
        final String label;
        final int score;
        /** 大题标题声明的题数（共N题），未知为 0。 */
        final int count;
        SectionInfo(String type, String label, int score, int count) {
            this.type = type;
            this.label = label;
            this.score = score;
            this.count = count;
        }
    }

    /**
     * 在「参考答案」区块内，将某题的答案回填到已解析的题目。
     * text 为原始行（如 "1．D"），stripped 为剥离题号后的内容（如 "D"）。
     * 按题号匹配 paper.questions 中的题目（题号 = questions 列表索引 + 1）。
     */
    private static void fillAnswer(RobotExamBank.Paper paper, String text, String stripped) {
        if (paper.questions == null || paper.questions.isEmpty()) return;
        // 从原始行提取题号
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s*[\\.．]").matcher(text);
        if (!m.find()) return;
        int qid = 0;
        try { qid = Integer.parseInt(m.group(1)); } catch (Exception ignore) {}
        if (qid <= 0) return;
        // 题目列表索引即题号-1（题目题号从 1 开始连续递增）
        int idx = qid - 1;
        if (idx < 0 || idx >= paper.questions.size()) return;
        Question q = paper.questions.get(idx);
        String ans = stripped == null ? "" : stripped.trim();
        // 剥离开包裹的括号或「评分项：」等前缀
        ans = ans.replaceAll("^[（(]", "").replaceAll("[)）]$", "").trim();
        // 实操题答案 "50．评分项：" 表示该题为实操评分，无客观答案
        if (ans.startsWith("评分项") || ans.startsWith("搭建") || ans.isEmpty()) {
            q.hasAnswer = false;
            return;
        }
        q.hasAnswer = true;
        setAnswer(q, ans);
    }

    /**
     * 纯题号行：如 "1."、"30."。真实 docx 中题号单独成段，是触发新题的唯一标志。
     * @deprecated 已由 {@link #stripQuestionNumberPrefix(String)} 取代，兼容「题号+题干合并」的实操卷格式。
     */
    private static boolean isQuestionNumberLine(String text) {
        return text.matches("^\\d+\\.$");
    }

    /**
     * 判断一行是否为「题号行」并返回剥离题号前缀后的剩余文本。
     * 兼容两种格式：
     *  - "1." / "1．"         -> 返回 ""（题号单独成段，题干在后续段落）
     *  - "1.下列选项…" / "1．下列选项…" -> 返回 "下列选项…"（题号与题干合并成段）
     * 题号后的分隔符兼容半角点 "1." 与全角句号 "1．"（U+FF0E）。
     * 无法匹配题号格式时返回 null，表示不是题号行。
     */
    private static String stripQuestionNumberPrefix(String text) {
        if (text.matches("^\\d+[\\.．]$")) return "";
        java.util.regex.Pattern ptn = java.util.regex.Pattern.compile("^(\\d+)[\\.．]\\s*(.+)$");
        java.util.regex.Matcher m = ptn.matcher(text);
        if (m.matches()) return m.group(2).trim();
        return null;
    }

    /**
     * 判断剥离题号后的文本是否为「实操卷评分项」标题（如 "器件及器件连接（20分）"、
     * "功能实现（60分）"）。此类行与题目一样以「N.」开头，但以「（N分）」结尾，
     * 是评分细则而非题目，必须排除以免多算题数。
     */
    private static boolean isScoringItemText(String stripped) {
        if (stripped == null || stripped.isEmpty()) return false;
        return stripped.matches(".*（\\d+分）\\s*");
    }

    /** 大题正文分点标题（编程题/实操题的「1.准备工作」「2.功能实现」等），不得作为新题。 */
    private static final String[] SUB_HEADING_LABELS = {
            "准备工作", "功能实现", "参考程序", "评分标准", "评分细则", "任务要求", "实现效果",
            "作品提交", "程序说明", "注意事项", "操作步骤", "功能描述", "效果展示"
    };

    /** 取行首题号（如 "29."、"1.准备工作" → 29/1）；非题号行返回 0。 */
    private static int questionNumber(String text) {
        if (text == null) return 0;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)\\s*[\\.．]").matcher(text);
        if (!m.find()) return 0;
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 判断「N. xxx」行是否应作为新题起点。
     * 大题正文的分点（1.准备工作 / 2.功能实现）与真实题号共用「N.」前缀，需用三重条件区分：
     *  1) 分点标题白名单排除；
     *  2) 本大题题数配额（标题「共N题」）用尽后不再新建；
     *  3) 题号连续性——大题首题接受任意题号，其后必须等于上一题号+1
     *     （分点通常从 1 重新编号，与连续题号不衔接，因此被并入题干）。
     */
    private static boolean isNewQuestionStart(int num, String body, int expectedNum,
                                              boolean sectionFresh, int sectionQuota,
                                              int startedInSection) {
        if (num <= 0) return false;
        if (isSubHeadingLabel(body)) return false;
        if (sectionQuota > 0 && startedInSection >= sectionQuota) return false;
        if (sectionFresh || expectedNum <= 0) return true;
        return num == expectedNum;
    }

    private static boolean isSubHeadingLabel(String body) {
        if (body == null) return false;
        String t = body.trim();
        if (t.isEmpty() || t.length() > 12) return false;
        for (String label : SUB_HEADING_LABELS) {
            if (t.startsWith(label)) return true;
        }
        return false;
    }

    private static boolean isOptionStart(String text) {
        // 兼容半角 "A." 与全角 "A．"（U+FF0E）。
        if (text.matches("^[A-D][\\.．].*")) return true;
        if (text.matches("^[A-D]$")) return true;
        if (text.equals("正确") || text.equals("错误")) return true;
        return false;
    }

    private static String stripOptionPrefix(String text) {
        if (text.matches("^[A-D][\\.．].*")) return text.replaceFirst("^[A-D][\\.．]", "").trim();
        if (text.matches("^[A-D]$")) return "";
        return text;
    }

    private static void resolveImageRefs(RobotExamBank.Paper paper, Map<String, String> relMap) {
        for (Question q : paper.questions) {
            q.stemImg = toFileName(q.stemImg, relMap);
            if (q.optionImgs != null) {
                for (int i = 0; i < q.optionImgs.length; i++) {
                    q.optionImgs[i] = toFileName(q.optionImgs[i], relMap);
                }
            }
            if (q.stemBlocks != null) {
                for (Question.StemBlock b : q.stemBlocks) {
                    if (b != null && b.kind == Question.StemBlock.KIND_IMAGE) {
                        b.image = toFileName(b.image, relMap);
                    }
                }
            }
        }
    }

    private static String toFileName(String rid, Map<String, String> relMap) {
        if (rid == null || rid.isEmpty()) return rid;
        // 兼容多值/脏数据引用（如 "rId4|rId5"）：取第一个能解析到的 rId，避免图片无法显示
        if (rid.indexOf('|') >= 0) {
            for (String part : rid.split("\\|")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                String mapped = relMap.get(p);
                if (mapped != null) {
                    return mapped.startsWith("media/") ? mapped.substring("media/".length()) : mapped;
                }
            }
        }
        String target = relMap.get(rid);
        if (target == null) return rid;
        if (target.startsWith("media/")) return target.substring("media/".length());
        return target;
    }

    private static class Para {
        String text = "";
        List<String> imageRids = new ArrayList<>();
        String firstImageRid() { return imageRids.isEmpty() ? null : imageRids.get(0); }
    }

    private static List<Para> extractParagraphs(String xml) {
        List<Para> result = new ArrayList<>();
        // 段落切分：WPS 导出的 docx 含大量嵌套表格(<w:tbl>/<w:tc>/<w:tr>)，
        // 段落标签 <w:p ...> 与 </w:p> 并非严格顺序相邻，非贪婪正则 <w:p[ >].*?</w:p>
        // 会提前在嵌套空段落处错误闭合。改为按标签位置栈配对，精确定位每个真正
        // <w:p>...</w:p>（或自闭合 <w:p .../>）的边界。
        // <w:p> 开标签需排除 <w:pPr>/<w:pBdr>/<w:pStyle> 等属性标签，用 <w:p(?=[ >]) 约束。
        Matcher openM = Pattern.compile("<w:p(?=[ >])").matcher(xml);
        Matcher closeM = Pattern.compile("</w:p>").matcher(xml);
        List<Integer> opens = new ArrayList<>();
        List<Integer> closes = new ArrayList<>();
        while (openM.find()) opens.add(openM.start());
        while (closeM.find()) closes.add(closeM.start());
        Matcher selfM = Pattern.compile("<w:p[^>]*/>").matcher(xml);
        List<Integer> selfCloses = new ArrayList<>();
        while (selfM.find()) selfCloses.add(selfM.start());

        List<int[]> events = new ArrayList<>();
        for (int p : opens) events.add(new int[]{p, 0});       // 0=open
        for (int p : closes) events.add(new int[]{p, 1});      // 1=close
        for (int p : selfCloses) events.add(new int[]{p, 2});  // 2=selfclose
        events.sort((a, b) -> Integer.compare(a[0], b[0]));

        List<int[]> ranges = new ArrayList<>();
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        for (int[] ev : events) {
            int pos = ev[0], typ = ev[1];
            if (typ == 0) {
                stack.push(pos);
            } else if (typ == 1) {
                if (!stack.isEmpty()) {
                    int s = stack.pop();
                    ranges.add(new int[]{s, pos});
                }
            } else {
                int end = xml.indexOf("/>", pos);
                if (end < 0) end = pos;
                else end += 2;
                ranges.add(new int[]{pos, end});
            }
        }
        ranges.sort((a, b) -> Integer.compare(a[0], b[0]));

        // 关键修复：<w:t> 文本标签用 <w:t(?=[ >]) 约束，排除 <w:tblPrEx>、
        // <w:tblBorders>、<w:tcPr>、<w:tr> 等 w:t 开头的非文本标签，
        // 否则 (.*?)</w:t> 非贪婪会跨结构拉到垃圾 XML 碎片。
        Pattern textP = Pattern.compile("<w:t(?=[ >])[^>]*>(.*?)</w:t>", Pattern.DOTALL);
        Pattern imgP = Pattern.compile("r:embed=\"([^\"]+)\"");
        for (int[] r : ranges) {
            String p = xml.substring(r[0], r[1]);
            Para para = new Para();
            Matcher tm = textP.matcher(p);
            StringBuilder sb = new StringBuilder();
            while (tm.find()) sb.append(tm.group(1));
            para.text = sb.toString();
            Matcher im = imgP.matcher(p);
            while (im.find()) para.imageRids.add(im.group(1));
            result.add(para);
        }
        return result;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    /** 判断是否为常见图片格式（大小写不敏感）。 */
    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpeg") || lower.endsWith(".jpg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }
}
