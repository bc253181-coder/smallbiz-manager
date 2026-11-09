/**
 * 安装到主屏幕（PWA）。安卓 Chrome / Edge 会发出 beforeinstallprompt，可以一键安装；
 * iPhone Safari 不支持，只能提示用户「分享 → 添加到主屏幕」。
 * 事件可能在页面组件挂载前就发出，所以在模块加载时就开始监听。
 */
interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

let deferred: InstallPromptEvent | null = null
const listeners = new Set<() => void>()

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault() // 由我们自己的提示来触发
    deferred = e as InstallPromptEvent
    listeners.forEach((l) => l())
  })
  window.addEventListener('appinstalled', () => {
    deferred = null
    listeners.forEach((l) => l())
  })
}

export function onInstallChange(l: () => void): () => void {
  listeners.add(l)
  return () => listeners.delete(l)
}

export function canPromptInstall(): boolean {
  return deferred !== null
}

/** 弹出系统的安装确认框；返回是否已安装。 */
export async function promptInstall(): Promise<boolean> {
  if (!deferred) return false
  const e = deferred
  deferred = null
  await e.prompt()
  const { outcome } = await e.userChoice
  listeners.forEach((l) => l())
  return outcome === 'accepted'
}

/** 已经以 App 方式打开（从主屏幕图标启动）。 */
export function isStandalone(): boolean {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches === true ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

/** iPhone / iPad 上的 Safari（只能手动添加到主屏幕）。 */
export function isIosSafari(): boolean {
  const ua = navigator.userAgent
  const ios = /iPhone|iPad|iPod/i.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)
  return ios && /Safari/i.test(ua) && !/CriOS|FxiOS|EdgiOS/i.test(ua)
}

const DISMISS_KEY = 'sb:installDismissedAt'
const DISMISS_DAYS = 30

export function installDismissedRecently(): boolean {
  try {
    const at = Number(localStorage.getItem(DISMISS_KEY) ?? 0)
    return Date.now() - at < DISMISS_DAYS * 24 * 3600 * 1000
  } catch {
    return false
  }
}

export function dismissInstall() {
  try {
    localStorage.setItem(DISMISS_KEY, String(Date.now()))
  } catch {
    // 忽略
  }
}
