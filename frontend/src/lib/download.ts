import { AxiosError } from 'axios'
import { api } from './api'
import { getLang } from './i18n'

/**
 * 下载接口返回的文件（CSV / Excel），文件名取服务器的 Content-Disposition。
 * 出错时服务器返回的是 JSON，但以 Blob 形式收到：这里转回对象，errorMessage() 才能显示具体原因。
 */
export async function downloadFile(path: string, params: Record<string, string | number | boolean | undefined>, fallbackName: string) {
  // lang：英文界面下载的表格表头、文件名也是英文
  const clean = Object.fromEntries(
    Object.entries({ ...params, lang: getLang() === 'en' ? 'en' : undefined }).filter(([, v]) => v !== undefined && v !== ''),
  )
  try {
    const res = await api.get<Blob>(path, { params: clean, responseType: 'blob' })
    const disposition = String(res.headers['content-disposition'] ?? '')
    const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    const name = match ? decodeURIComponent(match[1]) : fallbackName
    saveBlob(res.data, name)
  } catch (e) {
    if (e instanceof AxiosError && e.response?.data instanceof Blob) {
      try {
        e.response.data = JSON.parse(await e.response.data.text())
      } catch {
        // 不是 JSON：保留原样
      }
    }
    throw e
  }
}

export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
