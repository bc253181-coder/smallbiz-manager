import { FileSpreadsheet, Lock } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useFeature } from '@/features/billing/api'
import { errorMessage } from '@/lib/api'
import { downloadFile } from '@/lib/download'
import { useCanManage } from '@/lib/hooks'

import { tr } from '@/lib/i18n'
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
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  if (!canManage) return null

  if (!allowed) {
    return (
      <Button
        variant="secondary"
        size={size}
        className="text-slate-500"
        title={tr('导出 Excel 需要专业版')}
        onClick={() =>
          toast(tr('导出 Excel 是专业版功能'), {
            description: tr('专业版 ¥9.9/月 · RM 6.9/月，还包含应收账款和员工账号'),
            action: { label: tr('查看套餐'), onClick: () => navigate('/settings/billing') },
          })
        }
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
