import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { Field, FormError, Input } from '@/components/ui/form'
import { errorMessage, fieldErrors } from '@/lib/api'
import { useAuth } from '../AuthProvider'

const schema = z
  .object({
    displayName: z.string().trim().min(1, '请填写昵称').max(100, '昵称最多 100 字'),
    email: z.string().trim().min(1, '请填写邮箱').pipe(z.email('邮箱格式不正确')),
    password: z.string().min(8, '密码至少 8 位').max(64, '密码最多 64 位'),
    confirmPassword: z.string().min(1, '请再次输入密码'),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: '两次输入的密码不一致' })
type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await registerAccount(values.email, values.password, values.displayName)
      navigate('/onboarding', { replace: true })
    } catch (e) {
      const fields = fieldErrors(e)
      Object.entries(fields).forEach(([k, msg]) => setError(k as keyof FormValues, { message: msg }))
      if (Object.keys(fields).length === 0) setFormError(errorMessage(e, '注册失败，请稍后重试'))
    }
  })

  return (
    <AuthLayout title="注册账号" subtitle="1 分钟开通，免费版长期可用">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <FormError message={formError} />
        <Field label="昵称" htmlFor="displayName" error={errors.displayName?.message}>
          <Input id="displayName" autoComplete="name" invalid={!!errors.displayName} {...register('displayName')} />
        </Field>
        <Field label="邮箱" htmlFor="email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" invalid={!!errors.email} {...register('email')} />
        </Field>
        <Field label="密码" htmlFor="password" error={errors.password?.message} hint="至少 8 位">
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            invalid={!!errors.password}
            {...register('password')}
          />
        </Field>
        <Field label="确认密码" htmlFor="confirmPassword" error={errors.confirmPassword?.message}>
          <Input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            invalid={!!errors.confirmPassword}
            {...register('confirmPassword')}
          />
        </Field>
        <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
          注册
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        已有账号？
        <Link to="/login" className="font-medium text-blue-600 hover:underline">
          去登录
        </Link>
      </p>
    </AuthLayout>
  )
}
