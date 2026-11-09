import { X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import type { FormEventHandler, ReactNode } from 'react'
import { Button } from './button'

import { tr } from '@/lib/i18n'
/** 表单弹窗外壳：桌面居中，手机从底部弹出。 */
export function FormDialog({
  open,
  onClose,
  title,
  description,
  submitText,
  saving,
  onSubmit,
  children,
}: {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  submitText: string
  saving?: boolean
  onSubmit: FormEventHandler<HTMLFormElement>
  children: ReactNode
}) {
  return (
    <Dialog.Root open={open} onOpenChange={(o) => !o && !saving && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-slate-900/40" />
        <Dialog.Content
          className="fixed inset-x-0 bottom-0 z-50 max-h-[92dvh] overflow-y-auto rounded-t-2xl bg-white p-5 shadow-xl sm:inset-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-lg sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:p-6"
          aria-describedby={undefined}
        >
          <div className="flex items-start justify-between gap-3">
            <div>
              <Dialog.Title className="text-base font-semibold text-slate-900">{title}</Dialog.Title>
              {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
            </div>
            <Dialog.Close className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label={tr('关闭')} disabled={saving}>
              <X className="size-5" />
            </Dialog.Close>
          </div>
          <form onSubmit={onSubmit} className="mt-5 space-y-4" noValidate>
            {children}
            <div className="flex gap-2 pt-2">
              <Button variant="secondary" className="flex-1" onClick={onClose} disabled={saving}>
                {tr('取消')}
              </Button>
              <Button type="submit" className="flex-1" loading={saving}>
                {submitText}
              </Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
