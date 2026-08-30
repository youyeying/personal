/**
 * 个人记录系统 前端入口
 * 挂载 sakura 根组件，注册 Element Plus / Pinia / Router
 */
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
// Element Plus 暗色模式变量（配合 html.dark 生效）
import 'element-plus/theme-chalk/dark/css-vars.css'

import Sakura from '@/sakura.vue'
import router from '@/router'
import { pinia } from '@/store'

// 字体（本地自托管）：
// 站酷小薇体（现代设计感）+ 站酷庆科黄油体（可爱加载）
import '@fontsource/zcool-xiaowei/chinese-simplified.css'
import '@fontsource/zcool-qingke-huangyou/chinese-simplified.css'
// 霞鹜文楷 Screen（GB 简体版：常规 + 粗体，unicode-range 子集按需加载）
import 'lxgw-wenkai-screen-webfont/lxgwwenkaigbscreen.css'
import 'lxgw-wenkai-screen-webfont/lxgwwenkaigbscreenr.css'

// 全局样式（scss）
import '@/styles/index.scss'

const app = createApp(Sakura)

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#sakura')
