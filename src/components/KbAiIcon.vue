<template>
  <!-- AI 图标：一个描边圆角方框里写 AI。
       选型过程（预览页并排比过 16px / 28px 两档）：
       ① 星芒   —— 有辨识度但要猜，"是 AI 吗"要看两秒
       ② 纯字母 —— 10.5px 在 16px 按钮里可读，但没有边界，孤零零两个字母
       ③ 实心方块 —— 最醒目，但灰底色块比旁边那排线性图标重一档，视觉不齐
       ④ 描边方框 —— 边界给了"这是一个标记"、字母给了"这是 AI"，描边又不是色块，和 Tools /
                    FullScreen / Delete 同一档。**最终选这个**。
       尺寸全用 em，跟着 .kb-icon-btn 的 font-size 一起缩放，和旁边 EP 图标自动同档。
       颜色用 currentColor，常态灰 / hover 深，跟着 .kb-icon-btn 的既有规则走。 -->
  <span class="kb-ai-icon" aria-hidden="true"><i>AI</i></span>
</template>

<script setup>
/** 纯展示，无 props 无状态。放 components/ 而不是 views 里：它是设计系统的一部分，
 *  知识点详情页的「AI 问答」和首页那个「问 AI 了解本系统」对话框共用同一个。 */
defineOptions({ name: 'KbAiIcon' })
</script>

<style scoped>
/* 这里刻意**不设 font-size**，让它继承 .kb-icon-btn 的 16px；
   下面所有 em 都相对这个 16px 算（宽高 1em=16px、边框 0.09em≈1.44px、内部字 0.58em≈9.3px）。 */
.kb-ai-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 1em;
  height: 1em;
  border: 0.09em solid currentColor;
  border-radius: 0.24em;
}
.kb-ai-icon > i {
  font-style: normal;
  font-weight: 700;
  font-size: 0.58em;
  line-height: 1;
  letter-spacing: -0.03em;
  color: currentColor;
}
</style>