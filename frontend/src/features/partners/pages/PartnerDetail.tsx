import type { LucideIcon } from 'lucide-react'
import { Pencil, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'

import { tr } from '@/lib/i18n'
/** 客户 / 供应商详情页的共用骨架。 */
export function PartnerDetailLayout({
  back,
  title,
  badge,
  canManage,
  onEdit,
  onDelete,
  info,
  history,
}: {
  back: { to: string; label: string }
  title: string
  badge?: ReactNode
  canManage: boolean
  onEdit: () => void
  onDelete: () => void
  info: { label: string; value: ReactNode; wide?: boolean }[]
  history: ReactNode
}) {
  return (
    <div>
      <PageHeader
        back={back}
        title={
          <span className="flex flex-wrap items-center gap-2">
            {title}
            {badge}
          </span>
        }
        actions={
          canManage && (
            <>
              <Button variant="secondary" onClick={onDelete}>
                <Trash2 aria-hidden />
                {tr('删除')}
              </Button>
              <Button onClick={onEdit}>
                <Pencil aria-hidden />
                {tr('编辑')}
              </Button>
            </>
          )
        }
      />
      <div className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <Card>
          <CardHeader title={tr('基本资料')} />
          <CardBody>
            <dl className="space-y-4 text-sm">
              {info.map((i) => (
                <div key={i.label}>
                  <dt className="text-slate-500">{i.label}</dt>
                  <dd className="mt-1 whitespace-pre-wrap break-words font-medium text-slate-900">{i.value || '—'}</dd>
                </div>
              ))}
            </dl>
          </CardBody>
        </Card>
        <Card className="lg:col-span-2">{history}</Card>
      </div>
    </div>
  )
}

/** 业务模块尚未上线时的历史记录占位（明确告知用户，而不是显示假数据）。 */
export function HistoryComingSoon({ title, icon, description }: { title: string; icon: LucideIcon; description: string }) {
  return (
    <>
      <CardHeader title={title} />
      <EmptyState icon={icon} title={tr('暂无记录')} description={description} />
    </>
  )
}

export function NotFoundCard({ back, title, error, onRetry, notFound }: {
  back: { to: string; label: string }
  title: string
  error: string
  onRetry: () => void
  notFound: boolean
}) {
  return (
    <div>
      <PageHeader title={title} back={back} />
      <Card>
        {notFound ? (
          <div className="flex flex-col items-center gap-3 py-14 text-center">
            <p className="font-semibold text-slate-900">{title}{tr('不存在')}</p>
            <p className="text-sm text-slate-500">{tr('它可能已被删除，或者不属于当前店铺')}</p>
            <Link to={back.to} className={buttonVariants({ variant: 'secondary' })}>
              {back.label}
            </Link>
          </div>
        ) : (
          <ErrorState onRetry={onRetry} message={error} />
        )}
      </Card>
    </div>
  )
}
