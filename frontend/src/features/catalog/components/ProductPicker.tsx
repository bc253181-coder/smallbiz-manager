import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { Input } from '@/components/ui/form'
import { useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { productApi } from '../api'
import type { Product } from '../types'
import { ProductThumb } from './ProductThumb'

/**
 * 商品搜索选择器：输入名称 / SKU / 条码，回车或点击选择。
 * excludeIds 中的商品不再出现在结果里（已添加的行）。
 */
export function ProductPicker({
  onPick,
  excludeIds = [],
  placeholder = '搜索商品名称、SKU 或条码添加商品',
  activeOnly = false,
  renderMeta,
}: {
  onPick: (p: Product) => void
  excludeIds?: number[]
  placeholder?: string
  activeOnly?: boolean
  renderMeta?: (p: Product) => string
}) {
  const [text, setText] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const q = useDebouncedValue(text.trim(), 250)
  const listId = useId()
  const box = useRef<HTMLDivElement>(null)

  const { data, isFetching } = useQuery({
    queryKey: ['products', 'picker', q, activeOnly],
    queryFn: () => productApi.list({ q, size: 10, status: activeOnly ? 'ACTIVE' : undefined, sort: q ? undefined : 'name,asc' }),
    enabled: open,
  })
  const results = (data?.items ?? []).filter((p) => !excludeIds.includes(p.id))

  useEffect(() => setActive(0), [q])
  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  function pick(p: Product) {
    onPick(p)
    setText('')
    setActive(0)
    setOpen(false)
  }

  return (
    <div ref={box} className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
      <Input
        value={text}
        placeholder={placeholder}
        className="pl-9"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-label={placeholder}
        onFocus={() => setOpen(true)}
        onChange={(e) => {
          setText(e.target.value)
          setOpen(true)
        }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault()
            setActive((a) => Math.min(a + 1, results.length - 1))
          } else if (e.key === 'ArrowUp') {
            e.preventDefault()
            setActive((a) => Math.max(a - 1, 0))
          } else if (e.key === 'Enter') {
            e.preventDefault()
            if (results[active]) pick(results[active])
          } else if (e.key === 'Escape') {
            setOpen(false)
          }
        }}
      />
      {open && (
        <ul
          id={listId}
          role="listbox"
          className="absolute inset-x-0 top-full z-30 mt-1 max-h-72 overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
        >
          {results.length === 0 ? (
            <li className="px-3 py-3 text-sm text-slate-500">{isFetching ? '搜索中…' : q ? '没有找到匹配的商品' : '没有可添加的商品'}</li>
          ) : (
            results.map((p, i) => (
              <li key={p.id} role="option" aria-selected={i === active}>
                <button
                  type="button"
                  onMouseEnter={() => setActive(i)}
                  onClick={() => pick(p)}
                  className={cn('flex w-full items-center gap-3 px-3 py-2 text-left', i === active && 'bg-blue-50')}
                >
                  <ProductThumb url={p.imageUrl} name={p.name} className="size-8" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-slate-900">{p.name}</span>
                    <span className="block truncate text-xs text-slate-500">
                      {p.sku} · 库存 {p.stockQty} {p.unit}
                      {renderMeta ? ` · ${renderMeta(p)}` : ''}
                      {p.status === 'INACTIVE' && ' · 已下架'}
                    </span>
                  </span>
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}
