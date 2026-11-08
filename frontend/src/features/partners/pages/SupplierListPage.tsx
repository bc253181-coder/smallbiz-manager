import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Factory, Plus, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { supplierApi } from '../api'
import { SupplierFormDialog } from '../SupplierFormDialog'

export function SupplierListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const q = params.get('q') ?? ''
  const page = Number(params.get('page') ?? 0)
  const [creating, setCreating] = useState(false)

  const [search, setSearch] = useState(q)
  const debounced = useDebouncedValue(search)
  useEffect(() => {
    if (debounced !== q) update({ q: debounced || null })
  }, [debounced]) // eslint-disable-line react-hooks/exhaustive-deps

  function update(changes: Record<string, string | null>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['suppliers', { q, page }],
    queryFn: () => supplierApi.list({ q, page, size: 20 }),
    placeholderData: keepPreviousData,
  })

  return (
    <div>
      <PageHeader
        title="供应商"
        description="管理进货渠道和联系方式"
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden />
            新增供应商
          </Button>
        }
      />
      <Card>
        <div className="border-b border-slate-100 p-4">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索名称、联系人、电话或供应商品"
              className="pl-9"
              aria-label="搜索供应商"
            />
          </div>
        </div>

        {isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : data && data.items.length === 0 ? (
          q ? (
            <EmptyState icon={Search} title="没有符合条件的供应商" />
          ) : (
            <EmptyState
              icon={Factory}
              title="还没有供应商"
              description="添加供应商后，采购时可以直接选择，并查看每个供应商的进货记录"
              action={
                <Button onClick={() => setCreating(true)}>
                  <Plus aria-hidden />
                  新增供应商
                </Button>
              }
            />
          )
        ) : (
          data && (
            <div className={cn(isFetching && 'opacity-60')}>
              <table className="hidden w-full text-sm md:table">
                <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                  <tr>
                    <th className="px-4 py-3">供应商</th>
                    <th className="px-4 py-3">联系人</th>
                    <th className="px-4 py-3">电话</th>
                    <th className="px-4 py-3">供应商品</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.items.map((s) => (
                    <tr key={s.id} onClick={() => navigate(`/suppliers/${s.id}`)} className="cursor-pointer hover:bg-slate-50">
                      <td className="px-4 py-3">
                        <Link to={`/suppliers/${s.id}`} onClick={(e) => e.stopPropagation()} className="font-medium text-slate-900 hover:text-blue-600">
                          {s.name}
                        </Link>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{s.contactPerson ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{s.phone ?? '—'}</td>
                      <td className="max-w-xs truncate px-4 py-3 text-slate-600">{s.suppliedProductsNote ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <ul className="divide-y divide-slate-100 md:hidden">
                {data.items.map((s) => (
                  <li key={s.id}>
                    <Link to={`/suppliers/${s.id}`} className="block p-4 active:bg-slate-50">
                      <p className="truncate font-medium text-slate-900">{s.name}</p>
                      <p className="truncate text-xs text-slate-500">
                        {[s.contactPerson, s.phone].filter(Boolean).join(' · ') || '未填写联系方式'}
                      </p>
                      {s.suppliedProductsNote && <p className="mt-0.5 truncate text-xs text-slate-500">供应：{s.suppliedProductsNote}</p>}
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} unit="个供应商" onChange={(p) => update({ page: String(p) }, false)} />
              </div>
            </div>
          )
        )}
      </Card>

      <SupplierFormDialog open={creating} onClose={() => setCreating(false)} onSaved={(s) => navigate(`/suppliers/${s.id}`)} />
    </div>
  )
}
