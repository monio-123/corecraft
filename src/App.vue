<template>
  <router-view></router-view>
</template>

<style>
/* 全局 reset（`*, *::before, *::after` 的 margin/padding/box-sizing）在 base.css，
   这里原来还有一份**更弱的**第二份（不含伪元素）⇒ 已删。
   留下的这两行是**承重的**：全高链 html → body → #app 的前两环在这里，
   #app 那一环在 main.css（那边的注释指回这里），删了登录页和后台的 100% 高度就断。 */
html, body {
  height: 100%;
  width: 100%;
}
</style>
