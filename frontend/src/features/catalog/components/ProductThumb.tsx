import { Package } from 'lucide-react'
import { cn } from '@/lib/utils'

export function ProductThumb({ url, name, className }: { url: string | null; name: string; className?: string }) {
  return (
    <span
      className={cn(
        'flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50 text-slate-300',
        className,
      )}
    >
      {url ? (
        <img src={url} alt={name} className="size-full object-cover" loading="lazy" />
      ) : (
        <Package className="size-1/2" aria-hidden />
      )}
    </span>
  )
}
