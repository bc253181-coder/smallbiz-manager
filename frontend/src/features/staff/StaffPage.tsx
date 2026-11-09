import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Copy, Link2, UserPlus, Users, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Field, Input, Select } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { ROLE_LABELS } from '@/features/auth/types'
import { errorMessage } from '@/lib/api'
import { formatDateTime, formatDay } from '@/lib/format'
import { inviteLink, ROLE_DESCRIPTIONS, staffApi, type AssignableRole, type Member } from './api'

import { tr } from '@/lib/i18n'
const QUERY_KEY = ['members']

export function StaffPage() {
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: QUERY_KEY, queryFn: staffApi.list })
  const [inviting, setInviting] = useState(false)
  const [createdLink, setCreatedLink] = useState<string | null>(null)
  const [removing, setRemoving] = useState<Member | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: QUERY_KEY })

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: number; role: AssignableRole }) => staffApi.changeRole(id, role),
    onSuccess: async (m) => {
      await refresh()
      toast.success(tr('已将 {{displayName}} 设为{{v1}}', { displayName: m.displayName, v1: ROLE_LABELS[m.role] }))
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const remove = useMutation({
    mutationFn: (id: number) => staffApi.remove(id),
    onSuccess: async () => {
      await refresh()
      toast.success(tr('已移除 {{v0}}', { v0: removing?.displayName ?? tr('员工') }))
      setRemoving(null)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const revoke = useMutation({
    mutationFn: (id: number) => staffApi.revoke(id),
    onSuccess: async () => {
      await refresh()
      toast.success(tr('邀请已撤销'))
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const full = !!data && data.used >= data.max

  return (
    <div className="space-y-6">
      <PageHeader
        title={tr('员工管理')}
        description={tr('邀请员工用自己的账号登录这家店。只有店主可以邀请、修改角色和移除员工。')}
        back={{ to: '/settings', label: tr('设置') }}
        actions={
          data?.allowed && (
            <Button onClick={() => setInviting(true)} disabled={full}>
              <UserPlus aria-hidden />
              {tr('邀请员工')}
            </Button>
          )
        }
      />

      {isLoading ? (
        <div className="h-48 animate-pulse rounded-xl bg-slate-100" />
      ) : isError ? (
        <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
      ) : (
        data && (
          <>
            {!data.allowed ? (
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
                <p className="font-medium">{tr('员工账号是专业版功能')}</p>
                <p className="mt-1 text-amber-800">
                  {tr('专业版 ¥9.9/月 · RM 6.9/月，店主以外最多 5 名员工。')}
                  {data.members.length > 1 && tr('专业版到期期间，已加入的员工暂时不能进入店铺，续费后自动恢复。')}
                </p>
                <Link to="/settings/billing" className={buttonVariants({ size: 'sm', className: 'mt-3' })}>
                  {tr('查看套餐')}
                </Link>
              </div>
            ) : (
              <p className="text-sm text-slate-600">
                {tr('员工名额：')}<span className="font-semibold tabular-nums text-slate-900">{tr('已用')} {data.used} / {data.max}</span>
                <span className="text-slate-500">{tr('（待接受的邀请也占名额）')}</span>
                {full && <span className="ml-2 text-amber-700">{tr('名额已满，可撤销未使用的邀请或移除员工')}</span>}
              </p>
            )}

            <Card>
              <CardHeader title={tr('成员')} description={tr('{{length}} 人', { length: data.members.length })} />
              <ul className="divide-y divide-slate-100">
                {data.members.map((m) => (
                  <li key={m.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center">
                    <div className="min-w-0 flex-1">
                      <p className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
                        <span className="truncate">{m.displayName}</span>
                        {m.self && <Badge>{tr('你')}</Badge>}
                      </p>
                      <p className="truncate text-xs text-slate-500">{m.email}</p>
                      <p className="text-xs text-slate-400">
                        {formatDay(m.joinedAt)} {tr('加入')}
                        {m.lastLoginAt && tr(' · 最近登录 {{lastLoginAt}}', { lastLoginAt: formatDateTime(m.lastLoginAt) })}
                      </p>
                    </div>
                    {m.role === 'OWNER' ? (
                      <Badge tone="blue" className="self-start sm:self-center">
                        {tr('店主')}
                      </Badge>
                    ) : (
                      <div className="flex items-center gap-2">
                        <Select
                          aria-label={tr('{{displayName}} 的角色', { displayName: m.displayName })}
                          className="w-28"
                          value={m.role}
                          disabled={changeRole.isPending}
                          onChange={(e) => changeRole.mutate({ id: m.id, role: e.target.value as AssignableRole })}
                        >
                          <option value="STAFF">{tr('店员')}</option>
                          <option value="ADMIN">{tr('管理员')}</option>
                        </Select>
                        <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50" onClick={() => setRemoving(m)}>
                          {tr('移除')}
                        </Button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </Card>

            {data.invitations.length > 0 && (
              <Card>
                <CardHeader title={tr('待接受的邀请')} description={tr('链接只在生成时显示一次；丢失了可以撤销后重新邀请')} />
                <ul className="divide-y divide-slate-100">
                  {data.invitations.map((i) => (
                    <li key={i.id} className="flex items-center gap-3 px-5 py-3">
                      <Link2 className="size-4 shrink-0 text-slate-400" aria-hidden />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-800">
                          {i.note || tr('未填写备注')} · {ROLE_LABELS[i.role]}
                        </p>
                        <p className="text-xs text-slate-500">{formatDateTime(i.expiresAt)} {tr('前有效')}</p>
                      </div>
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={revoke.isPending && revoke.variables === i.id}
                        onClick={() => revoke.mutate(i.id)}
                      >
                        {tr('撤销')}
                      </Button>
                    </li>
                  ))}
                </ul>
              </Card>
            )}

            <div className="grid gap-3 text-sm sm:grid-cols-2">
              {(['STAFF', 'ADMIN'] as const).map((r) => (
                <div key={r} className="rounded-xl border border-slate-200 bg-white p-4">
                  <p className="flex items-center gap-2 font-medium text-slate-900">
                    <Users className="size-4 text-slate-400" aria-hidden />
                    {ROLE_LABELS[r]}
                  </p>
                  <p className="mt-1 text-slate-600">{ROLE_DESCRIPTIONS[r]}</p>
                </div>
              ))}
            </div>
          </>
        )
      )}

      {inviting && (
        <InviteDialog
          onClose={() => setInviting(false)}
          onCreated={async (token) => {
            setInviting(false)
            setCreatedLink(inviteLink(token))
            await refresh()
          }}
        />
      )}
      {createdLink && <LinkDialog link={createdLink} onClose={() => setCreatedLink(null)} />}
      <ConfirmDialog
        open={!!removing}
        onOpenChange={(o) => !o && setRemoving(null)}
        title={tr('移除 {{v0}}？', { v0: removing?.displayName ?? '' })}
        description={tr('移除后对方立即不能再进入这家店铺；TA 开过的单、收过的款都会保留。以后可以重新邀请。')}
        confirmText={tr('移除')}
        danger
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id)}
      />
    </div>
  )
}

function InviteDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (token: string) => void }) {
  const [role, setRole] = useState<AssignableRole>('STAFF')
  const [note, setNote] = useState('')
  const create = useMutation({
    mutationFn: () => staffApi.invite(role, note.trim()),
    onSuccess: (r) => onCreated(r.token),
    onError: (e) => toast.error(errorMessage(e, tr('生成邀请失败'))),
  })
  return (
    <FormDialog
      open
      onClose={onClose}
      title={tr('邀请员工')}
      description={tr('生成一个邀请链接，通过微信发给员工。链接 7 天内有效，只能使用一次。')}
      submitText={tr('生成邀请链接')}
      saving={create.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        create.mutate()
      }}
    >
      <Field label={tr('角色')} htmlFor="inv-role">
        <Select id="inv-role" value={role} onChange={(e) => setRole(e.target.value as AssignableRole)}>
          <option value="STAFF">{tr('店员')}</option>
          <option value="ADMIN">{tr('管理员')}</option>
        </Select>
      </Field>
      <p className="-mt-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">{ROLE_DESCRIPTIONS[role]}</p>
      <Field label={tr('备注')} htmlFor="inv-note" hint={tr('方便你分辨是发给谁的，例如员工名字')}>
        <Input id="inv-note" maxLength={50} value={note} onChange={(e) => setNote(e.target.value)} />
      </Field>
    </FormDialog>
  )
}

function LinkDialog({ link, onClose }: { link: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      toast.success(tr('链接已复制，发给员工即可'))
    } catch {
      toast.error(tr('无法自动复制，请长按链接手动复制'))
    }
  }
  return (
    <Dialog.Root open onOpenChange={(o) => !o && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-slate-900/40" />
        <Dialog.Content
          className="fixed inset-x-0 bottom-0 z-50 rounded-t-2xl bg-white p-5 shadow-xl sm:inset-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-lg sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:p-6"
          aria-describedby={undefined}
        >
          <div className="flex items-start justify-between gap-3">
            <Dialog.Title className="text-base font-semibold text-slate-900">{tr('邀请链接已生成')}</Dialog.Title>
            <Dialog.Close className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label={tr('关闭')}>
              <X className="size-5" />
            </Dialog.Close>
          </div>
          <p className="mt-2 text-sm text-slate-600">
            {tr('把链接发给员工。对方打开后登录或注册，就能加入你的店铺。')}<span className="font-medium text-amber-700">{tr('链接只显示这一次')}</span>。
          </p>
          <p className="mt-4 break-all rounded-lg border border-slate-200 bg-slate-50 p-3 font-mono text-xs text-slate-800" aria-label={tr('邀请链接')}>
            {link}
          </p>
          <div className="mt-5 flex gap-2">
            <Button variant="secondary" className="flex-1" onClick={onClose}>
              {tr('完成')}
            </Button>
            <Button className="flex-1" onClick={() => void copy()}>
              {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
              {copied ? tr('已复制') : tr('复制链接')}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
