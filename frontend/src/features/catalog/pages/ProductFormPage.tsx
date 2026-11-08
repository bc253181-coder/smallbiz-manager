import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ImagePlus, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, FormError, Input, Select, Textarea } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { useAuth } from '@/features/auth/AuthProvider'
import { errorCode, errorMessage, fieldErrors } from '@/lib/api'
import { categoryApi, flattenCategories, productApi } from '../api'
import { COMMON_UNITS, type Product, type ProductInput } from '../types'

const money = (label: string) =>
  z
    .string()
    .trim()
    .min(1, `请填写${label}`)
    .regex(/^\d{1,12}(\.\d{1,2})?$/, `${label}格式不正确（最多两位小数）`)

const optionalMoney = (label: string) =>
  z
    .string()
    .trim()
    .regex(/^(\d{1,12}(\.\d{1,2})?)?$/, `${label}格式不正确（最多两位小数）`)

const intField = (label: string) =>
  z
    .string()
    .trim()
    .regex(/^\d{1,9}$/, `${label}请填写 0 或正整数`)

const schema = z.object({
  name: z.string().trim().min(1, '请填写商品名称').max(200, '商品名称最多 200 字'),
  sku: z
    .string()
    .trim()
    .max(64, 'SKU 最多 64 位')
    .regex(/^[A-Za-z0-9._\-/]*$/, 'SKU 只能包含字母、数字和 . _ - /'),
  categoryId: z.string(),
  unit: z.string().trim().min(1, '请填写单位').max(20, '单位最多 20 字'),
  barcode: z.string().trim().max(64, '条码最多 64 位'),
  description: z.string().max(5000, '描述最多 5000 字'),
  costPrice: money('进货成本'),
  retailPrice: money('零售价'),
  wholesalePrice: optionalMoney('批发价'),
  lowStockThreshold: intField('预警值'),
  initialStock: intField('初始库存'),
  active: z.boolean(),
})
type FormValues = z.infer<typeof schema>

const CURRENCY_LABELS: Record<string, string> = { CNY: '人民币（¥）', MYR: '马币（RM）' }
const MAX_IMAGE = 2 * 1024 * 1024
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

function toInput(v: FormValues): ProductInput {
  return {
    name: v.name,
    sku: v.sku,
    categoryId: v.categoryId ? Number(v.categoryId) : null,
    description: v.description,
    unit: v.unit,
    barcode: v.barcode,
    costPrice: v.costPrice,
    retailPrice: v.retailPrice,
    wholesalePrice: v.wholesalePrice ? v.wholesalePrice : null,
    lowStockThreshold: Number(v.lowStockThreshold),
    status: v.active ? 'ACTIVE' : 'INACTIVE',
  }
}

function toForm(p: Product): FormValues {
  return {
    name: p.name,
    sku: p.sku,
    categoryId: p.categoryId ? String(p.categoryId) : '',
    unit: p.unit,
    barcode: p.barcode ?? '',
    description: p.description ?? '',
    costPrice: p.costPrice ?? '0',
    retailPrice: p.retailPrice,
    wholesalePrice: p.wholesalePrice ?? '',
    lowStockThreshold: String(p.lowStockThreshold),
    initialStock: '0',
    active: p.status === 'ACTIVE',
  }
}

export function ProductFormPage() {
  const { id } = useParams()
  const editing = id !== undefined
  const productId = editing ? Number(id) : undefined

  const productQuery = useQuery({
    queryKey: ['product', productId],
    queryFn: () => productApi.get(productId!),
    enabled: editing,
  })

  if (editing && productQuery.isLoading) {
    return <div className="h-96 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
  }
  if (editing && (productQuery.isError || !productQuery.data)) {
    return <ErrorState onRetry={() => void productQuery.refetch()} message={errorMessage(productQuery.error, '商品不存在')} />
  }
  return <ProductForm product={productQuery.data} />
}

function ProductForm({ product }: { product?: Product }) {
  const editing = !!product
  const { session } = useAuth()
  const currency = session?.currentTenant?.currency ?? 'CNY'
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // 图片：保存商品后再上传
  const fileInput = useRef<HTMLInputElement>(null)
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [removeImage, setRemoveImage] = useState(false)
  const previewUrl = useMemo(() => (imageFile ? URL.createObjectURL(imageFile) : null), [imageFile])
  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
  }, [previewUrl])
  const shownImage = previewUrl ?? (removeImage ? null : product?.imageUrl ?? null)

  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoryApi.tree })

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: product
      ? toForm(product)
      : {
          name: '',
          sku: '',
          categoryId: '',
          unit: '件',
          barcode: '',
          description: '',
          costPrice: '',
          retailPrice: '',
          wholesalePrice: '',
          lowStockThreshold: '5',
          initialStock: '0',
          active: true,
        },
  })

  function pickImage(file: File | undefined) {
    if (!file) return
    if (!IMAGE_TYPES.includes(file.type)) {
      toast.error('只支持 JPG、PNG、WebP 格式的图片')
      return
    }
    if (file.size > MAX_IMAGE) {
      toast.error('图片不能超过 2MB')
      return
    }
    setImageFile(file)
    setRemoveImage(false)
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    setSaving(true)
    try {
      const input = toInput(values)
      let saved = product
        ? await productApi.update(product.id, { ...input, version: product.version })
        : await productApi.create({ ...input, initialStock: Number(values.initialStock) })

      try {
        if (imageFile) saved = await productApi.uploadImage(saved.id, imageFile)
        else if (removeImage && product?.imageUrl) saved = await productApi.deleteImage(saved.id)
      } catch (e) {
        toast.error(`商品已保存，但图片上传失败：${errorMessage(e)}`)
      }

      queryClient.setQueryData(['product', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      await queryClient.invalidateQueries({ queryKey: ['categories'] })
      toast.success(editing ? '商品已保存' : '商品已创建')
      navigate(`/products/${saved.id}`, { replace: true })
    } catch (e) {
      const fields = fieldErrors(e)
      Object.entries(fields).forEach(([k, msg]) => setError(k as keyof FormValues, { message: msg }))
      const code = errorCode(e)
      if (code === 'SKU_EXISTS') setError('sku', { message: errorMessage(e) })
      else if (code === 'CONCURRENT_MODIFICATION') {
        setFormError('这个商品刚被其他人修改过。请刷新页面查看最新内容后再修改。')
      } else if (Object.keys(fields).length === 0) setFormError(errorMessage(e, '保存失败'))
    } finally {
      setSaving(false)
    }
  })

  return (
    <div>
      <PageHeader
        title={editing ? '编辑商品' : '新增商品'}
        back={editing ? { to: `/products/${product.id}`, label: '返回商品详情' } : { to: '/products', label: '返回商品列表' }}
      />
      <form onSubmit={onSubmit} noValidate className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <FormError message={formError} />
          <Card>
            <CardHeader title="基本信息" />
            <CardBody className="grid gap-4 sm:grid-cols-2">
              <Field label="商品名称" htmlFor="name" error={errors.name?.message} required className="sm:col-span-2">
                <Input id="name" invalid={!!errors.name} placeholder="例如：iPhone 15 钢化膜" {...register('name')} />
              </Field>
              <Field
                label="SKU 编号"
                htmlFor="sku"
                error={errors.sku?.message}
                hint={editing ? undefined : '留空则自动生成，如 SKU000001'}
              >
                <Input id="sku" invalid={!!errors.sku} {...register('sku')} />
              </Field>
              <Field label="分类" htmlFor="categoryId">
                <Select id="categoryId" {...register('categoryId')}>
                  <option value="">未分类</option>
                  {flattenCategories(categories ?? []).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="单位" htmlFor="unit" error={errors.unit?.message} required>
                <Input id="unit" list="unit-options" invalid={!!errors.unit} {...register('unit')} />
                <datalist id="unit-options">
                  {COMMON_UNITS.map((u) => (
                    <option key={u} value={u} />
                  ))}
                </datalist>
              </Field>
              <Field label="条形码" htmlFor="barcode" error={errors.barcode?.message}>
                <Input id="barcode" inputMode="numeric" invalid={!!errors.barcode} {...register('barcode')} />
              </Field>
              <Field label="商品描述" htmlFor="description" error={errors.description?.message} className="sm:col-span-2">
                <Textarea id="description" rows={3} invalid={!!errors.description} {...register('description')} />
              </Field>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="价格" description={`金额单位：${CURRENCY_LABELS[currency] ?? currency}`} />
            <CardBody className="grid gap-4 sm:grid-cols-3">
              <Field label="进货成本" htmlFor="costPrice" error={errors.costPrice?.message} required hint="用于计算毛利，店员不可见">
                <Input id="costPrice" inputMode="decimal" invalid={!!errors.costPrice} {...register('costPrice')} />
              </Field>
              <Field label="零售价" htmlFor="retailPrice" error={errors.retailPrice?.message} required>
                <Input id="retailPrice" inputMode="decimal" invalid={!!errors.retailPrice} {...register('retailPrice')} />
              </Field>
              <Field label="批发价" htmlFor="wholesalePrice" error={errors.wholesalePrice?.message} hint="不填则按零售价">
                <Input id="wholesalePrice" inputMode="decimal" invalid={!!errors.wholesalePrice} {...register('wholesalePrice')} />
              </Field>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="库存" />
            <CardBody className="grid gap-4 sm:grid-cols-2">
              {editing ? (
                <div className="space-y-1.5">
                  <span className="block text-sm font-medium text-slate-700">当前库存</span>
                  <p className="flex h-10 items-center text-sm text-slate-900">
                    {product.stockQty} {product.unit}
                  </p>
                  <p className="text-xs text-slate-500">库存需要通过入库、出库或盘点调整，以保留完整记录</p>
                </div>
              ) : (
                <Field label="初始库存" htmlFor="initialStock" error={errors.initialStock?.message} hint="会记录为一条入库流水">
                  <Input id="initialStock" inputMode="numeric" invalid={!!errors.initialStock} {...register('initialStock')} />
                </Field>
              )}
              <Field
                label="低库存预警值"
                htmlFor="lowStockThreshold"
                error={errors.lowStockThreshold?.message}
                hint="库存小于或等于此数量时提醒补货"
              >
                <Input
                  id="lowStockThreshold"
                  inputMode="numeric"
                  invalid={!!errors.lowStockThreshold}
                  {...register('lowStockThreshold')}
                />
              </Field>
            </CardBody>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader title="商品图片" description="JPG / PNG / WebP，不超过 2MB" />
            <CardBody className="space-y-3">
              <div className="flex aspect-square items-center justify-center overflow-hidden rounded-lg border border-dashed border-slate-300 bg-slate-50">
                {shownImage ? (
                  <img src={shownImage} alt="商品图片预览" className="size-full object-cover" />
                ) : (
                  <button
                    type="button"
                    onClick={() => fileInput.current?.click()}
                    className="flex size-full flex-col items-center justify-center gap-2 text-sm text-slate-500 hover:bg-slate-100"
                  >
                    <ImagePlus className="size-8 text-slate-400" aria-hidden />
                    点击上传图片
                  </button>
                )}
              </div>
              <input
                ref={fileInput}
                type="file"
                accept={IMAGE_TYPES.join(',')}
                className="hidden"
                aria-label="选择商品图片"
                onChange={(e) => {
                  pickImage(e.target.files?.[0])
                  e.target.value = ''
                }}
              />
              {shownImage && (
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" className="flex-1" onClick={() => fileInput.current?.click()}>
                    <ImagePlus aria-hidden />
                    更换
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="flex-1"
                    onClick={() => {
                      setImageFile(null)
                      setRemoveImage(true)
                    }}
                  >
                    <Trash2 aria-hidden />
                    移除
                  </Button>
                </div>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="销售状态" />
            <CardBody>
              <label className="flex items-start gap-3">
                <input type="checkbox" className="mt-1 size-4 accent-blue-600" {...register('active')} />
                <span>
                  <span className="block text-sm font-medium text-slate-700">在售</span>
                  <span className="block text-xs text-slate-500">取消勾选即下架：保留商品和历史记录，但开单时不可选</span>
                </span>
              </label>
            </CardBody>
          </Card>

          <div className="flex gap-2 lg:flex-col">
            <Button type="submit" size="lg" loading={saving} className="flex-1">
              {editing ? '保存修改' : '创建商品'}
            </Button>
            <Button variant="secondary" size="lg" className="flex-1" onClick={() => navigate(-1)} disabled={saving}>
              取消
            </Button>
          </div>
        </div>
      </form>
    </div>
  )
}
