import { ScanLine } from 'lucide-react'
import { useState } from 'react'
import { CameraScanner } from './CameraScanner'

/** 放在搜索框右侧的扫码图标：用摄像头扫一个码，交给 onCode 处理。搜索框需要留出右内边距（pr-11）。 */
export function CameraScanButton({ onCode, title = '扫码搜索' }: { onCode: (code: string) => void; title?: string }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="absolute right-1 top-1/2 flex size-8 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-800"
        aria-label="用摄像头扫码"
        title="用摄像头扫码"
      >
        <ScanLine className="size-4.5" />
      </button>
      {open && <CameraScanner title={title} onDetected={onCode} onClose={() => setOpen(false)} />}
    </>
  )
}
