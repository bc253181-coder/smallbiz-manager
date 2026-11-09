import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { Button } from '@/components/ui/button'
import { Field, FormError, Input } from '@/components/ui/form'
import { errorMessage } from '@/lib/api'
import { useAuth } from '../AuthProvider'

const schema = z.object({
  email: z.string().trim().min(1, '请填写邮箱').pipe(z.email('邮箱格式不正确')),
  password: z.string().min(1, '请填写密码'),
})
type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login(values.email, values.password)
      navigate(from, { replace: true })
    } catch (e) {
      setFormError(errorMessage(e, '登录失败，请稍后重试'))
    }
  })

  return (
    <AuthLayout title="登录" subtitle="欢迎回来，继续管理你的生意">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <FormError message={formError} />
        <Field label="邮箱" htmlFor="email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" invalid={!!errors.email} {...register('email')} />
        </Field>
        <Field label="密码" htmlFor="password" error={errors.password?.message}>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            invalid={!!errors.password}
            {...register('password')}
          />
        </Field>
        <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
          登录
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        还没有账号？
        <Link to="/register" state={location.state} className="font-medium text-blue-600 hover:underline">
          免费注册
        </Link>
      </p>
    </AuthLayout>
  )
}
