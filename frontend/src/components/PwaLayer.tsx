import { useQueryClient } from '@tanstack/react-query'
import { Download, Share, WifiOff, X } from 'lucide-react'
import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { toast } from 'sonner'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/AuthProvider'
import { tr } from '@/lib/i18n'
import { canPromptInstall, dismissInstall, installDismissedRecently, isIosSafari, isStandalone, onInstallChange, promptInstall } from '@/lib/install'

/** 网络状态。 */
function useOnline(): boolean {
  return useSyncExternalStore(
    (cb) => {
      window.addEventListener('online', cb)
      window.addEventListener('offline', cb)
      return () => {
        window.removeEventListener('online', cb)
        window.removeEventListener('offline', cb)
      }
    },
    () => navigator.onLine,
    () => true,
  )
}

/** 能否一键安装（安卓）：随 beforeinstallprompt 事件变化。 */
export function useCanInstall(): boolean {
  return useSyncExternalStore(onInstallChange, canPromptInstall, () => false)
}

/**
 * PWA 相关的全局提示：断网横幅（恢复后自动刷新数据）、新版本提示、手机上的「添加到主屏幕」引导。
 */
export function PwaLayer() {
  const queryClient = useQueryClient()
  const online = useOnline()
  const wasOffline = useRef(false)

  useEffect(() => {
    if (!online) {
      wasOffline.current = true
    } else if (wasOffline.current) {
      wasOffline.current = false
      void queryClient.invalidateQueries()
      toast.success(tr('网络已恢复'), { id: 'network' })
    }
  }, [online, queryClient])

  const {
    needRefresh: [needRefresh],
    updateServiceWorker,
  } = useRegisterSW({ immediate: true })
  useEffect(() => {
    if (needRefresh) {
      toast(tr('有新版本可用'), {
        id: 'sw-update',
        duration: Infinity,
        action: { label: tr('点击更新'), onClick: () => void updateServiceWorker(true) },
      })
    }
  }, [needRefresh, updateServiceWorker])

  return (
    <>
      {!online && (
        <div
          role="status"
          className="fixed inset-x-0 top-0 z-[60] flex items-center justify-center gap-2 bg-amber-500 px-4 py-1.5 text-center text-sm font-medium text-white shadow"
        >
          <WifiOff className="size-4 shrink-0" aria-hidden />
          {tr('网络已断开，恢复后会自动刷新数据')}
        </div>
      )}
      <InstallBanner />
    </>
  )
}

/** 手机上（已登录、还没安装）提示一次添加到主屏幕；关闭后 30 天内不再提示。 */
function InstallBanner() {
  const { session } = useAuth()
  const canInstall = useCanInstall()
  const [hidden, setHidden] = useState(() => installDismissedRecently())
  const narrow = typeof window !== 'undefined' && window.matchMedia?.('(max-width: 767px)').matches
  const ios = isIosSafari()
  if (hidden || !session || !narrow || isStandalone() || (!canInstall && !ios)) return null

  function close() {
    dismissInstall()
    setHidden(true)
  }

  return (
    <div className="fixed inset-x-3 top-3 z-50 rounded-xl border border-blue-200 bg-white p-3 shadow-lg" role="dialog" aria-label={tr('添加到主屏幕')}>
      <div className="flex items-start gap-3">
        <img src="/icons/icon-192.png" alt="" className="size-10 shrink-0 rounded-lg" />
        <div className="min-w-0 flex-1 text-sm">
          <p className="font-medium text-slate-900">{tr('把 SmallBiz 添加到主屏幕')}</p>
          {ios ? (
            <p className="mt-0.5 text-slate-600">
              {tr('点 Safari 底部的')} <Share className="inline size-4 align-text-bottom" aria-label={tr('分享')} />{' '}
              {tr('，再选「添加到主屏幕」')}
            </p>
          ) : (
            <p className="mt-0.5 text-slate-600">{tr('像 App 一样从桌面打开，更快、全屏')}</p>
          )}
          {!ios && (
            <Button
              size="sm"
              className="mt-2"
              onClick={async () => {
                const ok = await promptInstall()
                if (ok) close()
              }}
            >
              <Download aria-hidden />
              {tr('安装')}
            </Button>
          )}
        </div>
        <button type="button" onClick={close} className="rounded p-1 text-slate-400 hover:bg-slate-100" aria-label={tr('不再提示')}>
          <X className="size-4" />
        </button>
      </div>
    </div>
  )
}
