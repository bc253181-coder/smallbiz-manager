import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './button'

export function Pagination({
  page,
  totalPages,
  totalItems,
  unit = '条',
  onChange,
}: {
  page: number
  totalPages: number
  totalItems: number
  unit?: string
  onChange: (page: number) => void
}) {
  if (totalItems === 0) return null
  return (
    <div className="flex items-center justify-between gap-3 text-sm text-slate-600">
      <span>
        共 {totalItems} {unit}
        {totalPages > 1 && (
          <span className="hidden sm:inline">
            ，第 {page + 1} / {totalPages} 页
          </span>
        )}
      </span>
      {totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
            <ChevronLeft aria-hidden />
            上一页
          </Button>
          <Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
            下一页
            <ChevronRight aria-hidden />
          </Button>
        </div>
      )}
    </div>
  )
}
