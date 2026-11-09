// 多语言检查（构建前运行，失败则构建失败）：
//   1. 源代码里还有没包进 tr(...) 的中文文字（注释除外）；
//   2. tr(...) 用到的中文在英文词典 src/locales/en.json 里找不到。
// 用法：node scripts/i18n-check.mjs [--dump 输出文件]  （--dump 把缺少翻译的键写成 JSON，便于补翻译）
import fs from 'node:fs'
import path from 'node:path'

const HAN = /[\u3400-\u9fff]/
const ROOT = 'src'
// 不需要翻译的文件：平台后台（只给平台管理员用）、协议正文（中英文分别写）、词典本身
// systemText.ts：用中文正则匹配数据库里系统生成的中文，本身就该是中文
const SKIP = [/[\\/]features[\\/]admin[\\/]/, /[\\/]features[\\/]legal[\\/]/, /[\\/]lib[\\/]i18n\.ts$/, /[\\/]lib[\\/]systemText\.ts$/, /[\\/]locales[\\/]/]

const TR = /\btr\(\s*'((?:\\.|[^'\\])*)'/g
// trc('语境', '原文')：语境本身不需要翻译；原文作为普通键检查（语境翻译是可选的覆盖）
const TRC = /\btrc\(\s*'(?:\\.|[^'\\])*'\s*,\s*'((?:\\.|[^'\\])*)'/g
const unescape = (s) => s.replace(/\\n/g, '\n').replace(/\\(.)/g, '$1')

function walk(p, out) {
  const st = fs.statSync(p)
  if (st.isDirectory()) fs.readdirSync(p).forEach((n) => walk(path.join(p, n), out))
  else if (/\.(ts|tsx)$/.test(p) && !SKIP.some((r) => r.test(p))) out.push(p)
  return out
}

const files = walk(ROOT, [])
const keys = new Map() // key → 首次出现的位置
const leftovers = []

for (const f of files) {
  const text = fs.readFileSync(f, 'utf8')
  for (const m of [...text.matchAll(TR), ...text.matchAll(TRC)]) {
    const k = unescape(m[1])
    if (!keys.has(k)) keys.set(k, f)
  }
  // 去掉 tr('...') 的内容和注释后，不应再有中文
  const stripped = text
    .replace(TRC, "trc('', ''")
    .replace(TR, "tr(''")
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:'"`\w])\/\/.*$/gm, '$1')
  stripped.split('\n').forEach((line, i) => {
    if (HAN.test(line)) leftovers.push(`${f}:${i + 1}: ${line.trim().slice(0, 120)}`)
  })
}

const en = JSON.parse(fs.readFileSync('src/locales/en.json', 'utf8'))
const missing = [...keys.keys()].filter((k) => !(k in en))
const unused = Object.keys(en).filter((k) => !keys.has(k) && !k.startsWith('ERR:') && !k.startsWith('SRV:'))

const dumpAt = process.argv.indexOf('--dump')
if (dumpAt > 0) {
  const out = Object.fromEntries(missing.map((k) => [k, '']))
  fs.writeFileSync(process.argv[dumpAt + 1], JSON.stringify(out, null, 2), 'utf8')
}

console.log(`翻译键 ${keys.size} 个，英文词典 ${Object.keys(en).length} 条`)
if (unused.length) console.log(`词典中未使用的 ${unused.length} 条（不影响构建）`)
let failed = false
if (leftovers.length) {
  failed = true
  console.error(`\n✗ 有 ${leftovers.length} 处中文没有包进 tr(...)：`)
  leftovers.slice(0, 50).forEach((l) => console.error('  ' + l))
}
if (missing.length) {
  failed = true
  console.error(`\n✗ 有 ${missing.length} 条文字缺少英文翻译（src/locales/en.json），例如：`)
  missing.slice(0, 20).forEach((k) => console.error(`  ${JSON.stringify(k)}  ← ${keys.get(k)}`))
}
if (failed) process.exit(1)
console.log('✓ 多语言检查通过')
