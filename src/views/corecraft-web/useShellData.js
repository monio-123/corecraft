import { bootstrapSession } from '../../utils/session'
import { SEARCH_SCOPE_BOTH } from '../../utils/searchMode'
import {
  ensureTopicIndexLoaded, ensureFullTopicIndexLoaded, ensureCategoriesLoaded
} from './useCorecraftWebStore'
import { ensureAiConfigLoaded } from './useAi'

/**
 * 业务壳启动要的数据：会话（菜单 / 资料 / 权限）、知识点、知识目录、知识点类型。
 *
 * 原来 AppLayout 和 AppHomeView 的 onMounted 各写了一遍**一模一样**的
 * Promise.all([bootstrapSession(), ensureTopicsLoaded(), ensureCategoriesLoaded()])，
 * 那是一次性拷贝——store 的 renameTag / confirmDeleteTag 刷的是它自己那份 allTags，
 * 本地这份是副本不会跟着变，于是"在标签下拉里改名/删除之后列表不动，必须手动刷新页面"。
 * 知识点列表没这个问题是因为它一直是 computed(() => getTopics())，只有标签这一处漏了。
 *
 * 类型清单也收在这里：首页录入、列表页筛选、详情页改类型三处都要它，
 * 各写一遍就是第三份重复。字典是全局的，跟着壳启动拉一次就够。
 *
 * 标签列表**整条下线了**（2026-11-03 用户拍板「标签从页面上去掉 后台逻辑暂时不去」）：
 * 界面上没有任何入口，后台那套逻辑留着是有意为之（见 brove-skills/corecraft-web-context
 * 「标签：界面已下线，后端有意保留」）。
 * ⇒ 这里**不用再解释为什么没拉它** —— 以前那句「详情页的 tagListOpen watch 才懒加载」
 * 指向的 watch 已经随标签界面一起删了，留着就是指着不存在的东西。
 *
 * 每一项都幂等（session 有 snapshot 短路、store 三个有 loaded 标志），
 * 所以壳和页面都调它不会打两次接口。
 *
 * <p>⚠️ **keyword 是"结构索引"的一部分**：索引是"当前关键词下的全集"（关键词要搜正文，
 * 只有服务端能筛）。所以启动时就得把 URL 上的关键词传进来 ——
 * 否则带 `?q=` 进来的那一屏，侧边栏的条数是"不筛关键词"的数字，和列表里看到的对不上。
 *
 * 返回会话快照（`{ menuTree, menuPaths, profile }`），调用方各取所需：
 * 壳要整份、首页只要 profile。硬塞成一份共享 state 反而多一层间接。
 *
 * @param {string} keyword 当前关键词（URL 的 `?q=`）
 * @param {string} scope 搜哪一块（标题 / 正文 / 都搜，见 utils/searchMode.js）
 */
export async function loadShellData(keyword = '', scope = SEARCH_SCOPE_BOTH) {
  const [session] = await Promise.all([
    bootstrapSession(),
    // ⚠️ **顺序有意义**：搜索索引先调（它的"身份"在函数体开头就同步写下了），
    // 全集索引那份才知道"现在有没有搜索词" ⇒ 没搜索词时它**直接复用**搜索索引，
    // 不重复打同一个接口（见 store 的 ensureFullTopicIndexLoaded）。
    ensureTopicIndexLoaded(false, keyword, scope),
    ensureFullTopicIndexLoaded(false),
    ensureCategoriesLoaded(),
    // AI 配置也在这里拉：界面各处用 aiEnabled 控制 AI 入口显不显示，
    // 那个判断发生在渲染时——配置要是等进设置页才加载，首页/详情页会先画出一瞬
    // "没有 AI"的界面再跳变。跟着壳启动一次，状态从第一帧就是对的。
    ensureAiConfigLoaded()
  ])
  return session
}
