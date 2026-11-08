import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { FormDialog } from '@/components/ui/form-dialog'
import { Field, Input, Textarea } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { errorMessage, fieldErrors } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { adminApi, type FaqInput, type FaqRow } from '../api'

const EMPTY: FaqInput = {
  category: '',
  question: '',
  keywords: '',
  answer: '',
  actionLabel: '',
  actionPath: '',
  quickReply: false,
  sortOrder: 100,
  enabled: true,
}

export function AdminSupportPage() {
  const queryClient = useQueryClient()
  const faqs = useQuery({ queryKey: ['admin', 'faqs'], queryFn: adminApi.faqs })
  const unmatched = useQuery({ queryKey: ['admin', 'unmatched'], queryFn: adminApi.unmatched })
  const [editing, setEditing] = useState<{ id: number | null; values: FaqInput; fromUnmatched?: number } | null>(null)
  const [deleting, setDeleting] = useState<FaqRow | null>(null)

  const resolve = useMutation({
    mutationFn: adminApi.resolveUnmatched,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'unmatched'] }),
  })
  const remove = useMutation({
    mutationFn: (id: number) => adminApi.deleteFaq(id),
    onSuccess: () => {
      setDeleting(null)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'faqs'] })
      toast.success('已删除')
    },
    onError: (e) => toast.error(errorMessage(e, '删除失败')),
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title="客服话术"
        description="客服按关键词匹配回答（关键词越长权重越高）；答不上来的问题会转人工微信，并记录在下方"
        actions={
          <Button onClick={() => setEditing({ id: null, values: EMPTY })}>
            <Plus aria-hidden />
            新增话术
          </Button>
        }
      />

      <Card>
        <CardHeader title="未能回答的问题" description="可据此补充话术；处理后点「已处理」" />
        {unmatched.isLoading ? (
          <div className="m-5 h-20 animate-pulse rounded-lg bg-slate-100" />
        ) : unmatched.isError ? (
          <ErrorState onRetry={() => void unmatched.refetch()} />
        ) : !unmatched.data?.length ? (
          <p className="p-5 text-sm text-slate-500">暂无</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {unmatched.data.map((u) => (
              <li key={u.id} className="flex flex-col gap-2 px-5 py-3 sm:flex-row sm:items-center">
                <div className="min-w-0 flex-1">
                  <p className="break-words text-sm text-slate-800">{u.queryText}</p>
                  <p className="text-xs text-slate-500">
                    {u.occurrences} 次 · 最近 {formatDateTime(u.lastSeenAt)}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button size="sm" variant="secondary" onClick={() => setEditing({ id: null, values: { ...EMPTY, question: u.queryText }, fromUnmatched: u.id })}>
                    添加话术
                  </Button>
                  <Button size="sm" variant="ghost" loading={resolve.isPending && resolve.variables === u.id} onClick={() => resolve.mutate(u.id)}>
                    已处理
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <CardHeader title={faqs.data ? `话术（${faqs.data.length}）` : '话术'} />
        {faqs.isLoading ? (
          <div className="m-5 h-40 animate-pulse rounded-lg bg-slate-100" />
        ) : faqs.isError ? (
          <ErrorState onRetry={() => void faqs.refetch()} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                <tr>
                  <th className="px-5 py-2">分类</th>
                  <th className="px-5 py-2">问题 / 关键词</th>
                  <th className="px-5 py-2 text-right">有用 / 没用</th>
                  <th className="px-5 py-2">状态</th>
                  <th className="px-5 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {faqs.data?.map((f) => (
                  <tr key={f.id} className={f.enabled ? '' : 'text-slate-400'}>
                    <td className="whitespace-nowrap px-5 py-2.5">{f.category}</td>
                    <td className="px-5 py-2.5">
                      <p className="font-medium">{f.question}</p>
                      <p className="text-xs text-slate-500">{f.keywords}</p>
                    </td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-right tabular-nums">
                      {f.helpfulCount} / {f.unhelpfulCount}
                    </td>
                    <td className="space-x-1 whitespace-nowrap px-5 py-2.5">
                      {!f.enabled && <Badge>停用</Badge>}
                      {f.quickReply && <Badge tone="blue">快捷问题</Badge>}
                    </td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-right">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() =>
                          setEditing({
                            id: f.id,
                            values: { ...f, actionLabel: f.actionLabel ?? '', actionPath: f.actionPath ?? '' },
                          })
                        }
                      >
                        编辑
                      </Button>
                      <Button size="sm" variant="ghost" className="text-red-600" onClick={() => setDeleting(f)}>
                        删除
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {editing && (
        <FaqDialog
          id={editing.id}
          initial={editing.values}
          onClose={() => setEditing(null)}
          onSaved={() => {
            if (editing.fromUnmatched) resolve.mutate(editing.fromUnmatched)
            setEditing(null)
          }}
        />
      )}
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        title={`删除话术「${deleting?.question ?? ''}」？`}
        description="删除后不可恢复。如只是暂时不用，可以编辑并取消「启用」。"
        confirmText="删除"
        danger
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
      />
    </div>
  )
}

function FaqDialog({
  id,
  initial,
  onClose,
  onSaved,
}: {
  id: number | null
  initial: FaqInput
  onClose: () => void
  onSaved: () => void
}) {
  const queryClient = useQueryClient()
  const [v, setV] = useState<FaqInput>(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const set = <K extends keyof FaqInput>(k: K, value: FaqInput[K]) => setV((x) => ({ ...x, [k]: value }))

  const save = useMutation({
    mutationFn: () => {
      const body = { ...v, actionLabel: v.actionLabel?.trim() || null, actionPath: v.actionPath?.trim() || null }
      return id ? adminApi.updateFaq(id, body) : adminApi.createFaq(body)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'faqs'] })
      void queryClient.invalidateQueries({ queryKey: ['support'] })
      toast.success('已保存')
      onSaved()
    },
    onError: (e) => {
      setErrors(fieldErrors(e))
      toast.error(errorMessage(e, '保存失败'))
    },
  })

  return (
    <FormDialog
      open
      onClose={onClose}
      title={id ? '编辑话术' : '新增话术'}
      submitText="保存"
      saving={save.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        const errs: Record<string, string> = {}
        if (!v.category.trim()) errs.category = '请填写分类'
        if (!v.question.trim()) errs.question = '请填写问题'
        if (!v.keywords.trim()) errs.keywords = '请填写关键词'
        if (!v.answer.trim()) errs.answer = '请填写回答'
        if (v.actionPath && !v.actionPath.startsWith('/')) errs.actionPath = '必须以 / 开头（站内页面）'
        setErrors(errs)
        if (Object.keys(errs).length === 0) save.mutate()
      }}
    >
      <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
        <Field label="分类" htmlFor="faq-cat" error={errors.category} required>
          <Input id="faq-cat" value={v.category} maxLength={30} onChange={(e) => set('category', e.target.value)} />
        </Field>
        <Field label="问题" htmlFor="faq-q" error={errors.question} required>
          <Input id="faq-q" value={v.question} maxLength={200} onChange={(e) => set('question', e.target.value)} />
        </Field>
      </div>
      <Field label="关键词" htmlFor="faq-kw" error={errors.keywords} required hint="用逗号分隔，例如：开单,收银,卖货">
        <Input id="faq-kw" value={v.keywords} maxLength={500} onChange={(e) => set('keywords', e.target.value)} />
      </Field>
      <Field label="回答" htmlFor="faq-a" error={errors.answer} required>
        <Textarea id="faq-a" rows={5} value={v.answer} maxLength={4000} onChange={(e) => set('answer', e.target.value)} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="按钮文字" htmlFor="faq-al" hint="选填，例如：去开单">
          <Input id="faq-al" value={v.actionLabel ?? ''} maxLength={30} onChange={(e) => set('actionLabel', e.target.value)} />
        </Field>
        <Field label="跳转页面" htmlFor="faq-ap" error={errors.actionPath} hint="选填，例如：/sales/new">
          <Input id="faq-ap" value={v.actionPath ?? ''} maxLength={100} onChange={(e) => set('actionPath', e.target.value)} />
        </Field>
      </div>
      <div className="flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="size-4 accent-blue-600" checked={v.enabled} onChange={(e) => set('enabled', e.target.checked)} />
          启用
        </label>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="size-4 accent-blue-600" checked={v.quickReply} onChange={(e) => set('quickReply', e.target.checked)} />
          显示为快捷问题
        </label>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          排序
          <Input
            type="number"
            value={v.sortOrder}
            onChange={(e) => set('sortOrder', Number(e.target.value) || 0)}
            className="h-9 w-20"
            aria-label="排序"
          />
        </label>
      </div>
    </FormDialog>
  )
}
