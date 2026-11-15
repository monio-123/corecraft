package com.mo.corecraft.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link OutlineText#anchorFirstLevel} 的锚定校验。
 *
 * <p>背景（2026-11-13 任务 #49 实锤）：模型展开某章时把整段画成大树的一支，
 * 模块行自己带上 {@code ├── }（前导 4 格 = 解析成第 2 层），拼树 +1 后这一章
 * 整体沉一级、挂到上一章最后一个节点底下；verifyTree 对连续深度全盲。
 * 锚定 = 片段第一行强制成为第 1 层，整段按它平移。
 */
class OutlineTextTest {

    /** 任务 #49 步骤 1780 的真实首行（只截结构需要的三行） */
    @Test
    void 模块行带树线时_锚定回片段第1层() {
        String drifted = "├── 3. 数据链路层与局域网 ｜ 成帧、差错流量控制与以太网\n"
                + "    ├── 3.1 数据链路层基础\n"
                + "    │   ├── 数据链路层总览 ｜ 帧的传输、检错与流量控制\n";

        List<OutlineText.Node> raw = OutlineText.parse(drifted);
        assertEquals(2, raw.get(0).depth(), "未锚定的现状：树线把模块行顶到第 2 层（bug 本体）");

        List<OutlineText.Node> anchored = OutlineText.anchorFirstLevel(raw);
        assertEquals(1, anchored.get(0).depth(), "模块行 = 片段第 1 层");
        assertEquals(2, anchored.get(1).depth(), "章跟着回到第 2 层");
        assertEquals(3, anchored.get(2).depth(), "叶子回到第 3 层");
        assertEquals("3. 数据链路层与局域网", anchored.get(0).name());
        assertEquals("成帧、差错流量控制与以太网", anchored.get(0).intent());
    }

    /** 正常片段（模块行顶格，同一任务第 2 章的真实格式）：锚定后原样 */
    @Test
    void 顶格片段锚定后不变() {
        String normal = "2. 物理层与数据通信 ｜ 传输介质、编码调制与信道复用\n"
                + "├── 2.1 传输介质与物理连接 ｜ 信号实际走的那条线\n"
                + "│   ├── 传输介质总览\n";

        List<OutlineText.Node> anchored = OutlineText.anchorFirstLevel(OutlineText.parse(normal));
        assertEquals(1, anchored.get(0).depth());
        assertEquals(2, anchored.get(1).depth());
        assertEquals(3, anchored.get(2).depth());
    }

    /** 模块行被缩进 8 格（沉两级）也能拉回来；后半段比首行还浅时钳到第 1 层 */
    @Test
    void 缩进更深时平移两级_浅行钳住() {
        String over = "        5. 传输层 ｜ 端到端通信\n"
                + "            ├── 5.1 UDP\n"
                + "├── 别的根\n";

        List<OutlineText.Node> anchored = OutlineText.anchorFirstLevel(OutlineText.parse(over));
        assertEquals(1, anchored.get(0).depth(), "沉两级的模块行拉回第 1 层");
        // 12 空格 + 树线 4 格 = 16 格宽（原第 5 层）⇒ 锚定后第 3 层 = 模块下两层，相对层级不变
        assertEquals(3, anchored.get(1).depth());
        assertEquals(1, anchored.get(2).depth(), "比首行还浅的畸形行钳到第 1 层，不产生非法深度");
    }

    /**
     * 章号识别（verifyTree 第五条检查的判据）：「N. 」是章号，「N.M 」节号不带尾点不算；
     * 「802.11」「2.4GHz」这类术语名绝不能误中（章号限 1~2 位 + 点 + 空格）。
     */
    @Test
    void 章号前缀识别() {
        org.junit.jupiter.api.Assertions.assertTrue(OutlineText.hasChapterIndex("3. 数据链路层与局域网"));
        org.junit.jupiter.api.Assertions.assertTrue(OutlineText.hasChapterIndex("12. 网络编程与工程实践"));
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex("3.1 数据链路层基础"), "节号「N.M 」不带尾点，不是章号");
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex("2.1.1 同步原语"));
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex("无线局域网802.11"), "不以数字开头");
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex("802.11 帧格式"), "802 三位数，不是章号");
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex("2.4GHz 与 5GHz"), "点后不是空格");
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex("数据链路层总览"));
        org.junit.jupiter.api.Assertions.assertFalse(OutlineText.hasChapterIndex(null));
    }
}
