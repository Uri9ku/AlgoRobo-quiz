package com.algorobo.quiz;

import java.util.ArrayList;
import java.util.List;

/**
 * 机器人等级考试目录数据模型。
 * 支持多级层级：级别标准 -> 分组(实践/知识) -> 条目 -> 子条目。
 * 用于备考目录的折叠展开交互。
 */
public class RobotLevel {

    /** 分组：如（一）实践 /（二）知识 */
    public static class Section {
        public String title;
        public List<Item> items = new ArrayList<>();
        public Section(String title) { this.title = title; }
        public Section add(Item it) { items.add(it); return this; }
    }

    /** 条目：如 1. 基本结构认知，可含子条目 ① ② */
    public static class Item {
        public String text;
        public List<String> subs = new ArrayList<>();
        public Item(String text) { this.text = text; }
        public Item sub(String s) { subs.add(s); return this; }
    }

    /** 级别标准：如 一级标准 */
    public static class Level {
        public String name;
        public List<String> metas = new ArrayList<>();  // 科目/器材/软件 说明
        public List<Section> sections = new ArrayList<>();
        public Level(String name) { this.name = name; }
        public Level meta(String m) { metas.add(m); return this; }
        public Level add(Section s) { sections.add(s); return this; }
    }

    /** 构建一级~八级标准完整目录 */
    public static List<Level> buildLevels() {
        List<Level> levels = new ArrayList<>();

        // ===== 一级标准 =====
        Level l1 = new Level("一级标准");
        l1.meta("科目：机械结构搭建、机器人常用知识");
        l1.meta("器材：结构件");
        Section l1p = new Section("实践");
        l1p.add(new Item("基本结构认知"));
        l1p.add(new Item("重心与重力"));
        l1p.add(new Item("简单机械原理").sub("杠杆").sub("轮轴").sub("滑轮").sub("斜面").sub("楔").sub("螺旋"));
        l1p.add(new Item("齿轮与齿轮比"));
        l1p.add(new Item("链传动与带传动"));
        l1p.add(new Item("机器人常用底盘").sub("轮式").sub("履带"));
        l1.add(l1p);
        Section l1k = new Section("知识");
        l1k.add(new Item("机器人影视作品与形象"));
        l1k.add(new Item("稳定结构与不稳定结构"));
        l1k.add(new Item("齿轮组变速比例计算"));
        l1k.add(new Item("省力杠杆与费力杠杆"));
        l1k.add(new Item("动滑轮与定滑轮"));
        l1k.add(new Item("带传动与链传动优缺点"));
        l1k.add(new Item("齿轮种类"));
        l1.add(l1k);
        levels.add(l1);

        // ===== 二级标准 =====
        Level l2 = new Level("二级标准");
        l2.meta("科目：机械结构搭建、机器人常用知识");
        l2.meta("器材：结构件、电子部分（电池盒、电机、连接线）");
        Section l2p = new Section("实践");
        l2p.add(new Item("电池盒、开关与电机连接"));
        l2p.add(new Item("特殊结构").sub("凸轮").sub("滑杆").sub("棘轮").sub("曲柄").sub("连杆"));
        l2p.add(new Item("电机应用（旋转往复摇摆）"));
        l2.add(l2p);
        Section l2k = new Section("知识");
        l2k.add(new Item("机器人重要历史事件"));
        l2k.add(new Item("机器人重要科学家"));
        l2k.add(new Item("机器人理论与相关人物"));
        l2k.add(new Item("特殊结构生活应用").sub("凸轮").sub("滑杆").sub("棘轮").sub("曲柄").sub("蜗轮蜗杆"));
        l2k.add(new Item("曲柄连杆机构区分"));
        l2k.add(new Item("电机工作原理"));
        l2k.add(new Item("摩擦力（产生条件、分类）"));
        l2k.add(new Item("凸轮机构从动件运动轨迹"));
        l2.add(l2k);
        levels.add(l2);

        // ===== 三级标准 =====
        Level l3 = new Level("三级标准");
        l3.meta("图形化编程+模块搭建+电子电路");
        l3.meta("器材及软件：Arduino控制板、电子元件、Mixly软件");
        Section l3k = new Section("知识");
        l3k.add(new Item("电子电路理论及前沿时事"));
        l3k.add(new Item("电流电压电阻与欧姆定律"));
        l3k.add(new Item("串联与并联"));
        l3k.add(new Item("二极管特性"));
        l3k.add(new Item("控制系统工作流程"));
        l3k.add(new Item("模拟量数字量与I/O口"));
        l3k.add(new Item("图形化编程软件使用"));
        l3k.add(new Item("三种基本结构与变量"));
        l3k.add(new Item("数学比较与逻辑运算"));
        l3k.add(new Item("程序流程图绘制"));
        l3.add(l3k);
        Section l3p = new Section("电子电路、图形化编程");
        l3p.add(new Item("串联、并联、混合连接电路"));
        l3p.add(new Item("LED显示效果电路"));
        l3p.add(new Item("按键数字输入信号电路"));
        l3p.add(new Item("光敏电阻环境光线检测电路"));
        l3p.add(new Item("可调电阻控制LED亮度电路"));
        l3p.add(new Item("蜂鸣器发声调电路"));
        l3p.add(new Item("超声波舵机与红外遥控"));
        l3p.add(new Item("交互装置（传感器、执行器）"));
        l3.add(l3p);
        levels.add(l3);

        // ===== 四级标准 =====
        Level l4 = new Level("四级标准");
        l4.meta("代码编程+机器人搭建");
        l4.meta("器材及软件：Arduino控制板、电子元件、Arduino C/C++、Arduino IDE");
        Section l4k = new Section("知识");
        l4k.add(new Item("细分机器人理论与时事"));
        l4k.add(new Item("二进制十进制十六进制"));
        l4k.add(new Item("上拉电阻、下拉电阻"));
        l4k.add(new Item("模拟量数字量与I/O口"));
        l4k.add(new Item("三种基本结构与自定义函数"));
        l4k.add(new Item("变量及变量作用域"));
        l4k.add(new Item("数学、比较及逻辑运算"));
        l4k.add(new Item("直流电机与舵机控制"));
        l4k.add(new Item("传感器功能函数"));
        l4k.add(new Item("类库（概念、安装、使用）"));
        l4k.add(new Item("三极管功能"));
        l4k.add(new Item("传感器、执行器工作原理"));
        l4k.add(new Item("开环控制、闭环控制"));
        l4k.add(new Item("自律型机器人行动方式"));
        l4.add(l4k);
        Section l4p = new Section("机器人搭建");
        l4p.add(new Item("数字信号传感器").sub("灰度").sub("接近开关").sub("触碰"));
        l4p.add(new Item("模拟信号传感器（光线强度）"));
        l4p.add(new Item("数字脉冲信号传感器").sub("超声波").sub("红外遥控"));
        l4p.add(new Item("I/O口信号读写"));
        l4p.add(new Item("编程控制伺服电机（舵机）"));
        l4p.add(new Item("编程控制机器人平台移动"));
        l4p.add(new Item("三极管控制电路通断"));
        l4p.add(new Item("自律型机器人制作").sub("自动跟随").sub("避障").sub("单线条巡线"));
        l4.add(l4p);
        levels.add(l4);

        // ===== 五级标准 =====
        Level l5 = new Level("五级标准");
        l5.meta("器材及软件：ESP32控制板、电子元件、Arduino C/C++");
        Section l5k = new Section("知识");
        l5k.add(new Item("集成电路与微控制器"));
        l5k.add(new Item("控制板基本功能与特性"));
        l5k.add(new Item("中断程序运行机制"));
        l5k.add(new Item("中断回调函数"));
        l5k.add(new Item("一维数组、二维数组"));
        l5k.add(new Item("UART串行通信"));
        l5k.add(new Item("报文含义与组成"));
        l5k.add(new Item("数据位操作"));
        l5k.add(new Item("串口库读写"));
        l5k.add(new Item("字符串操作"));
        l5.add(l5k);
        Section l5p = new Section("电子电路搭建");
        l5p.add(new Item("数码管、LED点阵"));
        l5p.add(new Item("软件按键消抖"));
        l5p.add(new Item("74HC595移位寄存器"));
        l5p.add(new Item("UART数据通信"));
        l5p.add(new Item("EEPROM读写"));
        l5.add(l5p);
        levels.add(l5);

        // ===== 六级标准 =====
        Level l6 = new Level("六级标准");
        l6.meta("器材及软件：ESP32控制板、电子元件、Arduino C/C++");
        Section l6k = new Section("知识");
        l6k.add(new Item("机器人产品工程与时事"));
        l6k.add(new Item("I²C总线通信"));
        l6k.add(new Item("SPI总线通信"));
        l6k.add(new Item("互联网基础"));
        l6k.add(new Item("HTML基本结构"));
        l6k.add(new Item("步进电机原理与控制"));
        l6k.add(new Item("PID控制"));
        l6k.add(new Item("姿态传感器"));
        l6k.add(new Item("I²C库读写"));
        l6k.add(new Item("WiFi库、Web服务器"));
        l6.add(l6k);
        Section l6p = new Section("机器人搭建");
        l6p.add(new Item("步进电机使用"));
        l6p.add(new Item("WiFi硬件读写"));
        l6p.add(new Item("I²C传感器值获取"));
        l6p.add(new Item("I²C液晶显示屏控制"));
        l6p.add(new Item("中断读取码盘"));
        l6p.add(new Item("比例控制、指定路线移动"));
        l6.add(l6p);
        levels.add(l6);

        // ===== 七级标准 =====
        Level l7 = new Level("七级标准");
        l7.meta("器材及软件：开源硬件控制板、无线通信模块");
        Section l7k = new Section("知识");
        l7k.add(new Item("解释型编程语言"));
        l7k.add(new Item("多种编程语言形式与特点"));
        l7k.add(new Item("处理器差别"));
        l7k.add(new Item("Linux命令行"));
        l7k.add(new Item("常用算法"));
        l7k.add(new Item("人工智能基础"));
        l7.add(l7k);
        Section l7p = new Section("机器人搭建");
        l7p.add(new Item("WiFi控制机器人"));
        l7p.add(new Item("机械臂运动"));
        l7p.add(new Item("自主避障、防跌落"));
        l7.add(l7p);
        levels.add(l7);

        // ===== 八级标准 =====
        Level l8 = new Level("八级标准");
        l8.meta("器材及软件：开源硬件控制板、无线通信模块");
        Section l8k = new Section("知识");
        l8k.add(new Item("嵌入式系统软件"));
        l8k.add(new Item("机器人操作系统"));
        l8k.add(new Item("深度学习基础"));
        l8k.add(new Item("AI语音识别算法"));
        l8.add(l8k);
        Section l8p = new Section("机器人搭建");
        l8p.add(new Item("非特定语音与网络语音"));
        l8p.add(new Item("特定颜色/物体跟随"));
        l8p.add(new Item("面部表情识别、指定任务"));
        l8p.add(new Item("图案/文字识别、指定任务"));
        l8.add(l8p);
        levels.add(l8);

        return levels;
    }
}
