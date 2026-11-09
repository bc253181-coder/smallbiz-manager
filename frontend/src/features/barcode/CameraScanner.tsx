import { Camera, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import { tr } from '@/lib/i18n'
/** 浏览器自带的条码识别（安卓 Chrome 等支持）；TypeScript 还没有内置类型。 */
interface NativeDetector {
  detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]>
}
declare global {
  interface Window {
    BarcodeDetector?: {
      new (opts?: { formats: string[] }): NativeDetector
      getSupportedFormats(): Promise<string[]>
    }
  }
}

const WANTED = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39', 'qr_code']

/**
 * 摄像头扫码弹窗。continuous = true 时可以连续扫多个（同一个码 2 秒内不重复触发）。
 * 优先用浏览器自带识别，不支持时（iPhone Safari）才加载 ZXing（只在打开扫码时下载）。
 */
export function CameraScanner({
  onDetected,
  onClose,
  continuous = false,
  title = tr('扫码'),
}: {
  onDetected: (code: string) => void
  onClose: () => void
  continuous?: boolean
  title?: string
}) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [last, setLast] = useState<string | null>(null)
  const [starting, setStarting] = useState(true)
  // 回调放在 ref 里，避免每次渲染重启摄像头
  const onDetectedRef = useRef(onDetected)
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onDetectedRef.current = onDetected
    onCloseRef.current = onClose
  })

  useEffect(() => {
    let stopped = false
    let stop: () => void = () => {}
    const seen = new Map<string, number>()

    function emit(code: string) {
      const now = Date.now()
      if ((seen.get(code) ?? 0) > now - 2000) return
      seen.set(code, now)
      setLast(code)
      onDetectedRef.current(code)
      if (!continuous) {
        stopped = true
        stop()
        onCloseRef.current()
      }
    }

    async function start() {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
        setError(tr('当前网页不能使用摄像头（需要 HTTPS 访问）。可以用扫码枪，或手动输入条码。'))
        setStarting(false)
        return
      }
      const video = videoRef.current!
      try {
        const native = window.BarcodeDetector
        const formats = native ? (await native.getSupportedFormats()).filter((f) => WANTED.includes(f)) : []
        if (native && formats.length > 0) {
          const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
          if (stopped) {
            stream.getTracks().forEach((t) => t.stop())
            return
          }
          video.srcObject = stream
          await video.play()
          const detector = new native({ formats })
          const timer = window.setInterval(async () => {
            if (stopped || video.readyState < 2) return
            try {
              const found = await detector.detect(video)
              if (found[0]?.rawValue) emit(found[0].rawValue)
            } catch {
              // 单帧识别失败忽略
            }
          }, 200)
          stop = () => {
            window.clearInterval(timer)
            stream.getTracks().forEach((t) => t.stop())
          }
        } else {
          const { BrowserMultiFormatReader } = await import('@zxing/browser')
          if (stopped) return
          const reader = new BrowserMultiFormatReader()
          const controls = await reader.decodeFromConstraints(
            { video: { facingMode: 'environment' }, audio: false },
            video,
            (result) => {
              if (result && !stopped) emit(result.getText())
            },
          )
          stop = () => controls.stop()
          if (stopped) stop()
        }
      } catch (e) {
        const name = e instanceof DOMException ? e.name : ''
        setError(
          name === 'NotAllowedError'
            ? tr('没有摄像头权限。请在浏览器设置中允许本网站使用摄像头。')
            : name === 'NotFoundError'
              ? tr('没有找到摄像头。可以用扫码枪，或手动输入条码。')
              : tr('摄像头启动失败，请关闭其他正在使用摄像头的应用后重试。'),
        )
      } finally {
        setStarting(false)
      }
    }

    void start()
    return () => {
      stopped = true
      stop()
    }
  }, [continuous])

  return (
    <Dialog.Root open onOpenChange={(o) => !o && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-slate-900/60" />
        <Dialog.Content
          className="fixed inset-x-0 bottom-0 z-50 rounded-t-2xl bg-white p-4 shadow-xl sm:inset-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-md sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl"
          aria-describedby={undefined}
        >
          <div className="mb-3 flex items-center justify-between">
            <Dialog.Title className="flex items-center gap-2 text-base font-semibold text-slate-900">
              <Camera className="size-4.5 text-slate-500" aria-hidden />
              {title}
            </Dialog.Title>
            <Dialog.Close className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label={tr('关闭')}>
              <X className="size-5" />
            </Dialog.Close>
          </div>
          {error ? (
            <p className="rounded-lg bg-amber-50 p-4 text-sm text-amber-900">{error}</p>
          ) : (
            <div className="relative aspect-[4/3] overflow-hidden rounded-lg bg-slate-900">
              <video ref={videoRef} className="size-full object-cover" playsInline muted />
              {/* 取景框 */}
              <div className="pointer-events-none absolute inset-x-8 top-1/2 h-24 -translate-y-1/2 rounded-lg border-2 border-white/80" />
              {starting && <p className="absolute inset-0 flex items-center justify-center text-sm text-white/80">{tr('正在打开摄像头…')}</p>}
            </div>
          )}
          <p className={cn('mt-3 text-center text-sm', last ? 'text-emerald-700' : 'text-slate-500')} role="status">
            {last ? tr('已扫到：{{last}}{{v1}}', { last: last, v1: continuous ? tr('，可以继续扫下一个') : '' }) : tr('把条码放进框里，保持稳定')}
          </p>
          {continuous && (
            <Button variant="secondary" className="mt-3 w-full" onClick={onClose}>
              {tr('完成')}
            </Button>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
