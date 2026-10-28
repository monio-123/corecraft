import { bootstrapSession } from '../../utils/session'
import {
  ensureTopicIndexLoaded, ensureCategoriesLoaded, ensureTopicTypesLoaded
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
 * 标签列表是**故意不在这里**的：详情页打开不请求它，懒加载到"点开下拉"才拉
 * （见 AppTopicDetailView 的 tagListOpen watch）。
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
 */
export async function loadShellData(keyword = '') {
  const [session] = await Promise.all([
    bootstrapSession(),
    ensureTopicIndexLoaded(false, keyword),
    ensureCategoriesLoaded(),
    ensureTopicTypesLoaded(),
    // AI 配置也在这里拉：界面各处用 aiEnabled 控制 AI 入口显不显示，
    // 那个判断发生在渲染时——配置要是等进设置页才加载，首页/详情页会先画出一瞬
    // "没有 AI"的界面再跳变。跟着壳启动一次，状态从第一帧就是对的。
    ensureAiConfigLoaded()
  ])
  return session
}
