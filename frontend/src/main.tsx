import './index.css'
import { initI18n } from '@/lib/i18n'

// 先确定界面语言并加载词典，再导入应用：
// 各页面模块顶层的文字（如状态名称表）在导入时就会翻译，所以必须在词典就绪之后才导入。
await initI18n()
const { renderApp } = await import('./app')
renderApp()