import { cn } from '@/lib/utils'

export function Brand({ className }: { className?: string }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <img src="/favicon.svg" alt="" className="size-8 shrink-0" width={32} height={32} />
      <span className="text-[15px] font-semibold tracking-tight text-slate-900">SmallBiz Manager</span>
    </div>
  )
}
