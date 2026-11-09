import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { Field, FormError, Input, Select } from '@/components/ui/form'
import { CURRENCIES, guessCurrency } from '@/lib/currency'
import { useAuth } from '@/features/auth/AuthProvider'
import { BUSINESS_TYPE_LABELS, type BusinessType } from '@/features/auth/types'
import { errorMessage } from '@/lib/api'
import { cn } from '@/lib/utils'

import { tr } from '@/lib/i18n'
const TYPES: { value: BusinessType; hint: string }[] = [
  { value: 'RETAIL', hint: tr('门店零售、服装饰品、美妆日用') },
  { value: 'WHOLESALE', hint: tr('批发、分销，按批发价出货') },
  { value: 'ECOMMERCE', hint: tr('淘宝、小红书、抖音等网店') },
  { value: 'TRADING', hint: tr('进出口、贸易公司') },
  { value: 'OTHER', hint: tr('其他类型的经营') },
]

const schema = z.object({
  name: z.string().trim().min(1, tr('请填写店铺名称')).max(100, tr('店铺名称最多 100 字')),
  businessType: z.enum(['RETAIL', 'WHOLESALE', 'ECOMMERCE', 'TRADING', 'OTHER'], { message: tr('请选择经营类型') }),
  currency: z.string().min(3),
})
type FormValues = z.infer<typeof schema>

/** 创建店铺：注册后的引导页（/onboarding），也用于新增店铺（/stores/new）。 */
export function CreateStorePage({ firstStore }: { firstStore: boolean }) {
  const { session, createTenant } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { businessType: 'RETAIL', currency: guessCurrency() } })
  const selected = watch('businessType')

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const s = await createTenant(values.name, values.businessType, values.currency)
      toast.success(tr('店铺「{{name}}」创建成功', { name: s.currentTenant?.name }))
      navigate('/', { replace: true })
    } catch (e) {
      setFormError(errorMessage(e, tr('创建失败，请稍后重试')))
    }
  })

  const hasStores = (session?.tenants.length ?? 0) > 0

  return (
    <AuthLayout
      wide
      title={firstStore ? tr('欢迎，{{v0}}！先创建你的店铺', { v0: session?.user.displayName ?? '' }) : tr('新建店铺')}
      subtitle={tr('每个店铺的商品、库存、订单数据完全独立。以后可以随时修改店铺信息。')}
    >
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <FormError message={formError} />
        <Field label={tr('店铺名称')} htmlFor="name" error={errors.name?.message} required>
          <Input id="name" placeholder={tr('例如：小美饰品店')} invalid={!!errors.name} {...register('name')} />
        </Field>

        <Field label={tr('币种')} htmlFor="currency" hint={tr('店铺的所有金额都用这个币种显示。有了销售或采购记录后不能再修改。')}>
          <Select id="currency" {...register('currency')}>
            {CURRENCIES.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}（{c.code} {c.symbol.trim()}）
              </option>
            ))}
          </Select>
        </Field>

        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-slate-700">{tr('经营类型')}</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {TYPES.map((t) => (
              <label
                key={t.value}
                className={cn(
                  'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors',
                  selected === t.value ? 'border-blue-600 bg-blue-50/60 ring-1 ring-blue-600' : 'border-slate-200 hover:bg-slate-50',
                )}
              >
                <input type="radio" value={t.value} className="mt-1 accent-blue-600" {...register('businessType')} />
                <span>
                  <span className="block text-sm font-medium text-slate-900">{BUSINESS_TYPE_LABELS[t.value]}</span>
                  <span className="block text-xs text-slate-500">{t.hint}</span>
                </span>
              </label>
            ))}
          </div>
          {errors.businessType && <p className="mt-1 text-xs text-red-600">{errors.businessType.message}</p>}
        </fieldset>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          {!firstStore && (
            <Button variant="secondary" onClick={() => navigate(-1)}>
              {tr('取消')}
            </Button>
          )}
          <Button type="submit" size="lg" loading={isSubmitting}>
            {tr('创建店铺')}
          </Button>
        </div>
      </form>
      {firstStore && hasStores && (
        <p className="mt-6 text-center text-sm text-slate-500">
          {tr('已经有店铺了？')}
          <Link to="/select-tenant" className="font-medium text-blue-600 hover:underline">
            {tr('选择店铺')}
          </Link>
        </p>
      )}
    </AuthLayout>
  )
}
