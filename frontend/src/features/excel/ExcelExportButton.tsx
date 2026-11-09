import { FileSpreadsheet, Lock } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useFeature } from '@/features/billing/api'
import { errorMessage } from '@/lib/api'
import { downloadFile } from '@/lib/download'
import { useCanManage } from '@/lib/hooks'

import { tr } from '@/lib/i18n'
import { openUpgrade } from '@/lib/upgrade'
/**
 * 「导出 Excel」按钮（专业版，店主/管理员）。path 为 /exports/... 或报表导出地址，params 为当前筛选条件。
 * 未开通专业版时显示锁图标，点击提示升级。
 */
export function ExcelExportButton({
  path,
  params = {},
  fallbackName,
  size = 'md',
  label = tr('导出 Excel'),
}: {
  path: string
  params?: Record<string, string | number | boolean | undefined>
  fallbackName: string
  size?: 'sm' | 'md'
  label?: string
}) {
  const canManage = useCanManage()
  const allowed = useFeature('EXCEL_EXPORT')
  const [busy, setBusy] = useState(false)
  if (!canManage) return null

  if (!allowed) {
    return (
      <Button
        variant="secondary"
        size={size}
        className="text-slate-500"
        title={tr('导出 Excel 是高级会员功能')}
        onClick={() => openUpgrade({ kind: 'feature', feature: 'EXCEL_EXPORT' })}
      >
        <Lock aria-hidden />
        {label}
      </Button>
    )
  }

  return (
    <Button
      variant="secondary"
      size={size}
      loading={busy}
      onClick={async () => {
        setBusy(true)
        try {
          await downloadFile(path, params, fallbackName)
        } catch (e) {
          toast.error(errorMessage(e, tr('导出失败')))
        } finally {
          setBusy(false)
        }
      }}
    >
      {!busy && <FileSpreadsheet aria-hidden />}
      {label}
    </Button>
  )
}
