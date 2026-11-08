import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { ChevronRight, CreditCard } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, Input, Select } from '@/components/ui/form'
import { authApi, tenantApi } from '@/features/auth/api'
import { useAuth } from '@/features/auth/AuthProvider'
import { BUSINESS_TYPE_LABELS, PLAN_LABELS, type BusinessType } from '@/features/auth/types'
import { usePlan } from '@/features/billing/api'
import { applySession, errorMessage, fieldErrors } from '@/lib/api'

const TIMEZONES = [
  { value: 'Asia/Shanghai', label: '中国（北京时间 UTC+8）' },
  { value: 'Asia/Kuala_Lumpur', label: '马来西亚（UTC+8）' },
  { value: 'Asia/Singapore', label: '新加坡（UTC+8）' },
  { value: 'Asia/Hong_Kong', label: '香港（UTC+8）' },
  { value: 'Asia/Taipei', label: '台湾（UTC+8）' },
  { value: 'Asia/Bangkok', label: '泰国（UTC+7）' },
  { value: 'Asia/Tokyo', label: '日本（UTC+9）' },
]

const phonePattern = /^[0-9+\- ]*$/

export function SettingsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">设置</h1>
        <p className="mt-1 text-sm text-slate-500">管理店铺资料和个人账号</p>
      </div>
      <PlanLink />
      <StoreSettings />
      <div className="grid gap-6 lg:grid-cols-2">
        <ProfileSettings />
        <PasswordSettings />
      </div>
    </div>
  )
}

// ---------------- 套餐入口 ----------------

function PlanLink() {
  const { data } = usePlan()
  return (
    <Link to="/settings/billing" className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm hover:bg-slate-50">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
        <CreditCard className="size-5" aria-hidden />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-medium text-slate-900">套餐与续费</span>
        <span className="block text-sm text-slate-500">
          {data ? `当前：${PLAN_LABELS[data.plan]}${data.expiresAt ? `，${new Date(data.expiresAt).toLocaleDateString('zh-CN')} 到期` : ''}` : '查看套餐、兑换激活码'}
        </span>
      </span>
      <ChevronRight className="size-5 text-slate-400" aria-hidden />
    </Link>
  )
}

// ---------------- 店铺资料 ----------------

const storeSchema = z.object({
  name: z.string().trim().min(1, '请填写店铺名称').max(100, '店铺名称最多 100 字'),
  businessType: z.enum(['RETAIL', 'WHOLESALE', 'ECOMMERCE', 'TRADING', 'OTHER']),
  timezone: z.string().min(1, '请选择时区'),
  contactPhone: z.string().trim().max(30, '电话最多 30 位').regex(phonePattern, '电话格式不正确'),
  address: z.string().trim().max(300, '地址最多 300 字'),
  allowNegativeStock: z.boolean(),
})
type StoreValues = z.infer<typeof storeSchema>

function StoreSettings() {
  const { session, patchSession } = useAuth()
  const queryClient = useQueryClient()
  const isOwner = session?.currentTenant?.role === 'OWNER'
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['tenant'], queryFn: tenantApi.current })

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<StoreValues>({ resolver: zodResolver(storeSchema) })

  useEffect(() => {
    if (data) {
      reset({
        name: data.name,
        businessType: data.businessType,
        timezone: data.timezone,
        contactPhone: data.contactPhone ?? '',
        address: data.address ?? '',
        allowNegativeStock: data.allowNegativeStock,
      })
    }
  }, [data, reset])

  const mutation = useMutation({
    mutationFn: tenantApi.update,
    onSuccess: (updated) => {
      queryClient.setQueryData(['tenant'], updated)
      patchSession((s) => ({
        ...s,
        currentTenant: s.currentTenant
          ? { ...s.currentTenant, name: updated.name, businessType: updated.businessType, timezone: updated.timezone }
          : null,
        tenants: s.tenants.map((t) => (t.id === updated.id ? { ...t, name: updated.name } : t)),
      }))
      toast.success('店铺资料已保存')
    },
    onError: (e) => {
      Object.entries(fieldErrors(e)).forEach(([k, msg]) => setError(k as keyof StoreValues, { message: msg }))
      toast.error(errorMessage(e, '保存失败'))
    },
  })

  return (
    <Card>
      <CardHeader
        title="店铺资料"
        description={isOwner ? '联系电话和地址会显示在打印的单据上' : '只有店主可以修改店铺资料'}
      />
      <CardBody>
        {isLoading ? (
          <div className="h-40 animate-pulse rounded-lg bg-slate-100" />
        ) : isError ? (
          <div className="flex items-center gap-3 text-sm text-slate-600">
            加载失败
            <Button variant="secondary" size="sm" onClick={() => void refetch()}>
              重试
            </Button>
          </div>
        ) : (
          <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
            <fieldset disabled={!isOwner} className="grid gap-4 sm:grid-cols-2">
              <Field label="店铺名称" htmlFor="s-name" error={errors.name?.message} required>
                <Input id="s-name" invalid={!!errors.name} {...register('name')} />
              </Field>
              <Field label="经营类型" htmlFor="s-type">
                <Select id="s-type" {...register('businessType')}>
                  {(Object.keys(BUSINESS_TYPE_LABELS) as BusinessType[]).map((k) => (
                    <option key={k} value={k}>
                      {BUSINESS_TYPE_LABELS[k]}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="联系电话" htmlFor="s-phone" error={errors.contactPhone?.message}>
                <Input id="s-phone" type="tel" invalid={!!errors.contactPhone} {...register('contactPhone')} />
              </Field>
              <Field label="时区" htmlFor="s-tz" hint="用于计算「今日」「本月」销售额">
                <Select id="s-tz" {...register('timezone')}>
                  {TIMEZONES.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="地址" htmlFor="s-addr" error={errors.address?.message} className="sm:col-span-2">
                <Input id="s-addr" invalid={!!errors.address} {...register('address')} />
              </Field>
              <label className="flex items-start gap-3 sm:col-span-2">
                <input type="checkbox" className="mt-1 size-4 accent-blue-600" {...register('allowNegativeStock')} />
                <span>
                  <span className="block text-sm font-medium text-slate-700">允许负库存</span>
                  <span className="block text-xs text-slate-500">
                    开启后，库存不足时仍可开单（库存会变为负数）。适合先卖后补货的情况。
                  </span>
                </span>
              </label>
            </fieldset>
            {isOwner && (
              <div className="flex justify-end">
                <Button type="submit" loading={mutation.isPending} disabled={!isDirty}>
                  保存
                </Button>
              </div>
            )}
          </form>
        )}
      </CardBody>
    </Card>
  )
}

// ---------------- 个人资料 ----------------

const profileSchema = z.object({
  displayName: z.string().trim().min(1, '请填写昵称').max(100, '昵称最多 100 字'),
  phone: z.string().trim().max(30, '手机号最多 30 位').regex(phonePattern, '手机号格式不正确'),
})
type ProfileValues = z.infer<typeof profileSchema>

function ProfileSettings() {
  const { session, patchSession } = useAuth()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: session?.user.displayName ?? '', phone: session?.user.phone ?? '' },
  })

  const mutation = useMutation({
    mutationFn: authApi.updateProfile,
    onSuccess: (user) => {
      patchSession((s) => ({ ...s, user }))
      reset({ displayName: user.displayName, phone: user.phone ?? '' })
      toast.success('个人资料已保存')
    },
    onError: (e) => toast.error(errorMessage(e, '保存失败')),
  })

  return (
    <Card>
      <CardHeader title="个人资料" description={session?.user.email} />
      <CardBody>
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
          <Field label="昵称" htmlFor="p-name" error={errors.displayName?.message} required>
            <Input id="p-name" invalid={!!errors.displayName} {...register('displayName')} />
          </Field>
          <Field label="手机号" htmlFor="p-phone" error={errors.phone?.message}>
            <Input id="p-phone" type="tel" invalid={!!errors.phone} {...register('phone')} />
          </Field>
          <div className="flex justify-end">
            <Button type="submit" loading={mutation.isPending} disabled={!isDirty}>
              保存
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  )
}

// ---------------- 修改密码 ----------------

const passwordSchema = z
  .object({
    oldPassword: z.string().min(1, '请填写原密码'),
    newPassword: z.string().min(8, '新密码至少 8 位').max(64, '新密码最多 64 位'),
    confirmPassword: z.string().min(1, '请再次输入新密码'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: '两次输入的密码不一致' })
type PasswordValues = z.infer<typeof passwordSchema>

function PasswordSettings() {
  const navigate = useNavigate()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<PasswordValues>({ resolver: zodResolver(passwordSchema) })

  const mutation = useMutation({
    mutationFn: (v: PasswordValues) => authApi.changePassword({ oldPassword: v.oldPassword, newPassword: v.newPassword }),
    onSuccess: () => {
      toast.success('密码已修改，请用新密码重新登录')
      applySession(null)
      navigate('/login', { replace: true })
    },
    onError: (e) => {
      const fields = fieldErrors(e)
      Object.entries(fields).forEach(([k, msg]) => setError(k as keyof PasswordValues, { message: msg }))
      if (Object.keys(fields).length === 0) setError('oldPassword', { message: errorMessage(e, '修改失败') })
    },
  })

  return (
    <Card>
      <CardHeader title="修改密码" description="修改后，所有设备都需要重新登录" />
      <CardBody>
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4" noValidate>
          <Field label="原密码" htmlFor="pw-old" error={errors.oldPassword?.message}>
            <Input id="pw-old" type="password" autoComplete="current-password" invalid={!!errors.oldPassword} {...register('oldPassword')} />
          </Field>
          <Field label="新密码" htmlFor="pw-new" error={errors.newPassword?.message} hint="至少 8 位">
            <Input id="pw-new" type="password" autoComplete="new-password" invalid={!!errors.newPassword} {...register('newPassword')} />
          </Field>
          <Field label="确认新密码" htmlFor="pw-confirm" error={errors.confirmPassword?.message}>
            <Input id="pw-confirm" type="password" autoComplete="new-password" invalid={!!errors.confirmPassword} {...register('confirmPassword')} />
          </Field>
          <div className="flex justify-end">
            <Button type="submit" loading={mutation.isPending}>
              修改密码
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  )
}
