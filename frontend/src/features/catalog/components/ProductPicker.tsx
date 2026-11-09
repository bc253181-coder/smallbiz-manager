import { useQuery } from '@tanstack/react-query'
import { ScanLine, Search } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Input } from '@/components/ui/form'
import { CameraScanner } from '@/features/barcode/CameraScanner'
import { findByCode, looksLikeCode, vibrate } from '@/features/barcode/scan'
import { errorMessage } from '@/lib/api'
import { useCanManage, useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { productApi } from '../api'
import type { Product } from '../types'
import { ProductThumb } from './ProductThumb'

/**
 * 商品搜索选择器：输入名称 / SKU / 条码，回车或点击选择。
 * 扫码枪：输入像条码的内容后回车，立即按条码精确查找（不等搜索结果）；
 * 摄像头：点右侧扫码图标，可以连续扫多个。扫到已在单里的商品时同样调用 onPick（由页面把数量 +1）。
 * excludeIds 中的商品不出现在搜索下拉里（扫码不受影响）。
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
  const [scanning, setScanning] = useState(false)
  const [looking, setLooking] = useState(false)
  const q = useDebouncedValue(text.trim(), 250)
  const listId = useId()
  const box = useRef<HTMLDivElement>(null)
  const canManage = useCanManage()

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

  function notFound(code: string) {
    toast.error(`没有条码为「${code}」的商品`, {
      action: canManage
        ? { label: '用这个条码新建商品', onClick: () => window.open(`/products/new?barcode=${encodeURIComponent(code)}`, '_blank') }
        : undefined,
    })
  }

  /** 扫码：精确查找。返回是否找到。 */
  async function scan(code: string, fallback?: Product): Promise<boolean> {
    setLooking(true)
    try {
      const p = await findByCode(code)
      if (!p) {
        if (fallback) {
          pick(fallback)
          return true
        }
        // 清空输入框：扫码枪接着扫下一个时不会接在旧内容后面
        setText('')
        setOpen(false)
        notFound(code)
        return false
      }
      if (activeOnly && p.status === 'INACTIVE') {
        toast.error(`「${p.name}」已下架，不能添加`)
        return false
      }
      vibrate()
      pick(p)
      return true
    } catch (e) {
      toast.error(errorMessage(e, '查找商品失败'))
      return false
    } finally {
      setLooking(false)
    }
  }

  return (
    <div ref={box} className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
      <Input
        value={text}
        placeholder={placeholder}
        className="pl-9 pr-11"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-label={placeholder}
        aria-busy={looking || undefined}
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
            const typed = text.trim()
            if (active > 0 && results[active]) {
              // 用方向键选中了下拉里的某一项
              pick(results[active])
            } else if (looksLikeCode(typed)) {
              // 扫码枪或手输条码：精确查找；查不到但下拉里有搜索结果（如英文名称）时选第一项
              const fallback = q === typed ? results[0] : undefined
              void scan(typed, fallback)
            } else if (results[active]) {
              pick(results[active])
            }
          } else if (e.key === 'Escape') {
            setOpen(false)
          }
        }}
      />
      <button
        type="button"
        onClick={() => setScanning(true)}
        className="absolute right-1 top-1/2 flex size-8 -translate-y-1/2 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100 hover:text-slate-800"
        aria-label="用摄像头扫码"
        title="用摄像头扫码"
      >
        <ScanLine className="size-4.5" />
      </button>
      {open && (
        <ul
          id={listId}
          role="listbox"
          className="absolute inset-x-0 top-full z-30 mt-1 max-h-72 overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg"
        >
          {results.length === 0 ? (
            <li className="px-3 py-3 text-sm text-slate-500">
              {isFetching || looking ? '搜索中…' : q ? (looksLikeCode(q) ? '按回车按条码查找' : '没有找到匹配的商品') : '没有可添加的商品'}
            </li>
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
      {scanning && (
        <CameraScanner
          continuous
          title="扫码添加商品"
          onDetected={(code) => void scan(code)}
          onClose={() => setScanning(false)}
        />
      )}
    </div>
  )
}
