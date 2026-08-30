/// <reference types="vite/client" />

/** .vue 文件类型声明 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

/** 文件读取能力：浏览器 FileReader 的全局对象无需额外声明 */
