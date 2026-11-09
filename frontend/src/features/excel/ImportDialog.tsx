import { useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, Download, FileUp, Upload, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/api'
import { useCanManage } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { DUPLICATE_RULES, IMPORT_LABELS, IMPORT_QUERY_KEYS, importApi, type ImportMode, type ImportResult, type ImportType } from './api'

/** 「导入」按钮 + 弹窗（店主/管理员）。 */
export function ImportButton({ type, size = 'md' }: { type: ImportType; size?: 'sm' | 'md' }) {
  const canManage = useCanManage()
  const [open, setOpen] = useState(false)
  if (!canManage) return null
  return (
    <>
      <Button variant="secondary" size={size} onClick={() => setOpen(true)}>
        <Upload aria-hidden />
        导入
      </Button>
      {open && <ImportDialog type={type} onClose={() => setOpen(false)} />}
    </>
  )
}

/**
 * 导入流程：下载模板 → 选择文件和重复处理方式 → 检查（不写入）→ 没有错误时确认导入。
 * 只要有一行错误就整批不导入。
 */
export function ImportDialog({ type, onClose }: { type: ImportType; onClose: () => void }) {
  const label = IMPORT_LABELS[type]
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [mode, setMode] = useState<ImportMode>('CREATE_ONLY')
  const [check, setCheck] = useState<ImportResult | null>(null)
  const [done, setDone] = useState<ImportResult | null>(null)
  const [busy, setBusy] = useState<'template' | 'check' | 'import' | null>(null)

  async function run(dryRun: boolean) {
    if (!file) return
    setBusy(dryRun ? 'check' : 'import')
    try {
      const r = await importApi.upload(type, file, mode, dryRun)
      if (dryRun) {
        setCheck(r)
      } else if (r.imported) {
        setDone(r)
        await Promise.all(IMPORT_QUERY_KEYS[type].map((k) => queryClient.invalidateQueries({ queryKey: k })))
        toast.success(`已导入${label}：新增 ${r.created}，更新 ${r.updated}，跳过 ${r.skipped}`)
      } else {
        // 两次上传之间数据有变化，正式导入时又发现了问题
        setCheck(r)
      }
    } catch (e) {
      toast.error(errorMessage(e, dryRun ? '检查失败' : '导入失败'))
      if (!dryRun) setCheck(null)
    } finally {
      setBusy(null)
    }
  }

  function chooseFile(f: File | null) {
    setFile(f)
    setCheck(null)
  }

  const canImport = !!check && check.errors.length === 0 && check.created + check.updated > 0

  return (
    <Dialog.Root open onOpenChange={(o) => !o && !busy && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-slate-900/40" />
        <Dialog.Content
          className="fixed inset-x-0 bottom-0 z-50 max-h-[92dvh] overflow-y-auto rounded-t-2xl bg-white p-5 shadow-xl sm:inset-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-xl sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:p-6"
          aria-describedby={undefined}
        >
          <div className="flex items-start justify-between gap-3">
            <div>
              <Dialog.Title className="text-base font-semibold text-slate-900">导入{label}</Dialog.Title>
              <p className="mt-0.5 text-sm text-slate-500">支持 .xlsx 和 .csv，一次最多 5000 行、5 MB</p>
            </div>
            <Dialog.Close className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label="关闭" disabled={!!busy}>
              <X className="size-5" />
            </Dialog.Close>
          </div>

          {done ? (
            <div className="mt-6 space-y-4">
              <div className="flex items-start gap-3 rounded-lg bg-emerald-50 p-4 text-sm text-emerald-900">
                <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-emerald-600" aria-hidden />
                <div>
                  <p className="font-medium">导入完成</p>
                  <p className="mt-1">
                    新增 {done.created} 个、更新 {done.updated} 个、跳过 {done.skipped} 个{label}
                    {done.newCategories.length > 0 && `，新建分类：${done.newCategories.join('、')}`}
                  </p>
                </div>
              </div>
              <Button className="w-full" onClick={onClose}>
                完成
              </Button>
            </div>
          ) : (
            <div className="mt-5 space-y-5">
              <Step n={1} title="下载模板，按模板填写">
                <p className="text-sm text-slate-600">
                  不要修改表头；带 * 的列必须填写，第二个工作表有每一列的填写说明。
                </p>
                <Button
                  variant="secondary"
                  size="sm"
                  className="mt-2"
                  loading={busy === 'template'}
                  onClick={async () => {
                    setBusy('template')
                    try {
                      await importApi.template(type)
                    } catch (e) {
                      toast.error(errorMessage(e, '下载失败'))
                    } finally {
                      setBusy(null)
                    }
                  }}
                >
                  <Download aria-hidden />
                  下载{label}导入模板
                </Button>
              </Step>

              <Step n={2} title="选择文件">
                <input
                  ref={inputRef}
                  type="file"
                  accept=".xlsx,.csv"
                  className="sr-only"
                  aria-label="选择导入文件"
                  onChange={(e) => {
                    chooseFile(e.target.files?.[0] ?? null)
                    e.target.value = ''
                  }}
                />
                <button
                  type="button"
                  onClick={() => inputRef.current?.click()}
                  className="flex w-full items-center gap-3 rounded-lg border border-dashed border-slate-300 px-4 py-3 text-left text-sm hover:border-blue-400 hover:bg-blue-50/40"
                >
                  <FileUp className="size-5 shrink-0 text-slate-400" aria-hidden />
                  <span className="min-w-0 flex-1 truncate">{file ? file.name : '点击选择 .xlsx 或 .csv 文件'}</span>
                  {file && <span className="shrink-0 text-xs text-slate-500">{Math.max(1, Math.ceil(file.size / 1024))} KB</span>}
                </button>
                <fieldset className="mt-3">
                  <legend className="text-sm text-slate-600">已存在的{label}（{DUPLICATE_RULES[type]}）：</legend>
                  <div className="mt-1.5 flex flex-col gap-1.5 sm:flex-row sm:gap-4">
                    {(
                      [
                        ['CREATE_ONLY', '跳过，只新增'],
                        ['UPSERT', type === 'products' ? '更新资料（不改库存）' : '更新资料'],
                      ] as const
                    ).map(([value, text]) => (
                      <label key={value} className="flex items-center gap-2 text-sm text-slate-700">
                        <input
                          type="radio"
                          name="import-mode"
                          className="size-4 accent-blue-600"
                          checked={mode === value}
                          onChange={() => {
                            setMode(value)
                            setCheck(null)
                          }}
                        />
                        {text}
                      </label>
                    ))}
                  </div>
                </fieldset>
              </Step>

              <Step n={3} title="检查并导入">
                {!check ? (
                  <Button variant="secondary" disabled={!file} loading={busy === 'check'} onClick={() => void run(true)}>
                    检查文件
                  </Button>
                ) : (
                  <CheckResult result={check} label={label} />
                )}
              </Step>

              <div className="flex gap-2 border-t border-slate-100 pt-4">
                <Button variant="secondary" className="flex-1" onClick={onClose} disabled={!!busy}>
                  取消
                </Button>
                <Button className="flex-1" disabled={!canImport} loading={busy === 'import'} onClick={() => void run(false)}>
                  {check && canImport ? `确认导入 ${check.created + check.updated} 个${label}` : '确认导入'}
                </Button>
              </div>
            </div>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function Step({ n, title, children }: { n: number; title: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-900">
        <span className="flex size-5 items-center justify-center rounded-full bg-blue-50 text-xs font-semibold text-blue-700">{n}</span>
        {title}
      </h3>
      {children}
    </section>
  )
}

function CheckResult({ result, label }: { result: ImportResult; label: string }) {
  const ok = result.errors.length === 0
  return (
    <div className="space-y-3 text-sm">
      <div className={cn('flex items-start gap-2 rounded-lg p-3', ok ? 'bg-emerald-50 text-emerald-900' : 'bg-red-50 text-red-900')}>
        {ok ? (
          <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" aria-hidden />
        ) : (
          <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600" aria-hidden />
        )}
        <div>
          <p className="font-medium" role="status">
            {ok ? '检查通过' : `发现 ${result.errors.length} 处问题，修改后重新选择文件`}
          </p>
          <p className="mt-0.5">
            共 {result.total} 行：将新增 {result.created}、更新 {result.updated}、跳过 {result.skipped} 个{label}
          </p>
          {ok && result.created + result.updated === 0 && <p className="mt-0.5">没有需要导入的数据（全部已存在）</p>}
        </div>
      </div>
      {result.newCategories.length > 0 && (
        <p className="text-slate-600">将自动新建分类：{result.newCategories.join('、')}</p>
      )}
      {result.notes.map((n) => (
        <p key={n} className="text-amber-700">
          {n}
        </p>
      ))}
      {!ok && (
        <div className="max-h-56 overflow-y-auto rounded-lg border border-slate-200">
          <table className="w-full text-left text-sm">
            <thead className="sticky top-0 bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="px-3 py-2 font-medium">位置</th>
                <th className="px-3 py-2 font-medium">问题</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {result.errors.map((e, i) => (
                <tr key={i}>
                  <td className="whitespace-nowrap px-3 py-2 text-slate-600">
                    {e.row > 0 ? `第 ${e.row} 行` : '整个文件'}
                    {e.column && ` · ${e.column}`}
                  </td>
                  <td className="px-3 py-2 text-slate-800">{e.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
