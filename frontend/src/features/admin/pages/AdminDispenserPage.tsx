import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, Check, Copy, Eye, PackageOpen, Search, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Field, Input } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { useDebouncedValue } from '@/lib/hooks'
import { adminApi, type DispensedCode, type DispenserRow } from '../api'

const STATE: Record<DispenserRow['state'], { label: string; tone: 'slate' | 'blue' | 'green' | 'red' }> = {
  UNUSED: { label: '未发出', tone: 'slate' },
  DISPENSED: { label: '已发出', tone: 'blue' },
  REDEEMED: { label: '已使用', tone: 'green' },
  VOIDED: { label: '已作废', tone: 'red' },
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制，可以发给买家了')
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}

/** 发码台：按小红书订单取出高级永久会员激活码，记录去向（docs/17）。 */
export function AdminDispenserPage() {
  const queryClient = useQueryClient()
  const stock = useQuery({ queryKey: ['admin', 'dispenser', 'stock'], queryFn: adminApi.dispenserStock })
  const [orderRef, setOrderRef] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<{ orderRef: string; codes: DispensedCode[] } | null>(null)
  const [repeat, setRepeat] = useState<number | null>(null)

  const dispense = useMutation({
    mutationFn: (confirmRepeat: boolean) => adminApi.dispense(orderRef.trim(), Number(quantity), confirmRepeat),
    onSuccess: (codes) => {
      setResult({ orderRef: orderRef.trim(), codes })
      setRepeat(null)
      setOrderRef('')
      setQuantity('1')
      void queryClient.invalidateQueries({ queryKey: ['admin', 'dispenser'] })
    },
    onError: (e) => {
      if (errorCode(e) === 'ORDER_ALREADY_DISPENSED') {
        const count = (e as { response?: { data?: { details?: { count?: number } } } }).response?.data?.details?.count ?? 0
        setRepeat(count)
        return
      }
      setError(errorMessage(e, '取码失败'))
    },
  })

  const s = stock.data
  return (
    <div className="space-y-6">
      <PageHeader
        title="发码台"
        description="小红书有新订单时：输入订单号 → 取码 → 复制发给买家。每个码只会被取出一次，系统记录发给了哪个订单"
        actions={
          <Link to="/quick-code" target="_blank" className={buttonVariants({ variant: 'secondary' })}>
            快速发码（不用订单号）
          </Link>
        }
      />

      {s && !s.keyConfigured && (
        <p className="flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          <TriangleAlert className="size-4 shrink-0" aria-hidden />
          发码台需要先在服务器的 deploy/.env 中配置 CODE_ENCRYPTION_KEY（openssl rand -base64 32），配置后新生成的激活码才能在这里取出
        </p>
      )}
      {s?.keyConfigured && s.low && (
        <p className="flex flex-wrap items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800" data-testid="low-stock">
          <TriangleAlert className="size-4 shrink-0" aria-hidden />
          激活码快发完了：还可以发出 {s.available} 个。
          <Link to="/admin/codes" className="font-medium underline">
            生成一批高级永久会员激活码
          </Link>
        </p>
      )}

      <RecentlyUsedCard />

      <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <Card>
          <CardHeader title="取码" description={s ? `还可以发出 ${s.available} 个高级永久会员激活码` : '加载中…'} />
          <CardBody>
            <form
              className="space-y-4"
              noValidate
              onSubmit={(e) => {
                e.preventDefault()
                const q = Number(quantity)
                if (!orderRef.trim()) {
                  setError('请填写小红书订单号')
                  return
                }
                if (!Number.isInteger(q) || q < 1 || q > 20) {
                  setError('数量为 1–20')
                  return
                }
                setError(null)
                dispense.mutate(false)
              }}
            >
              <div className="grid gap-4 sm:grid-cols-[1fr_6rem]">
                <Field label="小红书订单号" htmlFor="d-order" required error={error ?? undefined}>
                  <Input
                    id="d-order"
                    value={orderRef}
                    maxLength={100}
                    autoComplete="off"
                    onChange={(e) => {
                      setOrderRef(e.target.value)
                      setError(null)
                    }}
                    invalid={!!error}
                  />
                </Field>
                <Field label="数量" htmlFor="d-qty">
                  <Input id="d-qty" inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
                </Field>
              </div>
              <Button type="submit" className="w-full" loading={dispense.isPending} disabled={s ? !s.keyConfigured : true}>
                <PackageOpen aria-hidden />
                取码
              </Button>
            </form>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="刚取出的激活码" description={result ? `订单 ${result.orderRef}` : '取码后显示在这里，复制发给买家'} />
          <CardBody>
            {!result ? (
              <p className="text-sm text-slate-500">还没有取码</p>
            ) : (
              <ul className="space-y-2" aria-label="刚取出的激活码">
                {result.codes.map((c) => (
                  <li key={c.id} className="flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2">
                    <span className="min-w-0 flex-1 break-all font-mono text-lg font-semibold tracking-wider text-emerald-900" data-testid="dispensed-code">
                      {c.code}
                    </span>
                    <Button size="sm" variant="secondary" onClick={() => void copy(c.code)}>
                      <Copy aria-hidden />
                      复制
                    </Button>
                  </li>
                ))}
                {result.codes.length > 1 && (
                  <Button size="sm" variant="ghost" onClick={() => void copy(result.codes.map((c) => c.code).join('\n'))}>
                    全部复制
                  </Button>
                )}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>

      <CodesCard />

      <ConfirmDialog
        open={repeat !== null}
        onOpenChange={(o) => !o && setRepeat(null)}
        title="这个订单已经发过码"
        description={`订单「${orderRef.trim()}」已经发过 ${repeat ?? 0} 个码。确定要再发 ${quantity} 个吗？（例如买家买了多份）`}
        confirmText="确定再发"
        loading={dispense.isPending}
        onConfirm={() => dispense.mutate(true)}
      />
    </div>
  )
}

const SEEN_KEY = 'smallbiz.dispenser.lastSeenRedeemedAt'

function readSeen(): string | null {
  try {
    return localStorage.getItem(SEEN_KEY)
  } catch {
    return null
  }
}

/**
 * 最近 7 天被使用（兑换）的激活码。上次点「知道了」之后新被使用的，高亮提示并显示数量。
 * 每分钟自动刷新。
 */
function RecentlyUsedCard() {
  const used = useQuery({
    queryKey: ['admin', 'dispenser', 'redeemed'],
    queryFn: () => adminApi.recentlyRedeemed(7),
    refetchInterval: 60_000,
  })
  const [seen, setSeen] = useState<string | null>(readSeen)
  const rows = used.data ?? []
  const isNew = (r: DispenserRow) => !!r.redeemedAt && (!seen || r.redeemedAt > seen)
  const fresh = rows.filter(isNew)

  function markSeen() {
    const newest = rows[0]?.redeemedAt
    if (!newest) return
    try {
      localStorage.setItem(SEEN_KEY, newest)
    } catch {
      // 浏览器不允许保存时，只在本页生效
    }
    setSeen(newest)
  }

  if (used.isLoading || used.isError) return null
  return (
    <Card className={fresh.length ? 'border-emerald-300 ring-2 ring-emerald-100' : undefined} data-testid="recently-used">
      <CardHeader
        title={
          <span className="inline-flex items-center gap-2">
            <BellRing className={fresh.length ? 'size-4 text-emerald-600' : 'size-4 text-slate-400'} aria-hidden />
            {fresh.length ? `有 ${fresh.length} 个激活码刚被使用` : '最近被使用的激活码'}
          </span>
        }
        description={rows.length ? '最近 7 天被兑换的码（已使用的码不能再发给别人）' : '最近 7 天没有激活码被使用'}
        action={
          fresh.length > 0 && (
            <Button size="sm" variant="secondary" onClick={markSeen}>
              <Check aria-hidden />
              知道了
            </Button>
          )
        }
      />
      {rows.length > 0 && (
        <ul className="divide-y divide-slate-100" aria-label="最近被使用的激活码">
          {rows.slice(0, 10).map((r) => (
            <li key={r.id} className={isNew(r) ? 'flex flex-wrap items-center gap-x-4 gap-y-1 bg-emerald-50/60 px-5 py-2.5 text-sm' : 'flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-2.5 text-sm'}>
              <span className="font-mono font-semibold text-slate-800">****{r.hint}</span>
              {isNew(r) && <Badge tone="green">新</Badge>}
              <span className="text-slate-600">{formatDateTime(r.redeemedAt!)} 被使用</span>
              <span className="min-w-0 break-all text-slate-600">
                账号 {r.redeemedByEmail ?? '—'}
                {r.redeemedTenantName && ` · 店铺 ${r.redeemedTenantName}`}
              </span>
              <span className="text-xs text-slate-400">{r.orderRef ? `订单 ${r.orderRef}` : r.batchName}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function CodesCard() {
  const queryClient = useQueryClient()
  const [q, setQ] = useState('')
  const query = useDebouncedValue(q.trim())
  // 每分钟刷新：买家兑换后状态自动变成「已使用」
  const rows = useQuery({ queryKey: ['admin', 'dispenser', 'codes', query], queryFn: () => adminApi.dispenserCodes(query), refetchInterval: 60_000 })
  const [revealed, setRevealed] = useState<Record<number, string>>({})
  const [voiding, setVoiding] = useState<string | null>(null)

  const reveal = useMutation({
    mutationFn: (id: number) => adminApi.revealCode(id),
    onSuccess: (c) => setRevealed((r) => ({ ...r, [c.id]: c.code })),
    onError: (e) => toast.error(errorMessage(e, '无法显示')),
  })
  const voidOrder = useMutation({
    mutationFn: (order: string) => adminApi.voidOrder(order),
    onSuccess: (r) => {
      setVoiding(null)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'dispenser'] })
      toast.success(
        r.alreadyRedeemed.length
          ? `已作废 ${r.voided} 个；${r.alreadyRedeemed.join('、')} 已经兑换，请到「店铺」页取消该账号的永久会员`
          : `已作废 ${r.voided} 个`,
      )
    },
    onError: (e) => toast.error(errorMessage(e, '作废失败')),
  })

  return (
    <Card>
      <CardHeader
        title="发码记录"
        description="不搜索时显示最近发出的码"
        action={
          <div className="relative w-full sm:w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="订单号或激活码后 4 位" className="pl-9" aria-label="搜索发码记录" />
          </div>
        }
      />
      {rows.isLoading ? (
        <div className="m-5 h-24 animate-pulse rounded-lg bg-slate-100" />
      ) : rows.isError ? (
        <ErrorState onRetry={() => void rows.refetch()} />
      ) : !rows.data?.length ? (
        <p className="p-6 text-center text-sm text-slate-500">没有记录</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-5 py-2">激活码</th>
                <th className="px-5 py-2">状态</th>
                <th className="px-5 py-2">小红书订单</th>
                <th className="px-5 py-2">发出</th>
                <th className="px-5 py-2">使用</th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.data.map((r) => (
                <tr key={r.id}>
                  <td className="px-5 py-2.5">
                    {revealed[r.id] ? (
                      <span className="inline-flex items-center gap-1">
                        <span className={r.state === 'REDEEMED' ? 'font-mono font-semibold text-slate-400 line-through' : 'font-mono font-semibold'}>
                          {revealed[r.id]}
                        </span>
                        {r.state === 'UNUSED' || r.state === 'DISPENSED' ? (
                          <Button size="sm" variant="ghost" onClick={() => void copy(revealed[r.id])} aria-label="复制激活码">
                            <Copy aria-hidden />
                          </Button>
                        ) : null}
                      </span>
                    ) : (
                      <span className="font-mono text-slate-600">****{r.hint}</span>
                    )}
                    <p className="text-xs text-slate-400">{r.batchName}{!r.hasRaw && ' · 旧码（未保存原码）'}</p>
                    {revealed[r.id] && r.state === 'REDEEMED' && (
                      <p className="text-xs font-medium text-red-600" role="alert">这个码已经被使用过，不要再发给别人</p>
                    )}
                    {revealed[r.id] && r.state === 'VOIDED' && (
                      <p className="text-xs font-medium text-red-600" role="alert">这个码已作废，不能使用</p>
                    )}
                  </td>
                  <td className="px-5 py-2.5">
                    <Badge tone={STATE[r.state].tone}>{STATE[r.state].label}</Badge>
                  </td>
                  <td className="px-5 py-2.5 font-mono">{r.orderRef ?? '—'}</td>
                  <td className="whitespace-nowrap px-5 py-2.5 text-slate-600">
                    {r.dispensedAt ? (
                      <>
                        {formatDateTime(r.dispensedAt)}
                        <p className="text-xs text-slate-400">{r.dispensedByName}</p>
                      </>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-5 py-2.5 text-slate-600">
                    {r.redeemedAt ? (
                      <>
                        {formatDateTime(r.redeemedAt)}
                        <p className="break-all text-xs text-slate-400">
                          {r.redeemedByEmail}
                          {r.redeemedTenantName && ` · ${r.redeemedTenantName}`}
                        </p>
                      </>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="space-x-1 whitespace-nowrap px-5 py-2.5 text-right">
                    {r.hasRaw && !revealed[r.id] && (
                      <Button size="sm" variant="ghost" loading={reveal.isPending && reveal.variables === r.id} onClick={() => reveal.mutate(r.id)}>
                        <Eye aria-hidden />
                        显示激活码
                      </Button>
                    )}
                    {r.orderRef && r.state === 'DISPENSED' && (
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => setVoiding(r.orderRef)}>
                        作废
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ConfirmDialog
        open={voiding !== null}
        onOpenChange={(o) => !o && setVoiding(null)}
        title={`作废订单「${voiding ?? ''}」的激活码？`}
        description="这个订单还没兑换的码会立即作废，不能再使用（例如买家退款）。已经兑换的码不受影响，需要到「店铺」页取消该账号的永久会员。"
        confirmText="作废"
        danger
        loading={voidOrder.isPending}
        onConfirm={() => voiding && voidOrder.mutate(voiding)}
      />
    </Card>
  )
}

/** 后台概况页的库存提醒。 */
export function DispenserStockNotice() {
  const { data } = useQuery({ queryKey: ['admin', 'dispenser', 'stock'], queryFn: adminApi.dispenserStock })
  if (!data || !data.keyConfigured || !data.low) return null
  return (
    <div className="mb-6">
      <Link to="/admin/dispenser" className={buttonVariants({ variant: 'secondary', className: 'w-full justify-start border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100' })}>
        <TriangleAlert aria-hidden />
        激活码快发完了：还可以发出 {data.available} 个，去补充
      </Link>
    </div>
  )
}
