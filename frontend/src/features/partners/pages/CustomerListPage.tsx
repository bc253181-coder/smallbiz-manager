import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Contact, Plus, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { ExcelExportButton } from '@/features/excel/ExcelExportButton'
import { ImportButton } from '@/features/excel/ImportDialog'
import { useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { CUSTOMER_TYPE_LABELS, PRICE_TIER_LABELS, customerApi, type CustomerType, type PriceTier } from '../api'
import { CustomerFormDialog } from '../CustomerFormDialog'

import { tr } from '@/lib/i18n'
export function CustomerListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const q = params.get('q') ?? ''
  const type = (params.get('type') as CustomerType | null) ?? undefined
  const priceTier = (params.get('priceTier') as PriceTier | null) ?? undefined
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

  const filtered = !!(q || type || priceTier)
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['customers', { q, type, priceTier, page }],
    queryFn: () => customerApi.list({ q, type, priceTier, page, size: 20 }),
    placeholderData: keepPreviousData,
  })

  return (
    <div>
      <PageHeader
        title={tr('客户')}
        description={tr('管理客户资料和默认价格（零售 / 批发）')}
        actions={
          <>
            <ImportButton type="customers" />
            <ExcelExportButton path="/exports/customers" params={{ q, type, priceTier }} fallbackName={tr('客户.xlsx')} />
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              {tr('新增客户')}
            </Button>
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder={tr('搜索名称、电话或邮箱')} className="pl-9" aria-label={tr('搜索客户')} />
          </div>
          <div className="grid grid-cols-2 gap-2 sm:flex">
            <Select aria-label={tr('客户类型')} value={type ?? ''} onChange={(e) => update({ type: e.target.value || null })} className="sm:w-36">
              <option value="">{tr('全部类型')}</option>
              {(Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[]).map((k) => (
                <option key={k} value={k}>
                  {CUSTOMER_TYPE_LABELS[k]}
                </option>
              ))}
            </Select>
            <Select aria-label={tr('默认价格')} value={priceTier ?? ''} onChange={(e) => update({ priceTier: e.target.value || null })} className="sm:w-36">
              <option value="">{tr('全部价格')}</option>
              {(Object.keys(PRICE_TIER_LABELS) as PriceTier[]).map((k) => (
                <option key={k} value={k}>
                  {PRICE_TIER_LABELS[k]}{tr('客户')}
                </option>
              ))}
            </Select>
          </div>
        </div>

        {isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : data && data.items.length === 0 ? (
          filtered ? (
            <EmptyState icon={Search} title={tr('没有符合条件的客户')} description={tr('换个关键词或筛选条件试试')} />
          ) : (
            <EmptyState
              icon={Contact}
              title={tr('还没有客户')}
              description={tr('添加常来往的客户，开单时可以直接选择，并自动带出零售价或批发价')}
              action={
                <Button onClick={() => setCreating(true)}>
                  <Plus aria-hidden />
                  {tr('新增客户')}
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
                    <th className="px-4 py-3">{tr('客户')}</th>
                    <th className="px-4 py-3">{tr('电话')}</th>
                    <th className="px-4 py-3">{tr('类型')}</th>
                    <th className="px-4 py-3">{tr('默认价格')}</th>
                    <th className="hidden px-4 py-3 lg:table-cell">{tr('地址')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.items.map((c) => (
                    <tr key={c.id} onClick={() => navigate(`/customers/${c.id}`)} className="cursor-pointer hover:bg-slate-50">
                      <td className="px-4 py-3">
                        <Link to={`/customers/${c.id}`} onClick={(e) => e.stopPropagation()} className="font-medium text-slate-900 hover:text-blue-600">
                          {c.name}
                        </Link>
                        {c.email && <p className="text-xs text-slate-500">{c.email}</p>}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{c.phone ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{CUSTOMER_TYPE_LABELS[c.customerType]}</td>
                      <td className="px-4 py-3">
                        <Badge tone={c.priceTier === 'WHOLESALE' ? 'blue' : 'slate'}>{PRICE_TIER_LABELS[c.priceTier]}</Badge>
                      </td>
                      <td className="hidden max-w-xs truncate px-4 py-3 text-slate-600 lg:table-cell">{c.address ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <ul className="divide-y divide-slate-100 md:hidden">
                {data.items.map((c) => (
                  <li key={c.id}>
                    <Link to={`/customers/${c.id}`} className="flex items-center justify-between gap-3 p-4 active:bg-slate-50">
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-900">{c.name}</p>
                        <p className="truncate text-xs text-slate-500">
                          {CUSTOMER_TYPE_LABELS[c.customerType]}
                          {c.phone && ` · ${c.phone}`}
                        </p>
                      </div>
                      <Badge tone={c.priceTier === 'WHOLESALE' ? 'blue' : 'slate'}>{PRICE_TIER_LABELS[c.priceTier]}</Badge>
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} unit={tr('位客户')} onChange={(p) => update({ page: String(p) }, false)} />
              </div>
            </div>
          )
        )}
      </Card>

      <CustomerFormDialog open={creating} onClose={() => setCreating(false)} onSaved={(c) => navigate(`/customers/${c.id}`)} />
    </div>
  )
}
