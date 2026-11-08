import { Store } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Brand({ className }: { className?: string }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-white">
        <Store className="size-4.5" aria-hidden />
      </span>
      <span className="text-[15px] font-semibold tracking-tight text-slate-900">SmallBiz Manager</span>
    </div>
  )
}
