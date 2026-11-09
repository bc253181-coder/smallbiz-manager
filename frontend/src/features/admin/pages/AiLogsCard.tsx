import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Flag, ThumbsDown, ThumbsUp } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { ErrorState } from '@/components/ui/page'
import { errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { adminApi, type AiUsage } from '../api'

const STATUS: Record<string, { label: string; tone: 'green' | 'amber' | 'red' }> = {
  ANSWERED: { label: '已回答', tone: 'green' },
  HANDOFF: { label: '转人工', tone: 'amber' },
  INVALID: { label: '未通过校验', tone: 'red' },
}

function usd(u: AiUsage) {
  return `$${Number(u.estimatedUsd).toFixed(4)}`
}

/** 平台后台：AI 客服用量、费用估算和回答记录（可标记「回答有误」，据此补充话术）。 */
export function AiLogsCard() {
  const queryClient = useQueryClient()
  const [flaggedOnly, setFlaggedOnly] = useState(false)
  const [page, setPage] = useState(0)
  const stats = useQuery({ queryKey: ['admin', 'ai', 'stats'], queryFn: adminApi.aiStats })
  const logs = useQuery({ queryKey: ['admin', 'ai', 'logs', flaggedOnly, page], queryFn: () => adminApi.aiLogs(flaggedOnly, page) })

  const flag = useMutation({
    mutationFn: ({ id, flagged }: { id: number; flagged: boolean }) => adminApi.flagAiLog(id, flagged),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'ai', 'logs'] }),
    onError: (e) => toast.error(errorMessage(e, '操作失败')),
  })

  const s = stats.data
  return (
    <Card>
      <CardHeader
        title="AI 回答记录"
        description={
          s
            ? s.enabled
              ? `模型 ${s.model} · 今日 ${s.today.calls} / ${s.dailyLimit} 次（约 ${usd(s.today)}）· 本月 ${s.month.calls} 次（约 ${usd(s.month)}）· 每个 IP 每天最多 ${s.perIpDailyLimit} 次`
              : '未配置 ANTHROPIC_API_KEY：AI 已关闭，客服只用关键词匹配'
            : '加载中…'
        }
      />
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 px-5 py-2">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="size-4 accent-blue-600"
            checked={flaggedOnly}
            onChange={(e) => {
              setFlaggedOnly(e.target.checked)
              setPage(0)
            }}
          />
          只看标记有误的
        </label>
        <span className="text-xs text-slate-500">记录保存 90 天，不包含用户和店铺信息</span>
      </div>
      {logs.isLoading ? (
        <div className="m-5 h-24 animate-pulse rounded-lg bg-slate-100" />
      ) : logs.isError ? (
        <ErrorState onRetry={() => void logs.refetch()} />
      ) : !logs.data?.items.length ? (
        <p className="p-5 text-sm text-slate-500">暂无</p>
      ) : (
        <>
          <ul className="divide-y divide-slate-100">
            {logs.data.items.map((l) => (
              <li key={l.id} className={cn('space-y-1.5 px-5 py-3', l.flagged && 'bg-red-50/50')}>
                <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                  <Badge tone={STATUS[l.status]?.tone}>{STATUS[l.status]?.label ?? l.status}</Badge>
                  <span>{formatDateTime(l.createdAt)}</span>
                  <span>{l.lang === 'en' ? '英文' : '中文'}</span>
                  {l.faqIds.length > 0 && <span>引用话术 #{l.faqIds.join(', #')}</span>}
                  <span className="tabular-nums">
                    {l.inputTokens + l.cacheReadTokens} / {l.outputTokens} token · {(l.latencyMs / 1000).toFixed(1)} 秒
                  </span>
                  {l.helpful === true && <ThumbsUp className="size-3.5 text-emerald-600" aria-label="用户评价有帮助" />}
                  {l.helpful === false && <ThumbsDown className="size-3.5 text-red-600" aria-label="用户评价没帮助" />}
                </div>
                <p className="break-words text-sm font-medium text-slate-900">{l.question}</p>
                {l.answer && <p className="whitespace-pre-line break-words text-sm text-slate-600">{l.answer}</p>}
                <Button
                  size="sm"
                  variant="ghost"
                  className={l.flagged ? 'text-red-600' : ''}
                  loading={flag.isPending && flag.variables?.id === l.id}
                  onClick={() => flag.mutate({ id: l.id, flagged: !l.flagged })}
                >
                  <Flag aria-hidden />
                  {l.flagged ? '取消标记' : '标记回答有误'}
                </Button>
              </li>
            ))}
          </ul>
          {logs.data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-2 border-t border-slate-100 px-5 py-2 text-sm">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                上一页
              </Button>
              <span className="tabular-nums text-slate-500">
                {page + 1} / {logs.data.totalPages}
              </span>
              <Button size="sm" variant="secondary" disabled={page + 1 >= logs.data.totalPages} onClick={() => setPage((p) => p + 1)}>
                下一页
              </Button>
            </div>
          )}
        </>
      )}
    </Card>
  )
}
