import { createPinia } from 'pinia'

/** 全局 Pinia 实例 */
export const pinia = createPinia()

// 各业务 store 后续在 store/ 下新建
export * from './user'
