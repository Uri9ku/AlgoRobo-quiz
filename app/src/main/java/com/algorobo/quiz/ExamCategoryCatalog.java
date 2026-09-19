package com.algorobo.quiz;

import java.util.ArrayList;
import java.util.List;

/**
 * 竞赛与考级题库目录 - 中央数据源。
 * 考试类型/类目/节点全部通过静态 Java 数据结构声明。
 * 机器人等级考试另见 RobotLevel（多级层级 + 展开交互）。
 */
public class ExamCategoryCatalog {

    /** 节点：具体知识点/章节（普通类目用） */
    public static class Node {
        public String title;
        public List<String> children = new ArrayList<>();
        public Node(String title) { this.title = title; }
        public Node add(String child) { children.add(child); return this; }
    }

    /** 类目：如 C语言 / Python / 图形化 / 机器人 */
    public static class Category {
        public String name;
        public String icon;
        public String hint;
        public List<Node> nodes = new ArrayList<>();
        /** 机器人等级考试专用：级别标准列表（一级~八级） */
        public List<RobotLevel.Level> levels = null;
        public Category(String name, String icon, String hint) {
            this.name = name; this.icon = icon; this.hint = hint;
        }
        public Category add(Node n) { nodes.add(n); return this; }
    }

    /** 考试类型：如 中国电子学会等级考试 / 蓝桥杯 */
    public static class ExamType {
        public String name;
        public String subtitle;
        public List<Category> categories = new ArrayList<>();
        public ExamType(String name, String subtitle) {
            this.name = name; this.subtitle = subtitle;
        }
        public ExamType add(Category c) { categories.add(c); return this; }
    }

    public static final List<ExamType> TYPES = new ArrayList<>();

    static {
        // ===== ① 中国电子学会等级考试 =====
        ExamType cie = new ExamType("中国电子学会等级考试", "全国青少年软件编程等级考试");

        Category cLang = new Category("C语言", "🅲", "C语言编程等级");
        cLang.add(new Node("基础语法").add("变量与数据类型").add("运算符与表达式").add("输入输出"));
        cLang.add(new Node("控制结构").add("顺序结构").add("选择结构").add("循环结构"));

        Category python = new Category("Python", "🐍", "Python 编程等级");
        python.add(new Node("基础语法").add("变量与数据类型").add("运算符与表达式").add("输入输出"));
        python.add(new Node("函数与模块").add("函数定义").add("模块导入").add("常用内置函数"));

        Category scratch = new Category("图形化", "🧩", "图形化编程等级");
        scratch.add(new Node("基础操作").add("舞台与角色").add("运动与外观").add("声音与事件"));
        scratch.add(new Node("逻辑与算法").add("变量与列表").add("循环与判断").add("综合项目"));

        Category robot = new Category("机器人", "🤖", "机器人等级考试");
        robot.levels = RobotLevel.buildLevels();

        cie.add(cLang).add(python).add(scratch).add(robot);
        TYPES.add(cie);

        // ===== ② 蓝桥杯（类目待完善） =====
        ExamType lanqiao = new ExamType("蓝桥杯", "蓝桥杯青少年编程大赛");
        lanqiao.add(new Category("类目待完善", "📋", "待补充具体科目"));
        TYPES.add(lanqiao);
    }

    public static ExamType getByIndex(int index) {
        if (index < 0 || index >= TYPES.size()) return TYPES.get(0);
        return TYPES.get(index);
    }
}
