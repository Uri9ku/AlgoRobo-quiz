package com.algorobo.quiz;

import java.util.ArrayList;
import java.util.List;

public class QuestionBank {
    public static List<Question> getAll() {
        List<Question> list = new ArrayList<>();

        // ===== Scratch / 图形化编程 =====
        list.add(new Question(1, "Scratch基础", "Scratch 中让角色向左移动的积木是（）",
            new String[]{"移动10步", "移到x坐标减小", "旋转15度", "碰到边缘就反弹"}, 1,
            "角色在舞台上左右移动由 x 坐标控制，向左移动就是减小 x 坐标。"));
        list.add(new Question(2, "Scratch基础", "能让程序重复执行某段代码的积木是（）",
            new String[]{"重复执行", "如果那么", "广播", "停止全部"}, 0,
            "“重复执行”积木会让内部代码循环运行，是循环结构的基础。"));
        list.add(new Question(3, "Scratch基础", "积木“当绿旗被点击”属于（）",
            new String[]{"外观类", "事件类", "控制类", "侦测类"}, 1,
            "“当绿旗被点击”是程序启动的事件出发点，属于事件类积木。"));
        list.add(new Question(4, "Scratch基础", "Scratch 中舞台的默认坐标系，中心点坐标是（）",
            new String[]{"(0,0)", "(100,100)", "(240,180)", "(480,360)"}, 0,
            "舞台采用平面直角坐标系，中心点为 (0,0)。"));
        list.add(new Question(5, "Scratch基础", "想要判断角色是否碰到了另一个角色，应使用（）积木",
            new String[]{"碰到鼠标指针", "碰到颜色", "碰到某个角色", "距离到"}, 2,
            "侦测类“碰到某个角色”积木可判断两个角色是否接触。"));

        // ===== Python 语法 =====
        list.add(new Question(6, "Python语法", "Python 中用哪个关键字定义函数（）",
            new String[]{"func", "def", "function", "define"}, 1,
            "Python 使用 def 关键字定义函数，如 def foo()。"));
        list.add(new Question(7, "Python语法", "print(2 + 3 * 2) 的输出结果是（）",
            new String[]{"10", "8", "12", "7"}, 1,
            "先算乘法 3*2=6，再加 2 得 8，符合运算符优先级。"));
        list.add(new Question(8, "Python语法", "列表 [1, 2, 3] 的长度是（）",
            new String[]{"2", "3", "4", "1"}, 1,
            "列表有 3 个元素，len([1,2,3]) 返回 3。"));
        list.add(new Question(9, "Python语法", "以下哪个是正确的变量名（）",
            new String[]{"1abc", "my-var", "my_var", "class"}, 2,
            "变量名不能以数字开头、不能用连字符，也不能使用关键字 class。"));
        list.add(new Question(10, "Python语法", "range(5) 会生成哪些数字（）",
            new String[]{"1到5", "0到5", "0到4", "1到4"}, 2,
            "range(5) 从 0 开始，生成 0、1、2、3、4，共 5 个数。"));

        // ===== 算法思维 =====
        list.add(new Question(11, "算法思维", "程序设计的三种基本结构不包括（）",
            new String[]{"顺序结构", "分支结构", "循环结构", "递归结构"}, 3,
            "三种基本结构是顺序、分支（选择）、循环。递归属于具体手段，不是基本结构。"));
        list.add(new Question(12, "算法思维", "在有序列表中查找目标值，效率最高的常用算法是（）",
            new String[]{"顺序查找", "二分查找", "冒泡排序", "随机查找"}, 1,
            "二分查找每次折半，时间复杂度低，适用于有序列表。"));
        list.add(new Question(13, "算法思维", "“判断一个数是奇数还是偶数”最适合用（）结构",
            new String[]{"顺序", "分支/选择", "循环", "函数"}, 1,
            "根据条件是否成立执行不同分支，属于选择（分支）结构。"));
        list.add(new Question(14, "算法思维", "冒泡排序的时间复杂度是（）",
            new String[]{"O(n)", "O(log n)", "O(n²)", "O(1)"}, 2,
            "冒泡排序需要双循环比较，时间复杂度为 O(n²)。"));
        list.add(new Question(15, "算法思维", "递归算法必须包含（）才能正常结束",
            new String[]{"循环体", "终止条件", "全局变量", "多线程"}, 1,
            "递归必须有明确的终止条件，否则会无限递归导致栈溢出。"));

        // ===== 机器人结构 =====
        list.add(new Question(16, "机器人结构", "机器人常用的控制器相当于机器人的（）",
            new String[]{"手臂", "大脑", "眼睛", "腿"}, 1,
            "控制器（主控板）处理信息、发出指令，相当于机器人的大脑。"));
        list.add(new Question(17, "机器人结构", "能让机器人感知障碍物的传感器是（）",
            new String[]{"超声波传感器", "电机", "电池", "指示灯"}, 0,
            "超声波传感器通过发射接收声波测距，常用于避障。"));
        list.add(new Question(18, "机器人结构", "驱动机器人车轮转动的部件是（）",
            new String[]{"传感器", "电机", "主板", "齿轮箱"}, 1,
            "电机将电能转化为机械能，驱动轮子转动。"));
        list.add(new Question(19, "机器人结构", "齿轮传动中，主动轮齿数多于从动轮，则输出速度（）",
            new String[]{"变快", "变慢", "不变", "无法判断"}, 0,
            "主动轮齿多、从动轮齿少时，传动比大于1，从动轮转动更快。"));
        list.add(new Question(20, "机器人结构", "机器人巡线常用哪种传感器（）",
            new String[]{"红外/灰度传感器", "温度传感器", "湿度传感器", "声音传感器"}, 0,
            "巡线通常使用红外或灰度传感器识别地面黑线的颜色深浅。"));

        for (Question q : list) q.uid = Question.makeUid("builtin", q.id);
        return list;
    }
}
