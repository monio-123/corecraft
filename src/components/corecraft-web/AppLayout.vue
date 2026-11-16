<template>
  <!-- 收起时侧边栏宽度归零，.app-main 是 flex:1 会自动铺满。
       目录也跟着一起看不见——按约定不额外做目录入口，要切目录就点顶栏这个按钮展开。 -->
  <div class="app-shell" :class="{ 'is-side-collapsed': sideCollapsed }">
    <!-- 左侧栏：品牌 + 目录。沉浸模式下整个不渲染（它就是"壳"的一部分）。

         ⚠️ **这一列是纯导航了**（2026-11-03）：品牌区 + 过滤框（框内两个操作）+
         一列书名 + 每行的条数。**没有行内控件了** ——
         「⋯」（目录操作）挪到目录页（看着它才操作它），
         「›」（就地展开子树）删掉（展开子树由目录页做，它更宽、能分层、知识点能点）。
         ⇒ 这里也**不需要"点侧边栏收树"那个 handler**（树不在侧边栏了）。 -->
    <aside v-if="!immersive.on" class="app-side">
      <!-- 品牌区 = **首页入口**（2026-11-02）。
           导航块整个撤掉之后侧边栏只剩"目录"这一件事，而"回首页"总得有个地方放 ——
           品牌区是全站唯一的固定位置，放这儿最不占地方（原来它只是个静态标题）。
           用 RouterLink 而不是 div onclick：语义上就是链接，右键复制 / 新标签页都是白送的。 -->
      <RouterLink class="app-side__brand" to="/app" aria-label="首页">
        <div class="app-side__logo">Bro</div>
        <div class="app-side__name">知识库</div>
      </RouterLink>

      <!-- 目录：常驻侧边栏，只放一级（**过滤时例外**，见下面那个输入框）。
           层级不在这里常驻——264px 撑不住缩进，而层级只在两件事上才需要被看见：
           放东西（拖拽浮出完整树）和进深层（点 › 展开整棵子树）。

           这一块有两个落点角色：
           - **每一行**（data-cat-id）：拖到那一行 = 挪到那个目录下
           - **整块的空白处**（data-cat-root）：拖上去 = 挪成一级目录
           两者靠 hitCategory 的判断顺序分开（先找行、再退到块）。 -->
      <div
        class="app-side__section"
        data-cat-root
        :class="{ 'is-drop': dnd.over === ROOT_CATEGORY && dnd.overOk, 'is-bad': dnd.live && dnd.over === ROOT_CATEGORY && !dnd.overOk }"
      >
        <!-- 目录区抬头（2026-11-05 晚**回来**了）。
             ⚠️ **原来这行 2026-11-03 晚删过**：理由是"搜索框自己就说明了这块是什么"。
             现在那个「过滤目录」框**并进顶栏那个公共搜索**了（框没了）⇒ 不写一行"目录"
             就不知道这块是什么 ⇒ 抬头回来。
             ⚠️ **右边两个按钮原来在搜索框的 suffix 里**（框内右侧）：框没了 ⇒ 挪到抬头行右边。 -->
        <div class="app-side__cap">
          <span class="app-side__cap-title">目录</span>
          <span class="app-side__ops-row">
            <!-- 一个入口：**生成主题**（主题 → 规划 → 提交后台逐条写正文）。
                 生成的目录自成一体、放在第一层；挂到哪、怎么挪，用户自己在侧边栏拖。
                 为什么叫"生成"不叫"导入"：这套体系还没长出来的时候，"生成"才是
                 用户真正缺的那件事（"导入"的前提是先有东西可导）。 -->
            <KbTooltip content="生成主题" placement="right">
              <button class="app-side__op-btn" aria-label="生成主题" @click="openTreeDialog">
                <KbSvgIcon name="sparkle" />
              </button>
            </KbTooltip>
            <!-- 任务：生成搬到服务端之后，进度/停止/继续都在这里看（关页面不影响） -->
            <!-- 角标 = **等你处理**的任务数（待确认 + 失败，口径在 useGenTasks 的
                 attentionTaskCount）。进行中不算 —— 红点跟着后台常亮只会训练人无视它，
                 亮了就该是「该你了」（2026-11-13 用户拍板，原为「进行中 + 等确认」）。 -->
            <KbTooltip content="任务" placement="right">
              <button class="app-side__op-btn app-side__op-btn--badge" aria-label="任务" @click="openTasks">
                <KbSvgIcon name="clock" />
                <i v-if="attentionTaskCount" class="app-side__badge">{{ attentionTaskCount > 9 ? '9+' : attentionTaskCount }}</i>
              </button>
            </KbTooltip>
            <KbTooltip content="新建目录" placement="right">
              <button
                class="app-side__op-btn"
                aria-label="新建目录"
                @click="openCreate()"
              >
                <KbSvgIcon name="plus" />
              </button>
            </KbTooltip>
          </span>
        </div>

        <!-- 「全部目录」：搜索态下的"**不收窄到某本书**" ⇒ 回到所有符合的卡片。
             ⚠️ **只在正文档档出现**（用户 2026-11-05：「在搜索正文场景如果点击到了某个一级目录
             如果还想开整体就退回不了了 在左侧边栏加一个全部目录吧」）：
             目录档的结果就是"一棵目录树"，"全部"在那儿没有对应的形态（树总得有一本书做根）。
             ⚠️ **它不在那摞书里、单独一行**：它是"不去哪本"，不是"哪一本"。 -->
        <RouterLink
          v-if="showAllItem"
          class="app-cats__item app-cats__all"
          :class="{ 'is-on': !route.query.tree }"
          :to="allLink"
        >
          <span class="app-cats__label">
            <span class="app-cats__name">全部目录</span>
          </span>
          <span class="app-cats__count">{{ allHitTotal }}</span>
        </RouterLink>

        <div v-if="visibleCats.length" class="app-cats">
          <RouterLink
            v-for="c in visibleCats"
            :key="c.id"
            :to="bookLink(c)"
            class="app-cats__item"
            :class="{
              'is-on': isCurrentCategory(c.id),
              'is-drop': dnd.over === c.id && dnd.overOk,
              'is-bad': dnd.live && dnd.over === c.id && !dnd.overOk
            }"
            :data-cat-id="c.id"
            :data-cat-name="c.name"
            :draggable="false"
            @pointerdown="onCatPointerDown"
          >
            <span class="app-cats__label">
              <span class="app-cats__name">{{ c.name }}</span>
            </span>
            <!-- 侧边栏角标：掌握度迷你进度条（设计 §8.3「轻量掌握度进度」）+ 条数。
                 掌握度数字不进窄侧栏（悬浮 title 补已学/评估覆盖）；无数据=空轨道。 -->
            <MasteryBar :row="getCategoryMastery(c.id)" />
            <span class="app-cats__count">{{ categoryCounts[c.id] || 0 }}</span>
            <!--
              这一行**只有名字和条数**（2026-11-03）：
              - 「⋯」（新增子目录 / 写知识点 / 重命名 / 删除）→ 挪到目录页里那一行，
                **规则一句话：在哪儿看着它，就在哪儿操作它**。侧边栏是纯导航。
              - 「›」（就地展开子树）→ 删：展开子树这件事目录页做得更好
                （宽栏、分层、知识点能点），侧边栏这 264px 撑不开层级。
              - 「树 ⇄ 卡片」切换钮 → **2026-11-08 删**（用户：「可以把侧边栏一级目录的
                那个切换图标也给去了」）—— 切换收进树视图标题行的分段式
                「目录树 | 卡片」（就地换内容盒子），侧边栏回归纯导航。
            -->
          </RouterLink>
        </div>
        <!-- ⚠️ **「没有目录」这句话只在真的加载完之后才成立**。
             加载中就说它，是句假话（用户明明有目录），看着像"目录被清空了"。
             所以分「加载中」「加载失败」「过滤没命中」「真的没有」四种情况。

             ⚠️ **加载中 = 紧凑局部指示**（2026-11-13 统一加载层）：
             一枚小图形 + 一句话，不是居中大转圈 —— 侧栏只有 264px 宽，
             40px 上下留白的大图形会把这一格撑得比下面整列都高。 -->
        <p v-else-if="categoriesState === 'loading'" class="app-side__empty app-side__loading">
          <KbLoader size="sm" />
          <span>加载中…</span>
        </p>
        <!-- §4.7(o)：「加载失败」在同一个壳里原本有两种形态（这里是一句光秃秃的
             文字，底栏那边是「加载失败 + 刷新图标」），而 AppLearnView 是
             「加载失败 + 重试按钮」⇒ 三处三种形态。
             统一成「一句状态 + 一个**重试**按钮」，文案也统一。
             用 .kb-center--bare（20px 上下内距）而不是 .kb-center（40px）——
             侧栏只有 264px 宽，40px 的上下留白会把这一格撑得比下面整列都高。 -->
        <div v-else-if="categoriesState === 'failed'" class="kb-center kb-center--bare">
          <p>加载失败</p>
          <el-button @click="loadShell">重试</el-button>
        </div>
        <!-- 搜索没命中 ≠ 没有目录：目录都在，是用户搜的词没匹配上。
             两种情况说同一句话的话，用户会以为自己的目录被删了。 -->
        <p v-else-if="searching" class="app-side__empty">没有匹配</p>
        <p v-else class="app-side__empty">没有目录</p>
      </div>

      <!-- 底部：用户。**会话拉失败时这一格换成「加载失败 + 重试」**——
           位置不变、文案如实说、按钮形态不变，只是从"谎称未登录"变成"让你重来一次"。
           为什么不能显示「未登录」：真没登录时 401 拦截器已经 clearAuth + 弹到登录页了，
           能站在这里看见侧边栏就说明 token 是好的（建目录、查目录都调得通，
           那些请求不走 bootstrapSession）。那一格实际含义是"资料没加载出来"。
           判断依据是**共享的** sessionState（两个壳同一份，见 utils/session.js）。
           ⚠️ §4.7(o)：这个「重试」原来是一个**纯图标**按钮（刷新图标），
           标签是「重新加载」；而列表页那边是**文字按钮**「重试」⇒ 同一个动作
           两种形态、两种叫法。现在统一成文字按钮「重试」。
           ⚠️ 这里不用 .kb-center：这一格是侧边栏**底部的横向一行**，
           .kb-center 是竖排居中 + 40px 上下内距 ⇒ 套进来会把底栏撑成半屏高。 -->
      <div class="app-side__user">
        <template v-if="sessionState === 'failed' || shellDataFailed">
          <span class="app-side__failed">加载失败</span>
          <el-button @click="loadShell">重试</el-button>
        </template>
        <!-- 用户菜单：**两个壳共用这一份**（见 components/UserMenu.vue）。
             菜单项和管理后台一样：编辑个人信息 / 修改密码 / 退出登录。
             「AI 能力」不在这里 —— 它在右边那个齿轮里（见下面）。

             **退出登录不用监听**：确认框、清登录态、提示、跳登录页都在
             UserMenu 里做完了（两个壳共用那一份）；这个壳退出时没有要额外清的
             ——数据归 store 的 onAuthChange、AI 配置归 useAi.js 的 onAuthChange。

             它**不装在 v-if / v-else 里**：加载失败时那一条是"重试"不是"用户"，
             但侧边栏照样在、宽度和密度照样该能调——**没有理由让设置跟着数据一起消失。** -->
        <UserMenu
          class="app-side__user-menu"
          :user="currentUser"
        />

        <!-- 设置（齿轮 = 系统设置入口，2026-11-13）。**不塞进顶栏**：它管的是
             侧边栏 / 首页 / 知识点 / 详情这些版面，放在侧边栏底栏才是"哪儿的问题
             回哪儿改"。admin 壳压根没有侧边栏，没有对应物。
             **放在用户菜单之后**：`.app-side__user-menu` 是 `flex: 1`，撑满剩下的
             宽度，齿轮自然落到最右边——放它前面就会被顶到左边去。

             齿轮点开「设置」弹窗（SettingsDialog）：分类目录 + 右区内容 ——
             「界面」（风格 + 四组滑块）出右区，「AI 能力」也出右区，后续设置往
             目录里加项。⚠️ 目录里的项一律出右区，只有动作表单才叠弹窗
             （"界面直接显示、AI 单独弹窗"被判割裂，2026-11-13 收口）。
             ⚠️ **齿轮就是弹窗自己的触发器**：曾经试过齿轮套 el-dropdown、面板用
             虚拟触发器挂它上面 —— 同一元素两个触发器，点一下两个都响应。 -->
        <SettingsDialog :groups="['side', 'home', 'learn', 'detail']" />
      </div>
    </aside>

    <!-- 右侧：顶栏 + 内容 -->
    <div class="app-main">
      <!-- ⚠️ **--search-w = 搜索框宽度**（2026-11-09）：顶栏的宽度分配是算出来的，
           不是 CSS 猜的。标准宽 320 / 下限 160 写在脚本里（SEARCH_W_MAX / SEARCH_W_MIN），
           这里只把算出来的结果喂给 CSS。 -->
      <header
        v-if="!immersive.on"
        ref="topBarEl"
        class="app-top"
        :style="{ '--search-w': searchWidth + 'px', '--crumb-w': crumbWidth + 'px' }"
      >
        <div class="app-top__left">
          <!-- 纯图标按钮必须自带可访问名：tooltip 只管看，屏幕阅读器读的是 aria-label。
               ⚠️ 提示走 KbTooltip（全站统一壳，2026-11-11）：不是裸 el-tooltip。
                  裸 el-tooltip 用 EP 默认值会有两个用户可见的毛病 ——
                  ① 桌面端鼠标滑进提示框就不算"离开"，提示赖在屏幕上不走
                  ② 平板触屏 tap 后没有 mouseleave，提示**永远不消失**（用户原话：
                     「特别是平板触屏使用的时候一直不消失 看着很烦」）
                  KbTooltip：桌面移开立刻关；触屏改成点一下弹、1.2 秒后自动走。 -->
          <KbTooltip :content="sideToggleLabel" placement="bottom">
            <button class="app-top__icon-btn" :aria-label="sideToggleLabel" @click="toggleSide">
              <KbSvgIcon :name="sideCollapsed ? 'expand' : 'fold'" />
            </button>
          </KbTooltip>
          <!-- 顶栏那条：**目录路径**（一级目录/二级目录/…），没有目录上下文时才是路由标题。
               详情页由详情页把这条知识的目录归属报上来（store 的 openTopicCategoryId）。
               ⚠️ **整条一个字号（16px 标题字号），主次只靠粗细/颜色**（2026-11-13 二稿，
               用户看过一稿后「次级目录比一级目录小并不好看」）：**当前那一截 = 标题体**
               （粗·深，和「首页」/书页单名同一套），**祖先链 = 细浅**——"你在这一页"
               是这一格的重点，祖先只是"你怎么来的"（老稿 .d-crumb「祖先 muted、.here ink」
               的结构升到标题字号）。
               ⚠️ **形态（整条链 / 收掉中间层级）由本组件定**（2026-11-09）：
               宽度怎么分是顶栏在算（它同时管搜索框），这里只报两种形态各有多宽。 -->
          <div ref="crumbBoxEl" class="app-top__crumb">
            <CategoryCrumbs
              v-if="topCategoryId != null"
              :category-id="topCategoryId"
              :mode="crumbMode"
              @pick="pickCrumb"
              @measure="onCrumbMeasure"
            />
            <template v-else>{{ topTitle }}</template>
          </div>
        </div>
        <div class="app-top__actions">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索知识点…"
            class="app-search"
            @keyup.enter="goSearch"
            @focus="searchFocused = true"
            @blur="searchFocused = false"
          >
            <template #prefix>
              <KbSvgIcon name="search" />
            </template>
            <template #suffix>
              <!-- ⚠️ **一个搜索框，三副面孔**（2026-11-09 用户两次拍板：
                   「复用现有的搜索框就行，那个向上或者下的跳也加在搜索框中就行」
                   +「目录树的那个定位框也跟这里一样复用上面那个就行」）：
                   - **详情页** = 本文实时检索 ⇒ 「命中计数 + ↑↓」（跳正文里的上/下一处）；
                   - **列表页·目录档** = 命中在树里 ⇒ 同样是「命中计数 + ↑↓」（跳树里的上/下一行）
                   —— ⚠️ 树里原来那个独立的可拖浮条已撤（`CategoryTree` 的命中浮条），
                   计数与 ↑↓ 统一到这里 ⇒ **同一个框、同一套 ↑↓、同一个口径**；
                   - **列表页·正文档** = 纯过滤（命中什么只剩什么）⇒ 这里保留「正文/目录」范围切换。
                   ⇒ 三者互斥，不叠加（v-if / v-else-if / v-else）。
                   ⚠️ **计数口径两处一致**：定位了印「k/N」，没定位印「N 处」。 -->
              <template v-if="isDetail">
                <!-- ⚠️ **只在"已经输入了搜索内容"时才显示**（2026-11-10 用户：
                     「应该是在我在正文且输入了要搜索的内容的时候再显示，而不是只要进来了正文就显示」）。
                     原来这里只判 `isDetail` ⇒ **一进正文页就冒出两个箭头**，
                     而此时正文里根本没有检索词、也没有任何命中 ⇒ 箭头点不动（disabled）、
                     计数也是空的 ⇒ 纯噪声。
                     ⚠️ **判据用「有检索词」而不是「有命中」**：有词但没命中（如拼错）
                     时计数该显示「0 处」—— 那是有信息的状态（"搜了但没找到"）；
                     用命中数当判据反而会把它藏掉。 -->
                <SearchHitsControls
                  v-if="bodySearchKeyword.trim()"
                  :hits="bodySearchHits"
                  @jump="jumpHits"
                />
              </template>
              <template v-else-if="isCatMode(searchMode) && treeSearchHits.total > 0">
                <SearchHitsControls :hits="treeSearchHits" @jump="jumpTreeHits" />
              </template>
              <!-- ⚠️ **搜索范围切换**（2026-11-05，用户拍板「在搜索框中加一个切换按钮」）：
                   **两档** ——「正文」搜知识点正文、结果是**卡片**；「目录」搜目录
                   （目录名 + 知识点标题）、结果是**目录树**。
                   ⚠️ **它必须常显**：结果长什么样全靠它定，藏进 hover 里就没人用得上。
                   ⚠️ **2026-11-06 挪到框内的右侧**（用户：「图一这个正文切换按钮放到框内的右侧去吧」）
                   ⇒ 放 **suffix** 槽，放大镜留在 prefix。
                   ⚠️ **列表页时它和命中计数是并列的**（不是互斥）：计数只在目录档出现，
                   而档位切换按钮**必须常显**（藏了就没法换档了）。 -->
              <!-- ⚠️ **不挂 title**（2026-11-06 用户：「这个提示词给我去掉」）：
                   原来那句「现在搜目录（目录名 + 知识点标题），结果用目录树看。点一下改成搜正文。」
                   是**一整段解释** —— 而按钮上就写着「正文」/「目录」两个字，悬停出来的那段
                   是在替用户复述界面上看得见的东西（skill §8：界面提示绝大多数不需要）。
                   `aria-label` 留着：那是给读屏器念的，不是给人看的浮层。 -->
              <button
                v-if="!isDetail"
                type="button"
                class="app-search__mode"
                :aria-label="`搜索范围：${searchModeLabel}`"
                @click="toggleSearchMode"
              >{{ searchModeLabel }}</button>
              <!-- ⚠️ **清除按钮自己画，不用 EP 的 `clearable`**（2026-11-09 用户报
                   「点击那个上下箭头后 搜索词都直接不见了」）：
                   EP 的 × 是**悬停/聚焦才渲染**的（`showClear = clearable && 有值 && (focused || hovered)`），
                   而 ↑↓ 就住在同一个 suffix 槽里 ⇒ **鼠标移向箭头时 × 突然冒出来**，
                   把箭头往右挤 ⇒ 用户瞄准 ↓ 却点中了 × ⇒ 词被清掉。
                   ⚠️ **自己画的 × 位置固定在最后** ⇒ 箭头不会被挤，位置永远不变。
                   ⚠️ **不能删**（2026-11-10 用户：「就算不删这个 bug 也要修 我手动清空这里也该一样啊」
                   ⇒ 清空是**必需的手动出口**）⇒ 保留，只把它改成与 ↑↓ 同一套视觉
                   （无壳图标钮 + hover 反馈），别让它跟旁边的箭头长得不像一家人。 -->
              <button
                v-if="searchKeyword"
                type="button"
                class="app-search__clear"
                aria-label="清空搜索"
                @click="clearSearch"
              ><KbSvgIcon name="close" /></button>
            </template>
          </el-input>
          <KbTooltip v-if="hasAdminShell" content="管理后台" placement="bottom">
            <button class="app-top__icon-btn" aria-label="管理后台" @click="goAdmin">
              <KbSvgIcon name="gear" />
            </button>
          </KbTooltip>
        </div>
      </header>

      <!-- is-immersive 这个类同时是给全页 CSS 的开关标记：
           编辑器撑满（MilkdownEditorBody 的全局块）和首页只留录入区都认它。 -->
      <main class="app-content" :class="{ 'is-immersive': immersive.on }">
        <RouterView v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" :key="route.name" />
          </keep-alive>
        </RouterView>
      </main>
    </div>

    <!-- 拖拽幽灵：跟着指针的一行标题（拖的是目录名）。
         ⚠️ **知识点拖拽归类整套删了**（2026-11-05 用户：「图二是最早做的拖动归类
         也直接删掉吧」）⇒ 原来那棵「放到哪个目录」的落点浮层（mode="drop" 的树）
         永远出不来 ⇒ 连同它的定位计算一起撤掉。
         幽灵保留：拖目录换父级还要它（告诉用户"手里拿的是哪一个"）。 -->
    <div
      v-if="dnd.live && dnd.label"
      class="dnd-ghost"
      :style="{ left: dnd.x + 'px', top: dnd.y + 'px' }"
    >{{ dnd.label }}</div>

    <!-- ⚠️ **侧边栏展开子树的那个弹层删了**（2026-11-03）：点一级目录行尾的「›」
         展开整棵子树的那一套（遮罩 + 定位 + 开合状态）。
         理由：展开子树这件事现在由**目录页**做（主区里那棵树）—— 它更宽、能分层、
         知识点能直接点；侧边栏 264px 展开出来是两百多行的一根长条。
         ⇒ 侧边栏那列退回**纯导航**（书名 + 条数）。 -->

    <!-- 「给目录起个名」：**只剩新建一级目录**这一种用法（新建子级 / 改名都跟着目录页走了）。
         ⚠️ 其它参数全用组件自己的默认值（mode=create / parentId=ROOT_CATEGORY /
         initialName='' / initialId=null）—— 那几个值在这里是常量，写出来就是第二份。 -->
    <CategoryNameDialog v-model="nameDialog.visible" />

    <!-- 生成主题：主题 → 规划 → 提交后台逐条写正文。
         `plan-task` / `plan-outline`：任务中心「继续编辑」把一棵已经生成好的树交回来，
         弹窗直接跳到第 2 步（不再重新调模型）。 -->
    <CategoryTreeDialog
      v-model="treeDialog.visible"
      :plan-task="treeDialog.planTask"
      :plan-outline="treeDialog.planOutline"
      @done="onTreeDialogDone"
    />

    <!-- 删目录的确认框：**全站一个实例**（树上那个「⋯」菜单有三处，
         各挂一个就是三个实例）。它带一个「同时删除 N 条知识点」的选项，
         默认不勾 —— 见 DeleteCategoryDialog.vue 的注释。 -->
    <DeleteCategoryDialog />

    <!-- 任务面板：生成搬到服务端之后，进度 / 停止 / 继续都在这儿 -->
    <GenTaskPanel ref="taskPanel" @open-plan="openPlanTask" @open-answer="openAnswerTask" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
// ⚠️ ArrowUp / ArrowDown 已移走（2026-11-11 收敛）：搜索框的「命中计数 + ↑↓」
// 变成 `SearchHitsControls` 组件了 ⇒ 图标跟着那个组件走，本文件不再用。
// ⚠️ Refresh 已删：2026-11-06 §4.7(o) 把侧边栏底栏那个「刷新图标」换成了
// 文字按钮「重试」（与列表页同一个形态、同一个叫法），这个图标全文件就没用了。
import KbSvgIcon from './KbSvgIcon.vue'
import KbTooltip from './KbTooltip.vue'
import KbLoader from './KbLoader.vue'
import SearchHitsControls from './SearchHitsControls.vue'
import { viewLink, categoryLink } from '../../utils/categoryNav'
import { hasAdminMenus, bootstrapSession, sessionState, sessionProfile } from '../../utils/session'
import { applyUiPrefs, clearUiPrefs } from '../../utils/uiPrefs'
import UserMenu from '../UserMenu.vue'
import SettingsDialog from './SettingsDialog.vue'
import CategoryNameDialog from '../../views/corecraft-web/CategoryNameDialog.vue'
import MasteryBar from '../../views/corecraft-web/MasteryBar.vue'
import CategoryCrumbs from '../../views/corecraft-web/CategoryCrumbs.vue'
import CategoryTreeDialog from '../../views/corecraft-web/CategoryTreeDialog.vue'
import { attentionTaskCount, startGenTaskPolling, POLL_SLOW } from '../../views/corecraft-web/useGenTasks'
import GenTaskPanel from '../../views/corecraft-web/GenTaskPanel.vue'
import DeleteCategoryDialog from '../../views/corecraft-web/DeleteCategoryDialog.vue'
import { useCategoryDrag } from '../../views/corecraft-web/useCategoryDrag'
import { loadShellData } from '../../views/corecraft-web/useShellData'
import { keywordFromRoute } from '../../utils/routeQuery'
import { taskType } from '../../utils/taskTypes'
import { useImmersive } from '../../views/corecraft-web/useImmersive'
import {
  getChildCategories, categoriesState,
  getCategoryCountMap, ROOT_CATEGORY,
  getCategories, getCategoryPath, ensureTopicIndexLoaded,
  ensureCategoryMasteryLoaded, getCategoryMastery,
  openTopicCategoryId, bodySearchKeyword, bodySearchHits, bodySearchJump,
  treeSearchHits, treeSearchJump,
  refreshTopicIndexes, bumpDataVersion
} from '../../views/corecraft-web/useCorecraftWebStore'
import { dnd } from '../../views/corecraft-web/useCategoryDnd'
import { visibleRootCategoriesOf } from '../../utils/categoryTree'
import {
  searchModeFromRoute, searchScopeOf, isCatMode,
  SEARCH_MODE_CONTENT, SEARCH_MODE_CAT, SEARCH_MODE_LABEL
} from '../../utils/searchMode'

const route = useRoute()
const router = useRouter()

// 初值取自 URL：带着 ?q= 进来的（刷新 / 分享链接）框里就有那个词，见下面那段注释。
// **关键词的读法只有一处**（utils/routeQuery.js），别在这儿再抄一遍 `route.query.q`。
const searchKeyword = ref(keywordFromRoute(route))

// 侧边栏收起状态。存 localStorage 而不是只放内存：收起是有代价的——目录树跟着一起看不见，
// 一次偶然点错不该让下次打开还是收着的。
// 不写进 utils/auth.js：那是登录态，登出时 clearAuth 会 localStorage.clear() 把它一起抹掉，
// 界面偏好不该跟账号走。
const SIDE_COLLAPSED_KEY = 'app-side-collapsed'
const sideCollapsed = ref(localStorage.getItem(SIDE_COLLAPSED_KEY) === '1')

// 图标按钮没有文字可读，名字要同时喂给 tooltip 和 aria-label，只留这一处来源
const sideToggleLabel = computed(() => (sideCollapsed.value ? '展开侧边栏' : '收起侧边栏'))

function toggleSide() {
  sideCollapsed.value = !sideCollapsed.value
  localStorage.setItem(SIDE_COLLAPSED_KEY, sideCollapsed.value ? '1' : '0')
}

// 目录树弹层的开合状态。展开靠点行尾的 ›（见 toggleCascade），关靠点遮罩 / Esc 之外的
// 那几条业务路径（进目录 / 新建子级 / 改名都会顺手关掉）。
/**
 * 目录生成弹窗。**只用于"生成主题"**（mode 默认 'pack'）。
 *
 * <p>⚠️ 给某个目录写知识点（mode='points'）那个用法搬到了**目录页**里
 * （2026-11-03）—— 在哪儿看着它，就在哪儿操作它。
 *
 * <p>（2026-11-10 起只有一条链路：先出规划 → 建结构 → 提交服务端任务写正文。
 * 老的"前端本地循环"那条已整条删除。）
 */
const treeDialog = reactive({ visible: false, planTask: null, planOutline: '' })

/** 任务面板（生成搬到服务端之后的进度入口） */
const taskPanel = ref(null)

/**
 * 打开任务面板。
 */
function openTasks() {
  taskPanel.value?.open()
}

/**
 * 侧边栏每个一级目录**也是拖拽源**：把它拖到另一棵目录下 = 挪到那个目录下，
 * 拖到目录区空白处 = 挪成一级。
 *
 * **模板里那三个 data-* / draggable="false" / @pointerdown 都缺一不可**：
 * - 行上挂 `data-cat-id` / `data-cat-name` 是为了共用一个 handler 就能认出是哪一行
 * - **`draggable="false"` 是关键**：这些行是 `RouterLink`（`<a href>`），**`<a>` 默认
 *   `draggable=true`**，一按就启动浏览器原生拖拽——拖起来是个带 URL 的灰色 tooltip，
 *   pointerdown 收不到、我们这套完全不触发。2026-10-07 实机就撞了这个：
 *   "脱不进去"，实际从头到尾是浏览器在拖链接。
 */
const { onPointerDown: onCatPointerDown } = useCategoryDrag((e) => {
  const id = Number(e.currentTarget?.dataset?.catId)
  if (!id) return null
  return { catId: id, label: e.currentTarget.dataset.catName || '' }
})

// 「给目录起个名」弹窗。**这里只剩新建一级目录**（新建子级 / 改名都跟着目录页走了）
// ⇒ 其余参数全用组件自己的默认值，不在这儿再抄一份。
const nameDialog = reactive({ visible: false })

// 侧边栏只渲染一级（parentId = 0）。层级在**目录页**里看。
const rootCategories = computed(() => getChildCategories(ROOT_CATEGORY))
const categoryCounts = computed(() => getCategoryCountMap())

/**
 * **当前搜索模式**（URL 上的 `?mode=`）。
 *
 * <p>**两档**：搜正文（结果给卡片）/ 搜目录（结果给树）。
 * ⚠️ **它进 URL** —— 结果长什么样是这次搜索的一部分 ⇒ 得能分享、能后退。
 */
const searchMode = computed(() => searchModeFromRoute(route))

/**
 * **下一搜用哪一档**（就是顶栏那个按钮上写的字）。
 *
 * <p>⚠️ **它和 `searchMode` 是两件事，别合并**（2026-11-13 用户：
 * 「切换这个目录/正文会导致页面跳转到卡片栏里面去 …… 要等我回车」）：
 * - `searchMode`（URL 的 `?mode=`）= **这一屏结果正在用的**那一档；
 * - `pendingMode`（本地）= 用户刚点出来的、**下一搜要用**的那一档。
 *
 * <p>上一版把它们合成一个、切档直接 `router.push('/app/learn')` ⇒
 * **在首页点一下就跳进卡片栏**。档位是"下一搜的选项"，不是一次导航。
 *
 * <p>URL 那边变了（回车提交 / 后退 / 外链带着 mode 进来）⇒ 跟过去，
 * 保证"刷新后按钮显示的就是这一屏在用的那一档"。
 */
const pendingMode = ref(searchModeFromRoute(route))
watch(searchMode, (m) => {
  pendingMode.value = m
})
/** 切换按钮上写什么。 */
const searchModeLabel = computed(() => SEARCH_MODE_LABEL[pendingMode.value])

/**
 * **切搜索的那一档**（正文 ⇄ 目录）。
 *
 * <p>⚠️ **不导航、不刷新、不改 URL**（2026-11-13 用户：
 * 「在首页切换右上角的这个目录/正文 会导致页面跳转到卡片栏里面去
 * …… 页面逻辑应该也不能变吧 要等我回车」）。
 *
 * <p>上一版这里直接 `router.push({ path: '/app/learn', query: {...} })`：
 * - 在**首页**点 ⇒ 被拽进卡片栏（用户根本没想搜东西）；
 * - 在**列表页**点 ⇒ 立刻按新档重拉一次（换的是另一批命中集）。
 *
 * <p>⇒ 现在它只改 `pendingMode`：**这一屏的结果和地址一个字都不动**，
 * 按回车（{@link goSearchWith}）时才把档位写进 URL、真正搜一次。
 * 用户拍板的就是这个顺序：「先选档，回车才搜」。
 *
 * <p>⚠️ **URL 上的 `?mode=` 是"上一搜用的档"，在这里保持原样** ——
 * 它决定这一屏结果长什么样（卡片 / 目录树），而用户还没说"重搜"。
 */
function toggleSearchMode() {
  pendingMode.value = isCatMode(pendingMode.value) ? SEARCH_MODE_CONTENT : SEARCH_MODE_CAT
}

/**
 * **当前搜索词**（顶栏那个公共搜索框的值，也就是 URL 上的 `?q=`）。
 *
 * <p>⚠️ **原来侧边栏自己有一个「过滤目录」框**（2026-11-05 晚删）：它按**目录名**筛，
 * 而且**只在这一屏生效、不进 URL**。现在并进顶栏那个公共搜索 ⇒
 * 「在哪找」和「搜什么」合成一个动作。
 */
const keyword = computed(() => keywordFromRoute(route))
const searching = computed(() => keyword.value.trim().length > 0)

/**
 * 侧边栏那一行（书）点过去是哪个地址。
 *
 * <p>⚠️ **有搜索词时，形态由档位决定**（2026-11-05 晚用户拍板）：
 * - **目录档** ⇒ 这本书 = **一棵树**（`?tree=`）；
 * - **正文档** ⇒ 这本书 = **一摞卡片**（`?tree=` 收窄到这本书的子树）。
 *
 * <p>⚠️ **两档的"范围"是同一个参数**（`?tree=` = 哪本书）⇒ **切档不丢范围**
 * （在正文档选中的那本书，切到目录档还是它的树）。
 *
 * <p>⚠️ **正文档这一档原来是个死点击**：链接给的是 `?tree=`，而那一档形态由档位决定
 * （树不画）⇒ `tree` 不起作用 ⇒ 点完还是全局那几十条，**书名后面的数字和看到的对不上**。
 *
 * <p>⚠️ **没搜索词时按偏好走**（`viewLink`）：没有搜索就没有"结果形态"这回事，
 * 「这本书用树看还是卡片看」是用户自己记下的偏好（`utils/viewPref.js`）。
 *
 * <p>⚠️ **正文档下再点已选中那本 = 取消选中**（回到"所有符合的卡片"）：
 * 收窄之后**没有别的入口能退回来**（面包屑只画两级以上，一级目录那一屏什么都不画），
 * 而"没选中一级目录就展示所有符合的卡片"是默认态（用户 2026-11-05 晚原话）。
 *
 * <p>⚠️ **搜索词得跟着走**：搜索时侧边栏只列命中的书 ⇒ 点进去要看的正是
 * "这本书里命中的那几条" ⇒ 丢了 `q` = 点进去看到整本书（195 条），而不是那几行命中。
 */
function bookLink(c) {
  // ⚠️ **搜索词一律跟着走，不按所在页面分**（2026-11-11 用户拍板，**覆盖** 2026-11-09 的口径）。
  //
  // <p>2026-11-09 的口径是「**从详情页点书 = 进新目录，那次搜索就此结束**」：那时用户报
  // 「只要进新目录就回带上那个搜索」「搜索粘着删不掉」⇒ `q`/`mode` 在详情页被扔掉，
  // 跳过去 URL 干净、顶栏框跟着清空。
  //
  // <p>2026-11-11 用户报的正好是反面（「输入搜索条件回车之后，左侧边栏会过滤出满足条件的
  // 一级目录，但是如果我切换一级目录，上方的搜索条件就掉了」）—— **搜索期间侧边栏只剩
  // 命中的书**时，"换一本"显然还是"在这批命中的书之间来回看"，把词扔了等于每次切书都得
  // 重新搜一遍。⇒ 判据改成**只有"有没有搜索词"**，与所在页面无关：
  // - **有词** ⇒ 词和档位一律带走（详情页切书也带走）⇒ 切完仍是"这本书里命中的内容"；
  // - **没词** ⇒ 一个参数都不加（URL 干净，不出现空的 `?q=`）。
  //
  // <p>⚠️ 当年"粘着删不掉"的顾虑仍然成立（带词进书页后，点卡片会带词定位）⇒ 出口是
  // **顶栏那个 ×**（`clearSearch`：框 / store 通道 / URL 三处一起清）—— 手动清一次就干净，
  // 不再靠"进新目录自动清"这种看不见的规则。
  const keep = {
    q: keyword.value || undefined,
    mode: isCatMode(searchMode.value) ? SEARCH_MODE_CAT : undefined
  }
  // 没有搜索词 ⇒ 按「这本书用树看还是卡片看」的偏好走（原来的行为不变）。
  if (!searching.value) return viewLink(c)
  // 正文档：已经选中这一本 ⇒ 取消选中（回到所有符合的卡片）。
  if (!isCatMode(searchMode.value) && Number(route.query.tree) === c.id) {
    return { path: '/app/learn', query: { ...keep } }
  }
  return { path: '/app/learn', query: { tree: c.id, ...keep } }
}

/**
 * 侧边栏这一块显示哪些目录。
 *
 * <p>**平时 = 一级目录**（层级在目录页里看）。
 *
 * <p>⚠️ **有搜索词时 = 命中的那些一级目录**（2026-11-05 晚）：
 * 判据是 {@link hitCategoryIds}（内容命中 ∪ 名字命中）——
 * - **底下有知识点命中的一级书**（服务端按关键词筛过的结构索引给出的计数）；
 * - **名字命中的书**（以及它的某一级子树里名字命中的书）。
 *
 * <p>⚠️ **侧边栏永远只画一级**（用户 2026-11-05：「左侧的一级目录就只展示一级目录
 * 不要把搜索的结果目录加在左边一级目录中展示」）：
 * 命中的**深层目录不往这儿塞** —— 它在**右边的树**里显示（见 CategoryTree 的过滤）。
 * 侧边栏是导航，一层就够；层级在目录页里看。
 */
const visibleCats = computed(() =>
  visibleRootCategoriesOf(
    route,
    rootCategories.value,
    getCategories(),
    categoryCounts.value
  )
)

/**
 * **「全部目录」那一项要不要画**：**搜索态 + 正文档档**。
 *
 * <p>⚠️ **只在正文档档有**（用户 2026-11-05：「在搜索正文场景如果点击到了某个一级目录
 * 如果还想开整体就退回不了了 在左侧边栏加一个全部目录吧」）：
 * 目录档的结果是"一棵目录树"，树总得有一本书做根 ⇒ "全部"在那儿没有对应的形态。
 *
 * <p>⚠️ **没有搜索词时也不画**：那时候点侧边栏的书是"进那本书"，
 * "全部知识点"已经有入口了（品牌区那个首页）。
 */
const showAllItem = computed(() => searching.value && !isCatMode(searchMode.value))

/**
 * 点「全部目录」= **回到所有符合的卡片**（清掉目录范围 `?tree=` / `?category=`）。
 *
 * <p>⚠️ **必须把 `?category=` 也清掉**：它是"正在看的那个目录"，
 * 留着的话点这一项只会退回那一个目录的卡片，而不是整体。
 */
const allLink = computed(() => ({
  path: '/app/learn',
  query: { q: keyword.value || undefined }
}))

/**
 * 命中的**总条数**（= 侧边栏这几本各自命中数之和）。
 *
 * <p>每条命中只会被算一次（计数沿祖先链累加，一级书是链顶）⇒ 求和就是总数。
 */
const allHitTotal = computed(() =>
  visibleCats.value.reduce((n, c) => n + (categoryCounts.value[c.id] || 0), 0)
)

/**
 * **这一档的范围（哪本书）必须在这一档有命中**。
 *
 * <p>⚠️ **为什么**：两档的命中集不同（正文命中 ⇄ 标题命中），同一本书在两档里
 * 可能有命中、也可能一条都没有 ⇒ 切档后挂着上一档选的那本 ⇒ 右边是一棵没有命中的树
 * （正文档则是空卡片区）—— **界面上看不出异常，只会觉得"搜不出东西"**。
 *
 * <p>⇒ 两条处置：
 * - **目录档**：树得有本书 ⇒ 换成**这一档有命中的第一本**；
 * - **正文档**：没选书就是"所有符合的卡片"（用户原话）⇒ **取消选中**。
 *
 * <p>⚠️ **判据就是 `visibleCats`**（这一档有命中的一级书）—— 侧边栏列的就是它，
 * 各算一遍的话侧边栏列一本、树却长在另一本上，而界面上看不出哪儿不对。
 *
 * <p>⚠️ **它跑在"新一档的索引到了之后"**：切档那一刻手上还是上一档的清单 ⇒
 * 按旧清单选书就会选出一本在这一档一条都没有的（实测：正文档的「语言」在
 * 目录档一条标题都没有 ⇒ 右边挂着一棵全是「0」的树）。
 */
watch([searchMode, visibleCats], () => {
  if (!searching.value) return
  const books = visibleCats.value
  const cur = route.query.tree
  // 当前那本在这一档有命中 ⇒ 不动它（用户自己选的书优先）。
  if (cur != null && books.some((b) => b.id === Number(cur))) return
  if (isCatMode(searchMode.value)) {
    if (!books.length) return
    router.replace({ path: '/app/learn', query: { ...route.query, tree: books[0].id } })
  } else if (cur != null) {
    // 正文档：这本书在这一档没有命中 ⇒ 取消选中（回到"所有符合的卡片"）。
    router.replace({ path: '/app/learn', query: { ...route.query, tree: undefined } })
  }
})

/**
 * 当前所在的那本书（= 当前目录的最顶层祖先）。
 *
 * <p>侧边栏只放一级目录，而两种形态都能一路走进三层（书 → 章 → 小节）⇒
 * 判据不能是"当前目录 === 这一行"（进到小节时侧边栏就一行都不亮了，
 * 用户会以为位置丢了）。**认祖先链上的那本书**才对。
 *
 * <p>⚠️ **两个形态的 id 要合并着看**：树视图的书在 `?tree=` 上，
 * 卡片视图的目录在 `?category=` 上 —— 两者都可能是"当前所在的那本书"的线索。
 */
const currentBookId = computed(() => {
  const t = route.query.tree
  const id = t != null ? Number(t) : Number(route.query.category)
  if (!id) return null
  const chain = getCategoryPath(id)
  return chain.length ? chain[0].id : id
})

/** 侧边栏这一行是不是"当前所在的那本书"。 */
const isCurrentCategory = (id) => currentBookId.value === id

// 有管理后台菜单的（超管）才给"管理后台"入口。判据与 Layout 的 hasAppShell 共用 hasAdminMenus
// —— 原来这里自己写了一遍 `menuPaths.some(p => !p.startsWith('/app'))`，和 session.js 里的
// hasAdminMenus() 一字不差；Layout 那侧早就调共享函数了，只剩这侧是副本。
const hasAdminShell = computed(() => hasAdminMenus())

const currentUser = sessionProfile

/**
 * 业务数据（知识点 / 目录 / 类型 / AI 配置）拉失败。
 *
 * **和会话的失败分开记**：会话失败 → 用户名都不知道（sessionState 管）；
 * 业务数据失败 → 用户名好好地在那儿，只是侧边栏的目录树是空的。
 * 合成一个状态的话，业务数据一失败连用户名都跟着说"加载失败"——那是把好的说成坏的。
 */
const shellDataFailed = ref(false)

async function loadShell() {
  shellDataFailed.value = false
  // 会话（导航 / 用户名）**先到先显示** —— 它是壳的**第一帧**。
  //
  // 原来是从 loadShellData() 拿的，而那个函数把会话和知识点/目录/类型/AI 配置
  // 绑在**同一个 Promise.all** 上，于是"导航和用户名要等最慢的那个业务接口"：
  // 登录进来底下写着「未登录」、侧边栏空着好几秒。
  //
  // 而**数据其实早就在手**了 —— 路由守卫 await 过 bootstrapSession，这里命中
  // 快照，不会再打接口。守卫等的是"能不能进这个路由"，壳等的是"业务数据齐了没"，
  // 两件事被绑成一件，壳就得等最慢的那个。
  //
  // 失败不用这里自己记会话那份：**sessionState**（utils/session.js，两个壳共用）已经记了 ——
  // 侧边栏那一格就是看它决定显示「加载失败 + 重试」还是用户菜单。
  try {
    // **会话结果这一份不再存进本地 ref**：导航块撤掉之后，侧边栏没有任何地方
    // 读菜单树 / 菜单路径了（`session` 那个 ref 只剩写、没有读，是块死状态）。
    // 但 bootstrapSession() 本身**必须调** —— 它顺手把菜单路径和权限写进
    // localStorage（hasAdminMenus / hasAppMenus 从那儿读）。
    await bootstrapSession()
  } catch {
    return
  }
  // 业务数据在后面拉，不挡壳。store 的三个缓存都是 ref、组件是 computed，到位就自己刷新。
  try {
    await loadShellData(keywordFromRoute(route), searchScopeOf(searchMode.value))
  } catch {
    // 提示归 request.js（唯一提示出口），这里不重复弹；
    // 目录区自己有「加载失败」状态（见 store 的 categoriesState）。
    shellDataFailed.value = true
  }
  // 目录掌握度聚合自成一路（侧边栏/树角标共用一次拉全树）：失败不拖累壳——
  // 角标空轨道就是中性空态。错误提示由 request 层统一出。
  ensureCategoryMasteryLoaded().catch(() => {})
}

/**
 * 顶栏那条的**当前目录**（null = 没有目录上下文，顶栏显示路由标题）。
 *
 * <p>2026-11-08 起顶栏显示的是**完整路径**（一级目录/二级目录/…，面包屑从内容区移上来，
 * 用户：「把下方那个 一级目录 -> 二级目录 的那个索引移动到上面去 就在这个一级目录名字
 * 之后」）⇒ 不再"沿父链走到顶层只报书名"—— 书名就是路径的第一段，后面几级接着写。
 *
 * <p>详情页的目录归属是**详情页的局部状态**（topic 是它自己的 ref），壳拿不到
 * ⇒ 由详情页报上来（store 的 `openTopicCategoryId`）。
 */
const topCategoryId = computed(() => {
  if (route.name === 'AppTopicDetail') return openTopicCategoryId.value
  const raw = route.query.category ?? route.query.tree
  const id = raw != null && raw !== 'UNCATEGORIZED' ? Number(raw) : NaN
  return Number.isFinite(id) && id ? id : null
})

/**
 * 没有目录上下文时顶栏显示什么（首页 / 知识点 / 考试 …）。
 *
 * <p>**详情页例外**：这条知识没有目录归属（未归类）就什么都不显示 ——
 * 位置信息没有就是没有，"知识点"三个字回答不了"我在哪"
 * （原来顶栏那句「知识点」正是用户点名要改掉的）。
 */
const topTitle = computed(() =>
  route.name === 'AppTopicDetail' ? '' : (route.meta.title || '知识库')
)

/**
 * 顶栏宽度分配：**搜索框优先**（2026-11-09 用户：「改一下优先级 搜索框优先级比较高
 * 优先压缩目录索引文字…当目录索引文字压缩到最小的时候再压缩搜索框的长度」）。
 *
 * <p>三段降级链（**从先让位到后让位**）：
 * ① 目录索引**完整**放得下 → 用完整链；
 * ② 放不下 → 目录索引**收掉中间层级**（显示「一级目录 / … / 当前目录」）；
 * ③ 还放不下 → 收窄**搜索框**（下限 SEARCH_W_MIN），目录索引保持收拢态。
 *
 * <p>⚠️ **为什么不能靠 CSS 自动做**：②要"中间收成…"这种形态切换，
 * 而 CSS 只会把整条链从尾巴截断 —— 截尾丢的正是"我在哪一级"（当前目录在末尾）。
 * ⇒ 形态由这里定，宽度由这里算，面包屑只负责报两种形态各有多宽。
 *
 * <p>⚠️ **宽度是量出来的，不是猜的**：固定件（折叠按钮 / 齿轮按钮 / 三处间距）逐个量，
 * 剩下的空间才是「目录索引 + 搜索框」分的。
 */
const SEARCH_W_MAX = 320
const SEARCH_W_MIN = 160
const crumbMode = ref('full')
/** 面包屑两种形态的自然宽度（由 CategoryCrumbs 量了报上来）
 *  tail = 尾部宽度（收拢标记 + 当前目录）= 目录索引的"最小可读宽度" */
const crumbSizes = ref({ full: 0, collapsed: 0, tail: 0 })
const searchWidth = ref(SEARCH_W_MAX)
/** 分给目录索引的宽度（px）—— 算出来的，不是 flex 猜的 */
const crumbWidth = ref(0)

const topBarEl = ref(null)
const crumbBoxEl = ref(null)
let topBarRO = null

/** 目录索引两种形态各有多宽（由子组件量，量的是**自然宽度**，不受当前形态影响） */
function onCrumbMeasure(sizes) {
  crumbSizes.value = sizes
  applyTopBarLayout()
}

/**
 * 按当前可用宽度重排顶栏。
 *
 * <p>可用空间 = 顶栏内容区 − 固定件（折叠按钮、齿轮按钮、三处间距），
 * 这份额外空间由「目录索引 + 搜索框」分。
 */
function applyTopBarLayout() {
  const bar = topBarEl.value
  const box = crumbBoxEl.value
  if (!bar || !box) return

  // 没有目录上下文 ⇒ 顶栏那行是路由标题（短），不参与分配
  const sizes = crumbSizes.value
  if (!sizes.full || topCategoryId.value == null) {
    crumbMode.value = 'full'
    searchWidth.value = SEARCH_W_MAX
    crumbWidth.value = 0
    return
  }

  const left = box.parentElement
  const actions = bar.querySelector('.app-top__actions')
  if (!left || !actions) return

  const px = (v) => parseFloat(v) || 0
  const cs = getComputedStyle(bar)
  // 顶栏内容区宽度（clientWidth 含 padding、不含 border ⇒ 减掉左右 padding）
  const inner = bar.clientWidth - px(cs.paddingLeft) - px(cs.paddingRight)

  // 固定件：折叠按钮 / 齿轮按钮 / 三处间距
  // ⚠️ 齿轮按钮没有时，它左侧那处间距也不存在（gap 只在两项之间）⇒ 不能白算一个
  const gear = actions.querySelector('.app-top__icon-btn')
  const fixed =
    (left.querySelector('.app-top__icon-btn')?.getBoundingClientRect().width || 0) +
    (gear?.getBoundingClientRect().width || 0) +
    px(cs.columnGap) +                                   // 左侧组 ↔ 右侧组
    px(getComputedStyle(left).columnGap) +               // 折叠按钮 ↔ 目录索引
    (gear ? px(getComputedStyle(actions).columnGap) : 0)  // 搜索框 ↔ 齿轮按钮

  // 目录索引 + 搜索框 分剩下的空间
  const shared = inner - fixed
  const avail = (searchW) => shared - searchW

  let mode = 'full'
  let searchW = SEARCH_W_MAX

  if (sizes.full > avail(SEARCH_W_MAX)) {
    // ② 完整链放不下 ⇒ 收掉中间层级
    mode = 'collapsed'
    if (sizes.collapsed > avail(SEARCH_W_MAX)) {
      // ③ 收拢后还放不下 ⇒ 这才轮到搜索框让位。
      //    ⚠️ **收拢链的宽度就是"目录索引的最小"**（2026-11-09 用户：
      //    「优先压缩目录索引文字…当目录索引文字压缩到最小的时候再压缩搜索框的长度」）：
      //    收拢态 = 首级目录 + 「…」 + 当前目录 —— 中间已经全省了，
      //    再压就要丢掉"我在哪一级"（当前目录）⇒ 那不是更小，是信息丢失。
      //    ⇒ 搜索框收到**刚好装下收拢链**，目录索引也保底这么多。
      //    ⚠️ 搜索框自己的下限 SEARCH_W_MIN 仍然管着：真不够时先保搜索框
      //    （它优先级最高），目录索引那部分由 CSS 兜底裁首级目录。
      searchW = Math.max(SEARCH_W_MIN, Math.min(SEARCH_W_MAX, shared - sizes.collapsed))
    }
  }

  crumbMode.value = mode
  searchWidth.value = searchW
  // 分给目录索引的宽度：优先给满（内容不宽于它就只占内容宽，多余的留白归 flex）
  // ⚠️ **下限 = 尾部宽度**（2026-11-09）：CSS 已让首级目录可被裁（flex-shrink）、
  // 当前目录那截固定 ⇒ 盒子**不需要**装下整条链，但**必须装得下尾部**
  // （收拢标记 + 当前目录），否则"我在哪一级"会被 nav 的 overflow:hidden 裁掉
  // （实测 curInBox=false：字被裁在盒子外看不见）。
  const tailMin = mode === 'collapsed' ? (sizes.tail || 0) : 0
  crumbWidth.value = Math.max(0, avail(searchW))
  if (tailMin && crumbWidth.value < tailMin) {
    // 盒子装不下尾部 ⇒ 搜索框再让一点（用户明确：搜索框优先级高，但有下限）
    const need = tailMin - crumbWidth.value
    const give = Math.min(need, searchW - SEARCH_W_MIN)
    if (give > 0) {
      searchWidth.value = searchW - give
      crumbWidth.value += give
    }
  }
}

/**
 * 顶栏宽度一变就要重排（窗口缩放、侧栏收起/展开都会改顶栏宽度）。
 *
 * <p>用 ResizeObserver 而不是 window.resize：侧栏折叠不触发 window.resize，
 * 但顶栏宽度确实变了。
 */
function watchTopBarWidth() {
  if (typeof ResizeObserver === 'undefined') return
  topBarRO?.disconnect()
  topBarRO = new ResizeObserver(() => applyTopBarLayout())
  if (topBarEl.value) topBarRO.observe(topBarEl.value)
}

/**
 * 点路径里的某一级 = 去那一级目录。
 *
 * <p>两种去法沿用原面包屑的语义：**列表页 replace**（换个目录继续筛 —— 保留搜索/时间/
 * 类型，清掉 `tree`：面包屑可以跨书，留着会让"哪本书"和"哪个目录"对不上，侧边栏高亮
 * 的是 `tree` 那本）；**详情页 push** 到那一级的卡片视图（返回键能回到这条知识）。
 */
function pickCrumb(id) {
  if (route.name === 'AppLearn') {
    router.replace({
      path: route.path,
      query: { ...route.query, tree: undefined, category: id }
    })
    return
  }
  // ⚠️ **详情页点面包屑 = 切到那个目录看内容**，`categoryLink` 只带 `category` ⇒
  // 会把搜索词扔掉（症状同 {@link bookLink}："切换目录后上方的搜索条件掉了"）。
  // 2026-11-11 用户拍板「搜索词一律跟着走」⇒ 有词就一并带上；没词 URL 保持干净。
  const link = categoryLink(id)
  const kw = keyword.value.trim()
  if (kw) link.query = { ...link.query, q: kw }
  router.push(link)
}

// 沉浸模式：把侧边栏和顶栏收起来，只留内容。判据只有这一个函数，
// AppLayout、详情页、首页都从这儿取（见 useImmersive.js）。
const immersive = useImmersive()

// 搜索框跟着 URL 走（单向：URL → 框）。少了这条，**直接打开一个带 `?q=` 的分享链接**
// 会看到列表已经筛过了、搜索框却空空如也 —— 又一处"两边各存一份"。
//
// ⚠️ **只在初始化取一次，不做持续回读**：边打边搜每敲一个字 replace 一次 URL，
// 持续回读就会在用户正敲下一个字的时候把刚敲的抹掉（实测：打 "K8s" 打完框是空的）。
// 写方只有这个框一个，URL 上的 `q` 除了它没人改，所以"取一次"就够了。
// immediate 必须开：**首屏**这一次才是最要紧的那次（带着 ?q= 进来的），
// watch 默认只在"之后变了"才跑，刷新页面它一次都不会跑。
// Esc 退出沉浸模式。挂在 window 上而不是页面上：全屏时壳已经没了，
// 页面上只剩内容，没有一个可以承载键盘事件的固定按钮区。
function onKeydown(e) {
  // immersive 是 reactive 包着的，.on 在脚本里也是布尔值，不要再写 .value
  if (e.key === 'Escape' && immersive.on) immersive.exit()
}
onMounted(() => window.addEventListener('keydown', onKeydown))

// ==================== 详情页 chunk 空闲预取（2026-11-09 用户：「从目录树点进知识点详情
// 的时候还是会很慢特别是前几次 多点几次就快了」） ====================
//
// 「前几次慢、热了就快」= 第一次点要下载**懒加载路由 chunk**（AppTopicDetailView
// 连着 Milkdown 编辑器那一坨 ~1MB），热了之后走 HTTP 缓存才快 ⇒ 空闲时先拉好，
// 首次点击就是热的。考试页同理（也是懒加载路由）。
onMounted(() => {
  const warm = () => {
    import('../../views/corecraft-web/AppTopicDetailView.vue').catch(() => {})
    import('../../views/corecraft-web/AppExamView.vue').catch(() => {})
  }
  if (typeof requestIdleCallback === 'function') requestIdleCallback(warm, { timeout: 4000 })
  else setTimeout(warm, 1500)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// 搜索：**回车才生效**（2026-11-05 晚用户拍板）。
// ⚠️ **原来这里是"边打边搜"**（watch 每次敲字都打一次服务端）—— 内容搜索要读正文
// （实测读全文 4416ms、不读 78ms）⇒ 逐字搜必然卡。⇒ 只留回车。
// 写 `?q=` 时**保留已有的其它 query**（`category` 等）：原来是 `push({ query: { q: kw } })`
// —— 只带 q，别的全丢：在「某个目录」下搜一句，跳过去目录筛选就没了，等于"搜索顺手把当前范围清了"。
// 用 push 而不是 replace：回车一次 = 一条历史，一次搜索一条，浏览器后退正好退回上一次。
/** 现在是不是知识点详情页 —— **顶栏搜索框的语义由它定**（本文实时检索 ⇄ 整库搜索）。 */
const isDetail = computed(() => route.name === 'AppTopicDetail')

/**
 * 搜索框现在有没有焦点。
 *
 * <p>⚠️ **只用来判断"该不该自己画清除按钮"**：EP 的 `clearable` 是**悬停/聚焦才渲染**
 * ⇒ 清除按钮会凭空冒出来把 ↑↓ 挤走（用户报「点箭头后词直接不见了」的根因）。
 * ⇒ 改成自己画一个**位置固定**的 ×（见模板那段注释）。
 */
const searchFocused = ref(false)

/**
 * **清空搜索**（自己画的那个 × 按钮）。
 *
 * <p>⚠️ **必须同时清三处**：框里的字（`searchKeyword`）、store 通道（正文那档的高亮）、
 * URL 的 `?q=`。清完 ⇒ 高亮消失、刷新不复活。
 *
 * <p>⚠️ **命中计数也要显式清**（2026-11-10 用户：「我手动清空这里也该一样啊」）：
 * 清词会触发详情页的 `watch(bodySearchKeyword)` ⇒ 由它去清计数 —— 但那**依赖详情页活着**
 * （列表页上没有那个 watch）⇒ 计数可能留在上一条的值上。
 * ⇒ **这里显式清一次**（计数在 store 里，清它是安全的：没词就不该有命中）。
 */
function clearSearch() {
  searchKeyword.value = ''
  bodySearchKeyword.value = ''
  bodySearchHits.value = { total: 0, index: 0 }
  // URL 上的 q 也去掉（列表页时同样清干净）
  if (keywordFromRoute(route)) {
    router.replace({ query: { ...route.query, q: undefined } })
  }
}

/**
 * 顶栏 ↑↓ 按钮 = 下达跳转命令（详情页 watch {@link bodySearchJump} 去调编辑器
 * `gotoMatch`）。经函数写而不是模板里直接赋值：模板对 import 的 ref 赋值不可靠。
 */
function jumpHits(dir) {
  bodySearchJump.value = dir
}

/**
 * 顶栏 ↑↓ 按钮 = 下达**目录树那档**的跳转命令（`CategoryTree` watch {@link treeSearchJump}）。
 * 同上：经函数写，不在模板里直接赋值。
 */
function jumpTreeHits(dir) {
  treeSearchJump.value = dir
}

/**
 * **回车 = 整库搜索**（列表页与详情页**完全一样**）。
 *
 * <p>⚠️ **详情页回车也走整库搜索**（2026-11-09 用户原话：
 * 「在正文内不点回车直接展示跳转没问题 但是如果按了回车那就还是要跟在外面一样
 * 变成搜索正文卡片或者目录」）：
 * - **不点回车** = 详情页顶栏是「本文实时检索」⇒ 边打字边在正文里高亮，
 *   ↑↓ 跳本文命中（**不离开这一页**）；
 * - **点了回车** = 那是**另一个动作**（去全库里搜这个词）⇒ 跳列表页，
 *   结果按当前档给**卡片**（正文档）或**目录树**（目录档）。
 *
 * <p>原来这里在详情页写的是 `bodySearchJump = 'next'`（回车 = 跳下一处命中）⇒
 * 用户在详情页按回车永远去不了全库搜索，**两个动作被塞进了同一个键**。
 * ⇒ 现在：**回车 = 整库搜索**（两个页面一致）；跳下一处命中交给**顶栏的 ↑↓ 按钮**。
 */
function goSearch() {
  goSearchWith(searchKeyword.value)
}

/**
 * **在详情页时，顶栏搜索框变成「本文实时检索」**（2026-11-09 用户：
 * 「如果我再详情正文中的视图的时候 就变为实时生效查询 也是弹出那个检索定位框」）。
 *
 * <p>列表页仍然是**回车才搜**（整库检索要读正文，逐字搜必然卡 —— 见上面那段注释）。
 * 详情页搜的是**当前这一篇**（内容已经在浏览器里了）⇒ 边打字边搜没有代价。
 *
 * <p>⚠️ **打字只做"本文内定位"，一个接口都不发**（2026-11-11 用户：
 * 「当在详情中直接输入搜索词的时候是直接在文中定位不做搜索的 但是我看侧边栏却会变化
 * 应该是做了搜索 这个不对 只有当按下回车才能进行搜索 逻辑跟在外面一样 没按回车之前
 * 不调用任何接口」）。
 *
 * <p>⚠️ **原来的写法正好踩了这个坑**：详情页每敲一个字就 `router.replace` 把词写进
 * URL 的 `?q=`（当年为了"刷新不丢词"）⇒ `keyword` 一变，壳与列表页那两条
 * `watch([keyword, searchMode])` 就去 `ensureTopicIndexLoaded` 重拉**整库索引**
 * ⇒ 侧边栏的书单和条数立刻按这个词筛了一遍（用户看到的"侧边栏变了"就是它），
 * 而正文里的定位只是本地 `searchInBody`（这才是他想要的）。
 *
 * <p>⇒ **URL 只在"把词删光"时动一次**（那是**取消**那次搜索：侧边栏/树要还原，
 * 得重新拿一份无词的全集），**有字时一律不写**。刷新不再保留半截词 —— 与列表页
 * 口径一致（外面也是回车才进 URL）。
 *
 * <p>⚠️ 别把这条和 {@link bodySearchKeyword} 通道混起来：那条是**进程内**的
 * "本文高亮用哪个词"，不联网，可以逐字同步。
 */
watch(searchKeyword, (kw) => {
  if (!isDetail.value) return
  // ① 本文内定位（本地检索，不发请求）
  bodySearchKeyword.value = kw
  // ② 只有"删光"才回写 URL（= 取消搜索）；有字时不写 ⇒ 不触发索引重拉
  const next = kw.trim()
  if (!next && keywordFromRoute(route).trim()) {
    router.replace({ query: { ...route.query, q: undefined } })
  }
})

/**
 * URL 上的搜索词变了 ⇒ **框里的字要跟上**。
 *
 * <p>⚠️ **只在"这个值真的变了"时才同步**（watch 一个 getter，不是 watch 整个 route）：
 * 用户正在打字、而 URL 没动的时候不会被抹掉（那正是原来"边打边搜"的老毛病）。
 * ⚠️ **为什么现在需要它**：改成"回车才搜"之后，浏览器**后退**时 URL 上的 `q` 没了，
 * 但框里的字还留着 ⇒ 框和实际搜的东西对不上。
 *
 * <p>⚠️ **自己写的 URL 同步不回灌到框里**（2026-11-09 修「打字打到一半被抹掉」）：
 * 详情页打字时会把词 `router.replace` 进 URL ⇒ 这条 watch 就会把**同一个词**再写回框里。
 * 看起来值一样、无害，但 `replace` 是异步的 ⇒ 写回发生在用户**已经又敲了几个字之后**
 * ⇒ 正在打的字被旧的词覆盖（症状：打字时框里的字会突然跳回去）。
 * ⇒ **框正在被编辑时不回填**（`searchFocused` 为真）—— 那是用户自己的输入，别碰。
 */
watch(() => keywordFromRoute(route), (kw) => {
  // 用户正在框里打字 ⇒ 不回填（那是他自己的输入，不是 URL 推过来的）
  if (searchFocused.value) return
  searchKeyword.value = kw
})

/**
 * **换了一条知识点 ⇒ 检索上下文整个重置**（2026-11-10 用户报
 * 「我重新进另一个知识点 命名搜索内容都清空了 这里还是显示中了18处 这不扯淡么」）。
 *
 * <p>⚠️ **根因（demo harness 插桩实测，不是读代码猜的）**：
 * 换知识点时 URL 上的 `?q=` 没了（列表页的 `searchContext` 只在**有词**时带 `q`，
 * 而详情页之间的切换是**直接换 id**、压根不带 `q`）⇒ 上面那条 watch 收到空词
 * ⇒ 但它**在 `searchFocused` 为真时早退** ⇒ **框里的字没被清** ⇒
 * `searchKeyword` 没变 ⇒ 详情页的 `watch(bodySearchKeyword)` 不触发
 * ⇒ `applyBodySearch` 从不收到空词 ⇒ **`bodySearchHits` 停在上一条的值**
 * （实测「28 处」就这么留下的，而正文里高亮数其实是 0 —— **计数与实际脱节**）。
 *
 * <p>⇒ 修法不是"让 watch 强行回填"（那会抹掉用户正在打的字），
 * 而是**把"换知识点"这件事本身认出来**：它是**检索上下文的切换**，
 * 与"用户正在打字"是两回事 ⇒ 在这条路径上**强制清空**，
 * 不看焦点（焦点状态是"用户的手指"，而换知识点是"用户点了另一条"）。
 *
 * <p>⚠️ **判据用 `route.params.id` 变化**，不是"URL 上没词了"：
 * 后者在浏览器**后退**时也会命中（后退到没 `?q=` 的地址）⇒ 那时反而**不该**清
 * （用户可能只是回到没搜索的状态，而不是"换了另一条"）。
 */
watch(() => (isDetail.value ? route.params.id : null), (id, oldId) => {
  // 不是详情页之间的切换（进详情页 / 离开详情页）⇒ 不管
  if (id == null || oldId == null || id === oldId) return
  // 换了另一条知识点 ⇒ 检索词、命中计数、正文高亮**一起归零**
  // （计数在 store 里、正文高亮在编辑器插件里，两处都要清 —— 见下面两行）。
  searchKeyword.value = ''
  bodySearchKeyword.value = ''
  bodySearchHits.value = { total: 0, index: 0 }
})

/**
 * 搜索词 / 搜索档变了 ⇒ **结构索引要跟着重拉**。
 *
 * <p>索引是"当前词 + **当前档**下的全集"（服务端筛的）⇒ 档一变（正文 ⇄ 目录），
 * 那批命中集整个换掉 ⇒ 不重拉的话，侧边栏的计数和树上的标记都还是上一档的。
 *
 * <p>（学习页也有一份同样的 watch —— 两边调的是**同一个** store 函数，
 * 它认得"这份就是当前的"，不会打两遍接口。）
 */
watch([keyword, searchMode], ([kw, mode], [oldKw, oldMode]) => {
  // ⚠️ **没词时切档 ⇒ 不重拉**（2026-11-11 用户：「没词的时候就不用做任何操作了」）：
  // 索引是"**当前词**下的全集"，没词时它与档无关（档只决定服务端拿哪块字段去匹配）
  // ⇒ 这时候重拉一遍索引是纯浪费，而用户看到的只是"档位换了"。
  const k = String(kw || '').trim()
  if (!k && mode !== oldMode) return
  ensureTopicIndexLoaded(false, kw, searchScopeOf(mode))
})

/**
 * 回车 = 整库搜索 ⇒ **跳列表页**（`/app/learn`），结果按当前档给卡片 / 目录树。
 *
 * <p>⚠️ **`same` 守卫要连路径一起比**（2026-11-09 用户报「按了回车还是留在详情页」）：
 * 详情页的 URL 上**本来就带着 `?q=`**（从列表页点卡片带过来的那个词）⇒
 * 只比词的话 `same` 恒为 true ⇒ 直接 return ⇒ **按回车什么都不发生**。
 * 而"已经在搜同一个词"这件事，判据应该是**同一个页面上的同一批结果**——
 * 详情页和列表页是两屏结果（本文命中 ⇄ 全库命中）⇒ 路径不同就得跳。
 */
function goSearchWith(raw) {
  const kw = String(raw || '').trim()
  // ⚠️ **档位在这一刻才进 URL**（切档只改 `pendingMode`，见 `toggleSearchMode`）：
  // 回车 = 一次"搜索提交"，词和档一起落地址 ⇒ 结果形态与按钮上写的字永远一致。
  const query = {
    ...route.query,
    q: kw || undefined,
    mode: isCatMode(pendingMode.value) ? SEARCH_MODE_CAT : undefined
  }
  // ⚠️ **`same` 要连"档"一起比**：在列表页搜着同一个词、只是切了档
  // （卡片 ⇄ 目录树）也是一次**新搜索** —— 只比词的话这里直接 return，
  // 用户按了回车却什么都没发生（和当年"详情页回车没反应"是同一个坑）。
  const same = keywordFromRoute(route) === kw
    && route.path === '/app/learn'
    && searchModeFromRoute(route) === pendingMode.value
  if (same) return
  router.push({ path: '/app/learn', query })
}

function goAdmin() {
  router.push('/')
}

// ⚠️ **侧边栏展开子树的弹层整套删了**（2026-11-03）：cascadeFor / cascadeTop /
// toggleCascade / closeCascade / goCategory / goTopic 那一族，
// 连同模板里的遮罩 + <CategoryTree mode="nav">。
// 展开子树现在由**目录页**做（主区那棵树）：它更宽、能分层、知识点能直接点。
// ⇒ 侧边栏这列退回纯导航（书名 + 条数），目录的其它操作也一起搬到了目录页。

/**
 * 新建一级目录（侧边栏过滤框里那个「＋」）。
 *
 * <p>⚠️ **只有一级这一种**：新建子目录 / 改名都在**目录页**里（看着那个目录时操作它）。
 */
function openCreate() {
  nameDialog.visible = true
}

/**
 * 打开生成主题弹窗（主题 → 规划 → 提交后台写正文）。
 *
 * <p>生成的目录**自成一体、放在第一层**：挂到哪、怎么挪层级，用户自己在侧边栏拖。
 */
function openTreeDialog() {
  treeDialog.planTask = null
  treeDialog.planOutline = ''
  treeDialog.visible = true
}

/**
 * 任务中心「继续编辑」：把已经生成好的树交给弹窗（**直接进第 2 步**）。
 *
 * <p>为什么从任务卡走这条路：规划开思考要 3~5 分钟，放后台跑完再回来接着改，
 * 比在弹窗里干等（还可能超时）合理。树已经存在库里，这里只是"换个人接手"。
 */
function openPlanTask({ task, outline }) {
  treeDialog.planTask = task
  treeDialog.planOutline = outline
  treeDialog.visible = true
}

/**
 * 出题任务的「作答」：跳去对应页面（去哪由类型注册表说——随堂测验回这条知识的
 * 详情页、章节考试回考试页，路径参数从任务 params 取；组件零分支）。
 */
function openAnswerTask(task) {
  const path = taskType(task?.type)?.answerPath?.(task)
  if (path) router.push(path)
}

/**
 * 弹窗建完目录并提交了写正文任务：刷目录树 / 知识点列表。
 *
 * ⚠️ **任务列表不在这里刷**（2026-11-12）：弹窗自己走统一出口
 * （`genTaskCreated`）—— 那里会等在途那一拍回来再发一次新的，
 * 而这里调的 `refreshGenTasks()` 有"在途去重"，可能被吞掉
 * ⇒ 任务卡不立刻出现（用户原话「创建主题后任务列表不能马上看到任务」）。
 */
async function onTreeDialogDone() {
  await refreshTopicIndexes()
  bumpDataVersion()
}

/**
 * ⚠️ **这里原来还挂着一份「AI 配置作废」**（退出登录 / 改完密码时清）。
 * 现在归 `useAi.js` 自己订阅 `onAuthChange` 管了：AI 配置是按用户的，
 * 登录态一变就得清，而"登录态变了"这个信号只有一个出处。
 * 挂在这儿只有登出那一条路，**重新登录就漏了** ——
 * 症状是"登进来 AI 开关是关的，刷新一下才对"。
 */

// 落点全站只有**目录行**一处（目录页那棵树 + 侧边栏那一列），它们都渲染
// data-cat-id 标记，命中由 store 的 hitCategory() 用 elementFromPoint 算。
// 侧边栏、药丸都不再当落点：侧边栏的目录项是导航，「待归类」药丸是纯筛选。
// ⚠️ **知识点拖拽归类那套 2026-11-05 删了** ⇒ 也不再需要"拖拽时浮出一棵落点树"。

onMounted(async () => {
  // **任务角标**：30 秒拉一次（抽屉打开时面板会切到 3 秒）。
  // 这一份同时给「任务」图标上的数字用，所以放在壳上，不放在抽屉里。
  // ⚠️ 首拉延迟 1.5s（2026-11-16）：壳启动那一下已有 6~8 个并发请求，HTTP/1.1
  // 六连接全占满，角标首拉会排到队尾、把列表/统计的第一拍顶后一位；角标只在
  // 「待确认/失败」时才亮，晚一拍半无感知。30 秒内建任务走 genTaskCreated()
  // 统一出口立刻刷，不依赖这一拍。
  setTimeout(() => startGenTaskPolling(POLL_SLOW), 1500)
  // **首次点一级目录**要等 AppLearnView 的 45KB chunk 下完（冷缓存实测 1.86s）。
  // 浏览器空闲时先把这个 chunk 取回来（只下载、不渲染组件），路由真跳转时命中
  // 模块缓存 ⇒ 切换只剩渲染。⚠️ 不 await、也不放进 loadShell()：预取是顺手做的，
  // 失败或不支持 requestIdleCallback 都不能挡住壳。
  const prefetchLearn = () => import('../../views/corecraft-web/AppLearnView.vue').catch(() => {})
  if ('requestIdleCallback' in window) requestIdleCallback(prefetchLearn, { timeout: 2000 })
  else setTimeout(prefetchLearn, 500)
  // 侧边栏偏好（宽度 / 密度）写到 <html> 行内；teleport 到 body 的浮层也继承得到。
  // 读不到偏好就什么都不写，base.css 的 :root 说了算。
  // 卸载时 clearUiPrefs 摘掉——admin 壳压根没有侧边栏，留着是死重量。
  applyUiPrefs()
  // 顶栏宽度分配：观察顶栏尺寸变化（缩窗口 / 收侧栏都会改它）⇒ 重排。
  watchTopBarWidth()
  // 菜单/资料和业务数据并行拉：菜单决定左侧导航，数据决定目录树。
  // 三个 ensure 收敛在 loadShellData 一处（原来这里和 AppHomeView 各写了一遍）。
  await loadShell()
})

onBeforeUnmount(() => {
  clearUiPrefs()
  topBarRO?.disconnect()
  topBarRO = null
})
</script>

<style scoped>
/* 拖拽幽灵：跟着指针的一行标题。
   pointer-events: none 是硬要求——它压在指针正下方，命中检测靠 elementFromPoint，
   被它挡住就永远命不中任何目录行。 */
.dnd-ghost {
  position: fixed;
  z-index: 60;
  pointer-events: none;
  /* 往下偏一点，别正压在指针下的那一行上——正压着的话"正在放哪"和"这是哪一行"
     两个字面糊在一起（2026-10-07 实机截图就是这个样子）。
     pointer-events: none 保证它遮不住命中检测，所以这里只管好不好看。 */
  transform: translate(10px, 14px) rotate(2deg);
  max-width: 240px;
  padding: var(--sp-8) var(--sp-12);
  border-radius: var(--radius);
  border: 1px solid var(--cc-primary);
  background: var(--cc-surface);
  box-shadow: 0 8px 24px rgb(31 35 41 / 18%);
  font-size: var(--text-base);
  line-height: 1.5;
  color: var(--cc-text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.95;
}
/* "点外面关掉"的透明遮罩：**没有了**。
   它是触屏「点选归类」的配套（点弹层外面 = 取消），那套 2026-11-05 删了。
   ⚠️ **原来还有第二处 `.cascade-scrim`**（盖住目录树弹层），随弹层一起删了
   （2026-11-03）⇒ 现在整个 AppLayout 里一处遮罩都不剩。 */
.app-shell {
  display: flex;
  /* 不是 100vh：body 上有 zoom(--cc-scale)，视口单位会被重复放大，
     壳就会被撑到 1.5 倍视口高、整页开始滚。百分比在缩放坐标系里解析，天然正确。 */
  height: 100%;
  background: var(--cc-bg);
  overflow: hidden;
}

/* ---------- 左侧栏 ---------- */
.app-side {
  width: var(--w-side);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  /* 主题化（2026-11-08）：侧栏底色/分隔线走主题 token（本色=原值，素雅=暖纸、量子=冷白）。
     --cc-side-bg 带 fallback：主题层缺席时落回 surface，行为与从前一致。 */
  background: var(--cc-side-bg, var(--cc-surface));
  border-right: 1px solid var(--cc-side-sep, var(--cc-border));
  /* 2026-11-08 对齐 demo：外壳不再统一垫 padding，各块自带（brand 20 / cap 10-8 / user 12-14）；
     x 方向统一走 --side-line-x（= 列表容器内距 + 用户「左右」滑块），
     所有块的文字落在同一条线上 —— 这个 token 定义在 .app-side__section 上，
     品牌区 / 底栏不归它管（它们用各自的 20px / 12px）。 */
  padding: 0;
  /* 收起时宽度归零，必须裁掉内容，否则子元素会溢出到主区上面。
     收起用宽度过渡而不是 v-if：目录树和 hover 级联都有各自的展开/关缓冲，
     节点直接被销毁会让这些状态跟着重来一次。 */
  overflow: hidden;
  transition: width 0.2s, padding 0.2s;
}
.app-shell.is-side-collapsed .app-side {
  width: 0;
  padding-left: 0;
  padding-right: 0;
  border-right-width: 0;
}
/* §4.5(f) 收起侧栏那 0.2s 里，内容被"门夹住"。
   264px 一路缩到 0 的过程中，书名（有 ellipsis）、抬头、用户菜单跟着被压缩+裁切，
   而它们**没有任何 opacity 过渡** ⇒ 观感是一行行文字被吃掉。
   → 收起时先淡出再收宽。
   ⚠️ 用 `.app-side > *` 而不是给每个子元素加：这些子元素各有各的
   transition（目录树展开、hover 级联），统一在外层做一次淡出更省事。 */
.app-side > * {
  transition: opacity 0.12s;
}
.app-shell.is-side-collapsed .app-side > * {
  opacity: 0;
}

/* 品牌区 = 首页入口（RouterLink 渲染成 <a>）。
   `text-decoration` / `color` 得自己写：全局 reset 只清了 margin/padding，
   <a> 的下划线和浏览器默认的链接蓝还在（.app-cats__item 当年也是单独补的这两条）。 */
.app-side__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  /* 品牌行落底：与下面目录区隔出一条发丝缝（主题 token，本色=浅灰）。 */
  padding: 20px 20px 18px;
  border-bottom: 1px solid var(--cc-side-sep, transparent);
  text-decoration: none;
  color: inherit;
}
.app-side__logo {
  width: var(--ctl-md);
  height: var(--ctl-md);
  border-radius: var(--radius);
  background: var(--cc-logo-gradient);
  /* 徽标前景走专用 token：曜夜的渐变底是深色，写 var(--cc-surface) 会变深字深底 */
  color: var(--cc-logo-fg);
  font-size: var(--text-md);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 内环高光：渐变底上的玻璃感（本色下几乎不可见，深/彩主题下成立）。 */
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 16%);
}
.app-side__name {
  /* 字号 17→16（2026-11-13 壳上字号三档收敛）：品牌名与顶栏标题格同档（--text-lg），
     壳上最大的文字就这两处 —— 左边「这是哪」、上边「我在哪」。原来 17 比顶栏 16 还大，
     常驻装饰压过了当前位置。 */
  font-size: var(--text-lg);
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: var(--ls-tight);
  transition: color var(--t-fast, 140ms) var(--ease, ease);
}
/* 品牌行 = 首页入口，得有「能点」的反馈（2026-11-13）：原来没有任何 hover，
   长得像静态标题。hover 名字变主色、按下更深一档 —— 只动字色不加底，
   品牌区是常驻装饰，反馈够认出「这是链接」即可。 */
.app-side__brand:hover .app-side__name {
  color: var(--cc-primary);
}
.app-side__brand:active .app-side__name {
  color: var(--cc-primary-hover, var(--cc-primary));
}

/* 目录区**吃满剩余高度**（2026-11-02）：原来 `.app-cats` 顶着 40vh 上限，
   目录少的时候下方空一大片、目录多的时候在一个小窗口里滚。
   现在侧边栏只有"一个滚动条"，高度随视口自适应。
   ⚠️ margin-top 从 --sp-4 提到 --sp-12（2026-11-03 晚）：上面那行
   「目录」抬头删掉了，这段留白要由这里补上 —— 不然输入框会贴着品牌区。 */
.app-side__section {
  margin-top: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
  /* 目录区 **x 轴的唯一基准**（§4.5(a)）。
     一列文字的左沿 = 列表容器内距（--side-box-px）+ 行的左右内距（--side-px）。
     ⚠️ 2026-11-11：原来没有这个基准，四个位置各写各的 ——
     `.app-cats` 写死 10px、行写 --side-px、抬头写 20px、空状态写 12px ⇒ 同一列文字四个左沿。
     最刺眼的是「全部目录」：它在 `.app-cats` **外面**（压根不受那道 10px 内距管），
     于是整行连 hover/选中的底色块都比下面那摞书左右各外扩 10px —— 看着就是"没对齐"
     （用户 2026-11-11：「这个全部目录的排版没有跟其它目录对齐啊」）。
     现在四处全读这一个值：文字、底色块、右侧条数都落回同一条线；
     用户拖「左右」滑块（ui.side.sidePadX）时它们一起走。 */
  --side-box-px: 10px;
  --side-line-x: calc(var(--side-box-px) + var(--side-px));
}
/* 拖着一个目录时，这一整块是"移成一级目录"的落点。
   和树上那两套（蓝=能放 / 红=不能放）用同一组色，别搞出第二套视觉语言。 */
.app-side__section.is-drop {
  outline: 2px solid var(--cc-primary);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}
.app-side__section.is-bad {
  outline: 2px solid var(--cc-danger-deep);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}
/* 框内右侧那一排操作（清除 / 生成主题 / 新建目录）。 */
.app-side__ops-row {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}
/* 目录区抬头右边那两个图标按钮（生成主题 / 新建目录）。
   ⚠️ **2026-11-06 放大**（用户：「图三这个目录的字体大小和右侧的按钮太小了
   我觉得保持跟下面的目录大小一样好一点吧」）。
   原来图标 15px、点击区 19×19 —— 下面那些**目录名是 15px 的中文**，
   而 15px 的线性图标实际只有 ~12px 的"墨迹"高度 ⇒ 摆在 15px 的中文旁边
   明显小一号（截图里就是那个感觉）。
   ⇒ 图标提到 **18px**（--text-2xl）：18px 的线性图标墨迹高度 ≈ 15px 中文的高度，
   看上去才是"同一个大小"。点击区跟着长到 26×26（图标 18 + 上下左右各 4）。
   两个按钮**必须同规格**（同一个字号、同一套描边，只差形状）。 */
.app-side__op-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--cc-text-3);
  cursor: pointer;
  font-size: var(--text-2xl);
  line-height: 1;
  padding: var(--sp-4);
  border-radius: var(--radius-xs);
  transition: var(--cc-transition);
}
.app-side__op-btn:hover {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
/* 带角标的按钮：角标绝对定位在右上角，所以按钮得是定位父级 */
.app-side__op-btn--badge {
  position: relative;
}
.app-side__badge {
  position: absolute;
  top: -2px;
  right: -4px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 99px;
  background: var(--cc-primary);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  line-height: 14px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  pointer-events: none;
}
/* 目录区抬头（2026-11-05 晚回来的一行）：左边「目录」，右边两个操作。
   ⚠️ **原来没有这一行**（2026-11-03 晚删的，理由是"搜索框自己就说明了这块是什么"）——
   那个「过滤目录」框并进顶栏之后，框没了 ⇒ 这一行得回来。 */
.app-side__cap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-9);
  margin-bottom: 0;
  /* §4.5(a)：这一行抬头的左右内距**必须等于**它领的那一列书名的左沿
     （= --side-box-px + --side-px，见 .app-side__section 上的说明）。
     ⚠️ 原来这里写死 20px，而书名行是 10 + 12 = 22 ⇒ 抬头比自己的列表左偏 2px；
     用户拖「左右」滑块时差得更多。现在统一读 --side-line-x。 */
  padding: var(--sp-9) var(--side-line-x, 22px) var(--sp-8);
}
/* 抬头左边那个「目录」。
   ⚠️ **2026-11-06 放大**（同上一条，用户：跟下面的目录大小一样）：
   原来 13px（--text-sm）+ 最浅的一档灰（--cc-text-4）⇒ 缩在角落里，
   比下面 15px 的目录名小了一大截，压根不像一行抬头。
   现在字号跟目录名一样（--text-base），颜色留在 --cc-text-3 ——
   比目录名（--cc-text-2）浅一档，**抬头还是抬头**，但不再是"小字"。 */
.app-side__cap-title {
  /* 15→14（2026-11-13 三档收敛）：跟着书名一起降 —— 2026-11-06 用户定的
     「抬头跟目录名同档」是相对关系，档位一起挪、关系不变。
     颜色从最浅的 --cc-side-cnt-fg（老稿 --side-cap）提深到 --cc-text-3：
     抬头是抬头，但别再缩在角落里。 */
  font-size: var(--text-md);
  font-weight: 500;                        /* 老稿 .app-side__cap-title 的 500 */
  letter-spacing: 0.02em;
  color: var(--cc-text-3);
  white-space: nowrap;
}
.app-cats {
  display: flex;
  flex-direction: column;
  gap: var(--side-list-gap);
  overflow-y: auto;
  /* 吃满目录区剩下的高度（min-height:0 是让 overflow 生效的前提：
     flex 项的默认 min-height:auto 会被内容顶开，滚动条就永远不出现）。 */
  flex: 1;
  min-height: 0;
  /* ⚠️ 这个内距不要写裸值：--side-box-px（定义在 .app-side__section 上）就是它，
     「全部目录」那一行在容器**外面**，读同一个 token 才能跟书名列对齐。 */
  padding: 0 var(--side-box-px, 10px);
}
.app-cats__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--side-gap);
  height: 34px;
  padding: 0 var(--side-px);
  border-radius: 8px;   /* 老稿 .app-cats__item 的 8px（--radius-sm 是 6，差一档） */
  color: var(--cc-text-2);
  /* 15→14（2026-11-13 三档收敛）：导航是「去哪」的辅助信息，压到正文字号（--text-md），
     不比正文大；和顶栏标题（16）拉开 2px 的层级，而不是原来 15/16 差 1px 的含混。 */
  font-size: var(--text-md);
  text-decoration: none;
  /* 选中态的左短柱要贴着圆角走 → 需要定位上下文。 */
  position: relative;
}
/* 「全部目录」那一项（搜索态）：它是"不去哪本"，**跟那摞书分开一道缝**，
   否则它看着就是那摞书里的第一本。
   ⚠️ 它是 `.app-cats` 的**兄弟**（在容器外面），所以那道 10px 水平内距得自己补：
   补之前整行文字左起 12px、底色块 0→263px，而书名列是 22px / 10→253px
   ⇒ 右移 10px 才跟下面那摞书落在同一条线上（用户 2026-11-11 报的「没对齐」）。 */
.app-cats__all {
  margin: 0 var(--side-box-px, 10px) var(--sp-6);
}
/* ⚠️ 侧边栏行上的 --row-tools 机制随切换钮一起删了（2026-11-08）：
   这一行现在只有名字 + 常显条数，没有 hover 才显形的工具。 */
.app-cats__item:hover {
  /* **纸白小卡**（2026-11-13）：surface 底 + 卡片描边，和全站卡片同一语言。
     原来 §4.4(b) 的 surface-2 底在素雅下和侧栏底（#fbf9f2 vs #faf7ef）色差≈0，
     hover 基本无感 —— 这一层反馈得靠「亮一档的纸白 + 一根描边」才看得见。 */
  background: var(--cc-surface);
  box-shadow: inset 0 0 0 1px var(--cc-border-card);
  color: var(--cc-text-1);
}
.app-cats__item.is-on {
  /* 选中**两层**（2026-11-13）：底（primary 14% 混 surface，比原来的 10% soft 浓一档）
     + 左短柱。原来的第三层「内描边环」删了 —— 环是底色太淡时补的，底色够了它就多余。 */
  background: color-mix(in srgb, var(--cc-primary) 14%, var(--cc-surface));
  color: var(--cc-primary);
  font-weight: 600;
}
/* 选中三层之三：左侧短柱。绝对定位贴行左缘，:drop/:bad 的整行底色压不过它
   （拖拽是临时态、选中是常态，并存时柱子留着无妨）。 */
.app-cats__item.is-on::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 2px;
  background: var(--cc-primary);
}
/* 拖拽时这一行是落点。跟树上的行同一组色（蓝=能放 / 红=不能放），
   不另起一套视觉语言。压在 :hover 之后：hover 是常态、落点是一次性事件，
   一次性的要压过常态，否则拖到哪行都看着像"只是悬停着"。 */
.app-cats__item.is-drop {
  background: var(--cc-primary);
  color: var(--cc-surface);
}
.app-cats__item.is-bad {
  background: var(--cc-danger-soft);
  color: var(--cc-danger-deep);
}
/* 目录名 + 祖先路径（过滤时才出现路径）。**两者必须贴在一起**：
   名字和它的路径分得老远，就成了两段互不相干的信息。 */
.app-cats__label {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: var(--sp-6);
}
/* 名字吃掉全部剩余宽度，数字和图标才贴到同一条右边上。
   不给名字 flex 的话，space-between 会把余量均分给几个子元素——
   "JAVA 7" 的 7 贴着名字、"计算机网络 2" 的 2 却被推到更右，两列对不齐。

   ⚠️ 这里是 `0 1 auto` 而不是 `1`：名字要是"吃掉全部宽度"，
   数字就被顶到行尾、和名字隔开一大段。0 1 auto = 按内容宽排、空间不够才压缩。 */
.app-cats__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 条数 = **纯数字**（2026-11-13 胶囊退场）：等宽、右缘对齐，hover 加深、选中换主题色。
   2026-11-08 的胶囊（底色 + 圆角）撤了 —— 十本书十个胶囊是这一列最大的噪点，
   数字信息一点没少；树视图那列的条数（CategoryTree 的 .ct__count）从来就是纯数字。
   ⚠️ tabular-nums 保持（2026-11-06：一位数/两位数右缘对齐、数字变化不抖）；
   min-width 3ch 与 CategoryTree 同口径。 */
.app-cats__count {
  /* 11.5px 是不在尺寸阶梯上的手写值（§5.2 禁的那类）→ 归位 --text-xs（2026-11-13）。 */
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  min-width: 3ch;
  text-align: right;
  transition: color var(--t-fast, 140ms) var(--ease, ease);
}
.app-cats__item:hover .app-cats__count {
  color: var(--cc-text-3);
}
.app-cats__item.is-on .app-cats__count {
  color: var(--cc-primary);
}
/* ⚠️ **「树 ⇄ 卡片」切换钮（.app-cats__view-btn）删了**（2026-11-08 用户：
   「可以把侧边栏一级目录的那个切换图标也给去了」）—— 切换收进树视图标题行的
   分段式「目录树 | 卡片」（就地换内容盒子）。侧边栏回归纯导航：名字 + 条数。 */
.app-side__empty {
  /* 这几种状态（加载中 / 没有匹配 / 没有目录）是那一列目录的**替代文案**，
     左沿要跟书名行同一条线 ⇒ 读 --side-line-x（原来是 sp-10 = 12px，比书名列左偏 10px）。 */
  padding: var(--sp-5) var(--side-line-x, 22px);
  margin: 0;
  font-size: var(--text-md);
  color: var(--cc-text-4);
}
/* 加载中那一条：**图形 + 文案**一行排开（2026-11-13 统一加载层）。
   .app-side__empty 是 <p>，默认块级 ⇒ 这里改成 flex 才能把图形和文字并排。 */
.app-side__loading {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
}

.app-side__user {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  /* 左右内距读 --side-line-x（2026-11-13）：和书名列同一条线。原来写死 20px，
     素雅下碰巧相等（10+10），本色下书名列是 22px、底栏差 2px —— 写死就注定漂。 */
  padding: var(--sp-10) var(--side-line-x, 20px) var(--sp-9);
  border-top: 1px solid var(--cc-side-sep, var(--cc-surface-2));
}
/* 用户菜单在底栏里要**撑满**，齿轮才能被顶到最右。
 *
 * ⚠️ **撑的是 wrapper，不是触发器。** `ElDropdown` 的根是它自己包的一层
 * `<div class="el-dropdown">`，触发器在那一层**里面**。所以给触发器写 flex: 1
 * 只会在 wrapper 内部把尖角推到名字右边，**footer 层面那个 flex item 仍按内容收窄**，
 * 齿轮照样贴着尖角（2026-10-07 实机截图）。
 *
 * 现在用户菜单是独立的 `UserMenu` 组件，**它的根元素**承担这个 flex: 1
 * （ElDropdown 没声明 class prop，但只有一个根元素，Vue 的 fallthrough attrs
 * 会把 class 合并到根 div 上——所以这个 class 确实落在 wrapper 上）。
 * `min-width: 0` 别省：不然长用户名的 min-content 会把这一格顶开、名字不省略号。 */
.app-side__user-menu {
  flex: 1;
  min-width: 0;
}
/* 头像边界环（对齐老 demo：照片头像深色无边会「融进」背景，环给它明确的大小感） */
.app-side__user-menu :deep(.el-avatar) {
  box-shadow: 0 0 0 1px var(--cc-side-sep);
}

/* 启动加载失败时顶掉用户名那一格。文案用警示色而不是灰——"加载失败"是需要
   用户点一下重试的状态，不该和"未设置昵称"那种中性缺省长得一样。 */
.app-side__failed {
  flex: 1;
  font-size: var(--text-base);
  color: var(--cc-danger);
}
/* 用户菜单的触发区：头像 + 名字 + 尖角，整块可点。
   以前这一格是「头像 + 名字 + 一个独立的退出图标」，现在退出收进下拉了，
   触发区整体变成菜单入口，所以要自己给 cursor 和 hover 反馈。

/* 用户菜单的触发区（头像 + 名字 + 尖角）的样式在 components/UserMenu.vue ——
   两个壳共用那一份组件，样式也该跟着走一份。 */

/* ---------- 右侧 ---------- */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-top {
  /* §4.5(c) 顶栏高进 token 族（--h-top = 64px）：侧栏宽有 --w-side、内容宽有
     --w-content、目录行宽有 --w-toc，唯独顶栏高是字面量。
     ⚠️ 2026-11-06 改：`height: 64px` → `height:auto; min-height:var(--h-top)`。
     P0-3 的第三条改动要靠它：窄窗口下顶栏**换行**（搜索框挪到第二行），
     写死 height:64px 会把第二行裁掉 —— 而 `.app-shell{overflow:hidden}` 还会把
     裁掉的部分彻底吃掉，表现为「搜索框只剩 26px 可打字」。
     ⚠️ **2026-11-09 反转这条**：用户：「改一下优先级 搜索框优先级比较高 优先压缩目录索引文字…
     当目录索引文字压缩到最小的时候再压缩搜索框的长度」——
     **换行正是"搜索框被挤到下面"的成因**，所以顶栏改为**恒为一行**：
     宽度不够时先压目录索引（收中间层级），压到底了才收窄搜索框。
     ⇒ flex-wrap 去掉（不再换行）；height 回到固定值（单行用不着 auto）。 */
  height: var(--h-top);
  min-height: var(--h-top);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-13);
  padding: 0 var(--sp-14);
  /* §4.3 的硬编色映射：rgba(255,255,255,.85) 这个 α 是无效声明 ——
     背后没有会滚过的内容（.app-content 是它的**兄弟**滚动容器，不是父级），
     且全站零处 backdrop-filter ⇒ 它只是把白和 --cc-bg 混成 ~#fefefe，
     不是任何一个 token。直接用 --cc-surface。 */
  background: var(--cc-top-bg, var(--cc-surface));
  border-bottom: 1px solid var(--cc-side-sep, var(--cc-border));
}
.app-top__left {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  /* ⚠️ **宽度不由内容决定**（2026-11-09）：之前是 flex 0 1 auto，
     宽度 = 整条链的内容宽 ⇒ 链一长就把右边的搜索框挤到第二行（用户报的正是这个）。
     现在宽度由 JS 算好（--crumb-w）⇒ 这一组只需"占住折叠按钮 + 目录索引盒子"那么多，
     剩下的空隙归 flex 去分（justify-content:space-between 把搜索框推到右边）。
     min-width:0 ⇒ 空间真不够时，这一组可以缩（兜底）。 */
  flex: 0 0 auto;
}
/* 这一格 = **页名 / 当前位置**，标题体：display / 600 / text-1。
   ⚠️ **字号 16 → 15px**（2026-11-14，用户「目录索引的文字稍微大了点」——
   原与旧稿 .app-top__crumb 逐值一致的 16px（--text-lg），降一档到 --text-base）。
   ⚠️ **路径整条吃这个字号**（2026-11-13 二稿）：主次不靠字号 ——
   当前那一截（`.crumbs__cur`）就是这套标题体，祖先链由 CategoryCrumbs 降成
   细浅（400 / text-3）⇒ 首页 / 书页 / 章页同字号，粗的是"你在这一页"。 */
.app-top__crumb {
  font-size: var(--text-base);
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--cc-text-1);
  /* 标题就一两个词，绝不该折行。左边被挤窄时应该先让搜索框收（下面给了 min-width），
     而不是把「知识点」竖着排成一行一个字 —— 那是最难看的破法。 */
  white-space: nowrap;
  /* P0-3 第一条：长面包屑必须能截断。
     `white-space:nowrap` 只保证不折行，**不保证不溢出** ⇒ 窄窗口下
     面包屑会把右侧的搜索框和按钮挤出可视区。加上这三件套才是真的可截。
     ⚠️ 2026-11-09：截断本身**降级到了最后**（目录索引收中间层级、搜索框让位之后
     才可能发生）—— 但这三件套仍要留着，它是"内容比盒子还长"的兜底。 */
  min-width: 0;
  /* ⚠️ **宽度是算出来的**（2026-11-09）：--crumb-w 由 JS 按优先级分配
     （目录索引先让位 → 压到最小了才动搜索框）。这里只负责"内容怎么在盒子里显示"。
     ⚠️ 不能写 flex:1 1 0 —— 那样盒子会被 flex 压到比内容还窄，
     连「…」标记本身都被裁掉（实测 navBox=104 < navContent=230）。
     min-width:0 + 盒宽由变量给 ⇒ 内容再长也只在自己的盒子里截断，不会溢出到右边。 */
  flex: 0 0 auto;
  width: var(--crumb-w, auto);
  max-width: 100%;
}
.app-top__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  /* ⚠️ **不参与收缩**（2026-11-09）：搜索框优先级最高 ⇒ 它要多大给多大，
     宁可让左边的目录索引去让位。宽度由 --search-w 决定（JS 按三段降级链算）。 */
  flex: 0 0 auto;
}
.app-search {
  /* ⚠️ **宽度由 JS 算**（2026-11-09）：--search-w 是顶栏宽度分配的结果
     （目录索引先让位、压到底了才收窄搜索框）⇒ 320 / 160 的数字写在
     AppLayout 的 JS 里（SEARCH_W_MAX / SEARCH_W_MIN），这里只消费。
     聚焦时同样跟着走（+44px），别让聚焦把搜索框顶出顶栏。 */
  width: var(--search-w, 320px);
  height: 34px;
  transition: width 0.25s var(--ease, ease), box-shadow var(--t-fast, 140ms) var(--ease, ease), border-color var(--t-fast, 140ms) var(--ease, ease), background var(--t-slow, 300ms) var(--ease, ease);
}
/* demo 逐条映射：框体 = 白底 + 暖色 1px 边 + 圆角 10；聚焦 = 边变主色浅档 + 外圈 3px ring
   ⚠️ **聚焦不再变宽**（2026-11-09）：顶栏宽度改成算出来的之后，聚焦时多占 44px
   就会把刚分给目录索引的宽度抢走 ⇒ 目录索引被截尾（丢的正是"我在哪一级"）。
   demo（ui-preview/detail-v2.html）的聚焦也只有边框+光环，本来就没有变宽这一项。 */
.app-search:focus-within {
  /* --cc-primary-ring：原引用 --accent-ring 从未定义 ⇒ 这条 box-shadow 一直失效，2026-11-13 修复 */
  box-shadow: 0 0 0 3px var(--cc-primary-ring);
}
.app-search :deep(.el-input__wrapper) {
  height: 100%;
  border-radius: 8px;
  background: var(--cc-surface);
  box-shadow: 0 0 0 1px var(--cc-border-card) inset;
  padding: 0 8px 0 12px;
  transition: box-shadow var(--t-fast, 140ms) var(--ease, ease);
}
.app-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cc-primary-border) inset;
}
.app-search :deep(.el-input__inner) {
  height: 100%;
  font-family: inherit;
}
.app-search :deep(.el-input__inner::placeholder) {
  color: var(--cc-text-4);
  font-family: inherit;
}
.app-search :deep(.el-input__prefix) {
  color: var(--cc-text-4);
  font-size: 15px;
}
.app-search__mode {
  /* 2026-11-08 对齐 demo：描边小药丸，底色比框体深一档（层次），完整落在框内 */
  height: 24px;
  padding: 0 10px;
  margin: 0;
  border: 1px solid var(--cc-border-card);
  background: var(--cc-surface-2);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-family: inherit;
  color: var(--cc-text-2);
  cursor: pointer;
  white-space: nowrap;
  line-height: 1;
  transition: border-color var(--t-fast, 140ms) var(--ease, ease), color var(--t-fast, 140ms) var(--ease, ease);
}
.app-search__mode:hover {
  border-color: var(--cc-primary-border);
  color: var(--cc-primary);
}
/* 详情页 suffix：本文命中计数 + ↑↓（与「正文/目录」切换互斥，见模板那段注释）。
   ⚠️ **`.app-search__hits` / `.app-search__jump` 的样式已经搬到 `SearchHitsControls.vue`**（2026-11-10）——
   原来写在这儿（父组件的 scoped 样式）⇒ 编译成 `.app-search__jump[data-v-父]`
   而按钮 DOM 在**子组件**里（多根组件不继承父的 data-v）⇒ **选择器压根没命中**
   ⇒ 浏览器 UA 默认按钮外观（灰底 + 2px outset 边框 + 直角）⇒ 用户报「显示的这么丑」。
   ⇒ **样式跟着 DOM 走**（组件自带 scoped），别在父组件里替子组件的 DOM 写样式。 */
/* **自己画的清除按钮**（不用 EP 的 `clearable`）：EP 那个是悬停/聚焦才冒出来的，
   会把 ↑↓ 挤走 ⇒ 用户点箭头点到的是 ×。自己画的**位置固定在最后**。
   ⚠️ **与 ↑↓ 同一套视觉**（2026-11-10 用户：「换一下美观度」）：
   同样是无壳小图标钮（22×22、圆角、transparent 底），hover 反馈也一致
   ⇒ suffix 区里三颗按钮看着是同一样东西，不是"两个箭头 + 一个补丁"。 */
.app-search__clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-3);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  transition: background 0.15s, color 0.15s;
}
.app-search__clear:hover {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
.app-top__icon-btn {
  border: none;
  background: transparent;
  color: var(--cc-icon);
  cursor: pointer;
  padding: var(--sp-7);
  font-size: var(--text-2xl);
  /* §4.4(d)：--cc-radius-sm(8px) 与 --radius-sm(6px) 是两个不同的值、名字只差一个前缀
     ⇒ 这两个别名已删。现在只剩 --radius-* 七档，按控件高度归档：
     28–34px 高的控件 → --radius-sm(6)。 */
  border-radius: var(--radius-sm);
  display: flex;
  /* §4.1 第 1 条：原来只有 display:flex，缺 align-items/justify-content
     ⇒ 图标没有垂直居中（同行还有别的元素时更明显）。
     同文件 :909-911 与 kb.css:103-105 都写全了，这里补上。 */
  align-items: center;
  justify-content: center;
  /* §4.5(e)：顶栏这两个按钮没有 transition，而同类控件全都有
     （.app-side__op-btn / .app-cats__view-btn / kb.css / RowMenu）
     ⇒ hover 时底色和字色瞬间跳变，同屏的侧栏按钮却是渐变的。 */
  transition: var(--cc-transition);
}
.app-top__icon-btn:hover {
  /* §4.4(a) hover 反馈两档：**图标型可点 = hover 变 --cc-primary + --cc-primary-soft 底**。
     （文字型可点那一档 = --cc-text-1 + --cc-surface-2 底，写在 kb.css 的注释里。） */
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}

/* 内容区：居中窄栏——这是"产品"的视觉签名，不是"后台"。
   宽度只剩一档（base.css 的 --cc-content-w）。原来还有一档 --cc-content-w-reading
   专给详情页的 920px，#134 解除了列表页/首页的 1440 上限后**把详情页留作例外**，
   而那个例外正是用户反复投诉的"窗口一变宽、卡片比例就掉"的来源，已一并解除
   （见 base.css 同名注释）。
   1280 只在约 1330px 以上的视口才咬：1440 / 1536 这类笔记本已经铺满，
   1920 收起侧栏时左右留白从 476px 收到 296px。 */
.app-content {
  flex: 1;
  overflow-y: auto;
  /* 滚动条常驻留槽。**这不是美化，是修一个横跳**：内容是 `margin: 0 auto` 居中的，
     而滚动条长在本元素里 —— 它一出现，容器内容宽度就少一条，**居中的起点跟着变**。
     1920 / scale 1.3 下实测：内容被 max-width 截到 1664（宽度不变），
     左起点从 (1864-1664)/2 = 100 跳到 (1849-1664)/2 ≈ 92，**整块内容横移 8px**。
     触发条件只是"这一屏放不放得下"——「全部 33」和「待归类 3」卡片数差很多，
     切一下筛选滚动条就时有时无，看着像筛选条自己动了一下。
     留槽之后有没有滚动条都一样宽，起点也就固定了。 */
  scrollbar-gutter: stable;
  /* 底部 72px 是给**会滚动**的页面留的翻页呼吸位（列表页拉到最底时最后一张卡
     不会贴着视口边）。首页 AppHomeView 是唯一一页「三排刚好铺满一屏」的，它不滚动，
     不该继承这份呼吸位——见下面那条覆写。

     72 直接写字面量，不提成变量：#126 之前中间过一层 `--app-pad-b`，那是因为
     当时的错误做法让**子页面**去读它算补偿。现在子页面已经不读了（它在 .home 上
     老老实实 min-height:100%），这个变量只剩"被自己定义的 padding 消费"一次，
     纯属多绕一层。

     ⚠️ 2026-11-06 改（§4.5(d)）：72 是**唯一跳出 --sp-* 阶梯的间距**
     （阶梯 16 档最大 --sp-16 = 40px）⇒ 阶梯上出现一个孤儿值。
     写成 calc(var(--sp-16) + var(--sp-15)) = 40+32 = 72，语义是"两档之和"，
     来源可追。值一个字节没变 ⇒ 视觉零变化。 */
  padding: var(--sp-15) var(--sp-15) calc(var(--sp-16) + var(--sp-15));
}
/* 首页不留底部呼吸位，改成和左右一样的 --sp-15。
   左右留白由 .home 自己带（--home-pad-x，见 uiPrefs.js 的 homePadX）。
   ⚠️ **原注释写着「左右留白交给偏好面板（--home-pad-x）」—— 那是撒谎**：
   当时 `--home-pad-x` **零消费者**（面板滑块拖了没反应），.home 的左右内距是写死的 44px。
   2026-11-11 才把那个变量接上（首页改居中那轮一并修的）。

   ⚠️ **这里改 padding，不要让子页面去"补偿"**（#124 / #125 连续返工两次就在这）。
   之前首页写的是 `min-height: calc(100% + 72px - var(--sp-15))`，看着是把 72−32
   补回来了，实际是**错的**：`100%` 量的是内容盒，`.home` 顶到 y=32、底到 y=H−32，
   而**内容盒底边在 y=H−72** —— 元素多出来 40px 扎进了 padding 区。padding 不吸收
   溢出，滚动容器照样按内容盒 + 溢出算 scrollHeight，于是**凭空多出 40px 滚动条**。
   补 padding 只能在**拥有这个 padding 的元素自己身上**改。 */
.app-content:has(.home) {
  padding: 0;
  padding-bottom: 0;
  /* 2026-11-08 对齐 demo：左右内距由 .home 自带（44px），这里归零避免双重叠加 */
  padding-left: 0;
  padding-right: 0;
}
/* 知识点列表：左右留白（--learn-pad-x，见 uiPrefs.js）+ **顶部间距**（--learn-pad-y）。
   底部呼吸位照旧。

   判据用 :has(根节点) 而不是路由名：这一页"要不要底部留白 / 左右留白多大"是**它自己的
   形态**（三排铺满一屏 / 卡片网格）决定的，认根节点比认路由贴近事实，路由改名也不会失效。

   ⚠️ **顶部间距是 2026-11-04 加的**（用户：「这个一级目录 -> 二级目录 往上一点
   现在离上面的间隙比较大显得不好看」）—— 原来这里只有左右两档，顶部跟着
   `.app-content` 的通用 padding（--sp-15 = 32px）⇒ 面包屑离顶栏很远。
   顶部那一档**独立可调**（跟「上下」不是一回事：「上下」是区块**之间**的间距，
   这是**整体往上的偏移**）。 */
/* 详情页 v10（2026-11-09）：**壳收滚** —— 本页改为「头部固定 + 正文(.ProseMirror)唯一内滚」，
   双滚动条就此消除。flex 列把确定高度传给 .detail（高度链见 kb-editor.css 的 v10 段）。
   底部呼吸位从 72 收成 --sp-14：卡片自己铺满一屏，不再需要翻页呼吸位。
   判据沿用 :has(根节点) 的口径（认形态不认路由名）。 */
.app-content:has(.detail) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-bottom: var(--sp-14);
}
/* 首页同款壳收滚（2026-11-15）：这一页本来就是「三排铺满一屏」的形态（编辑器自己
   内滚，见 kb-editor.css 的 .quick .ProseMirror），壳不收滚的话 .home 高度永远跟着
   内容涨 ⇒ flex 从不出现赤字 ⇒ 矮窗时下方的栏纹丝不动、录入区被压没
   （用户：「输入框不能被挤压，下方的栏先被挤压然后才是输入框」）。
   收滚之后赤字才存在，压缩顺序才轮得到 flex-shrink 说话。 */
.app-content:has(.home) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.app-content:has(.learn) {
  /* ⚠️ 2026-11-08 晚：这一页的留白**整个交给版心**（AppLearnView 的 .learn =
     老稿 .main__inner：max-width 1160 + padding 34/44/60）。这里归零，
     否则 44px 在版心外面再叠一层，内容净宽和左右留白都对不上老稿。
     uiPrefs 的 learnPadX / learnPadY 滑块没死 —— 改由 .learn 消费（默认 44 / 34）。 */
  padding: 0;
}
.app-content > * {
  margin: 0 auto;
}
/* ⚠️ **详情页的宽度与居中由 `.detail` 自己管**（2026-11-11）。
   这里原来有一条 `.app-content:has(.detail) > :where(*) { margin-left/right: 0 }`
   （2026-11-10 修「正文少的知识点进来变成窄卡」加的），**现在整条撤掉**，理由两条：

   ① **它和「不封顶」是绑在一起的**。归零的成立前提是「详情页铺满内容区」
   （#134 的效果）。2026-11-11 `.detail` 自己带了 `max-width: 1200px` + 居中
   （行长恒定 ≈78 字，见 AppTopicDetailView.vue 里的 .detail 注释），
   ⇒ 归零会让它**永远贴左**，超宽屏时右边空一大片 —— **正是首页 A2 那个坑**。

   ② **两处各管一半必打架**。`.detail` 自己已经写了 `width/max-width/margin`，
   壳这边再写一遍外边距 ⇒ **同权重（都是 0,1,0）靠源序定胜负**，
   而 scoped 样式的源序取决于组件加载顺序，**不可靠** ⇒ 会出现「有时居中有时贴左」。
   ⇒ **宽度规则只留一处**：`.detail` 自己。

   ⚠️ **那个"窄卡"坑（#855）由 `.detail` 自己堵上**：
   flex 列里 `margin: 0 auto` 会让子项**按内容宽度收缩** ⇒ 正文短就成窄卡。
   ⇒ 堵法是 `.detail` 里的 **`width: 100%`**：先拉满、再被 max-width 截住。
   ⚠️ **别在这里给 `.detail` 补 width**：`.app-content > * { margin: 0 auto }`
   那条通用规则对 .detail 同样是 0 auto（两处一致，不冲突），但 width 必须在 .detail 上
   ——它得在 max-width 之前生效，壳这边写就管不到那个顺序了。 */
/* **知识点列表 / 知识点详情解除 1440 读宽上限**——但两者的理由不一样，
   别把其中一条当通用理由：

   · **知识点列表**：`auto-fill minmax(--w-grid, 1fr)` 的卡片网格**在内部就已经把列宽封住了**
     ——列数 = floor((可用宽+gap)/(400+gap))，列宽在"刚好塞不下一列"时被 1fr 拉到最大，
     上界约 **537px**。**卡片宽度本来就有界，屏幕再宽只是多排列**，外面这层封顶是多余的。
   · **知识点详情**：⚠️ #134 当初**只解除了前两页、把详情页留作例外**（它当时自己压着
     920px）。但用户随后发现详情页是**同一个毛病**，原话："**在比例相同的情况下 不应该
     被是否全屏影响啊 为什么会存在长度限制**"。根因就是这个 max-width——定值，宽过它卡片
     就卡住、两侧空白一路增长、占屏比例一路往下掉。**#134 留下的那个例外，正是问题本身的来源。**
     详情页自己那条封顶后来也整个删掉了（2026-10-21：「阅读宽度」那根滑块默认 2400 ≈ 不封顶，
     **调它像调了个摆设**），所以这条必须一起解除：
     **不解除的话，删掉详情页自己的 920 只会让它退到 1440 这个定值上，问题一点没解决。**

   两条的共同后果（用户原话："很明显缩放的更好看"、"拉满就两侧空一大片"）：浏览器一放大、
   视口宽度掉下来跨过上限，**内容宽度当场停住**，两侧开始长空白。
   ⚠️ 1440 现在**没有任何消费者**了（--cc-content-w 仍是 admin 之外的默认值来源）。
   ⚠️ **首页 2026-11-09 从这条里摘出去了**：录入框单独收 1080 而 .home 被解除上限时，
   统计卡/复习面板随 .home 铺满全宽、录入框卡在 1080 ⇒ 右边缘错开（1920 视口差 480px，
   实测），窗口一最大化就是"输入框右侧一大片空白"。首页改由 .home 自己的 1160 统一收口
   （整页一条工作面，见 AppHomeView），这条解除规则不再管它。

   ⚠️ **2026-11-11：详情页从这条里也摘出去了** —— 它**自己**封顶 1200 居中了
   （见 AppTopicDetailView.vue 里 .detail 的注释：行长恒定 ≈78 字 + 超宽时左右对称留白）。
   留着它会做两件坏事：
     ① `max-width: none` 与 `.detail` 自己的 `max-width: 1200px` **两处各管一半**
        （虽然 scoped 的 .detail 权重更高能赢，但那是**靠巧合**，不是靠设计）；
     ② 注释会继续骗人 —— 让人以为详情页不封顶。
   ⇒ **这条现在只管列表页**。 */
.app-content:has(.learn) > :where(*) {
  max-width: none;
}
.app-content > :where(*) {
  max-width: var(--cc-content-w);
}
</style>
