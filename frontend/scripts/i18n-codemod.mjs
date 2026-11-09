// 一次性迁移脚本：把源代码里的中文文字包进 tr(...)。用 TypeScript 语法树定位，只改文字，不改格式。
// 用法：node scripts/i18n-codemod.mjs [文件或目录...]（默认 src）
import fs from 'node:fs'
import path from 'node:path'
import ts from 'typescript'

const HAN = /[㐀-鿿]/
const SKIP_FILES = [
  /[\\/]features[\\/]admin[\\/]/, // 平台后台不翻译
  /[\\/]features[\\/]legal[\\/]/, // 协议单独写英文版
  /[\\/]lib[\\/]i18n\.ts$/,
  /[\\/]lib[\\/]currency\.ts$/,
  /\.d\.ts$/,
]
const SKIP_ATTRS = new Set(['key', 'className', 'id', 'value', 'name', 'type', 'htmlFor', 'to', 'href', 'role', 'data-testid'])

const esc = (s) => s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n')

function varName(expr, used, i) {
  let n
  if (ts.isIdentifier(expr)) n = expr.text
  else if (ts.isPropertyAccessExpression(expr)) n = expr.name.text
  else if (ts.isCallExpression(expr) && expr.arguments.length === 1) {
    const a = expr.arguments[0]
    if (ts.isIdentifier(a)) n = a.text
    else if (ts.isPropertyAccessExpression(a)) n = a.name.text
  }
  if (!n || !/^\w+$/.test(n)) n = `v${i}`
  let name = n
  let k = 2
  while (used.has(name)) name = `${n}${k++}`
  used.add(name)
  return name
}

function shouldSkipString(node) {
  const p = node.parent
  if (!p) return true
  if (ts.isImportDeclaration(p) || ts.isExportDeclaration(p) || ts.isExternalModuleReference(p)) return true
  if (ts.isLiteralTypeNode(p)) return true
  if ((ts.isPropertyAssignment(p) || ts.isPropertySignature(p) || ts.isMethodDeclaration(p) || ts.isEnumMember(p)) && p.name === node) return true
  if (ts.isCaseClause(p)) return true
  if (ts.isBinaryExpression(p) && [ts.SyntaxKind.EqualsEqualsEqualsToken, ts.SyntaxKind.ExclamationEqualsEqualsToken, ts.SyntaxKind.EqualsEqualsToken, ts.SyntaxKind.ExclamationEqualsToken].includes(p.operatorToken.kind)) return true
  if (ts.isElementAccessExpression(p) && p.argumentExpression === node) return true
  // 已经在 tr(...) 或 console.xxx(...) 里
  for (let a = p; a; a = a.parent) {
    if (ts.isCallExpression(a)) {
      const e = a.expression
      if (ts.isIdentifier(e) && e.text === 'tr') return true
      if (ts.isPropertyAccessExpression(e) && ts.isIdentifier(e.expression) && e.expression.text === 'console') return true
    }
  }
  return false
}

function transform(file) {
  const text = fs.readFileSync(file, 'utf8')
  if (!HAN.test(text)) return 0
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const edits = []

  function visit(node) {
    // JSX 文本
    if (ts.isJsxText(node)) {
      const raw = node.getFullText(sf)
      if (HAN.test(raw)) {
        const start = node.getFullStart()
        const lead = raw.match(/^\s*/)[0].length
        const trail = raw.match(/\s*$/)[0].length
        const content = raw.slice(lead, raw.length - trail).replace(/\s*\n\s*/g, ' ')
        edits.push([start + lead, start + raw.length - trail, `{tr('${esc(content)}')}`])
      }
      return
    }
    if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
      if (HAN.test(node.text) && !shouldSkipString(node)) {
        const p = node.parent
        if (ts.isJsxAttribute(p)) {
          const attr = p.name.getText(sf)
          if (!SKIP_ATTRS.has(attr) && !attr.startsWith('data-')) {
            edits.push([node.getStart(sf), node.getEnd(), `{tr('${esc(node.text)}')}`])
          }
        } else {
          edits.push([node.getStart(sf), node.getEnd(), `tr('${esc(node.text)}')`])
        }
      }
      return
    }
    if (ts.isTemplateExpression(node)) {
      const parts = [node.head.text, ...node.templateSpans.map((s) => s.literal.text)]
      if (parts.some((x) => HAN.test(x)) && !shouldSkipString(node)) {
        const used = new Set()
        let key = node.head.text
        const vars = []
        node.templateSpans.forEach((span, i) => {
          const name = varName(span.expression, used, i)
          vars.push(`${name}: ${span.expression.getText(sf)}`)
          key += `{{${name}}}` + span.literal.text
        })
        const call = `tr('${esc(key)}', { ${vars.join(', ')} })`
        const inJsxAttr = ts.isJsxExpression(node.parent) // 已经在 {...} 里
        edits.push([node.getStart(sf), node.getEnd(), inJsxAttr || !ts.isJsxAttribute(node.parent) ? call : `{${call}}`])
        return
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(sf)
  if (edits.length === 0) return 0

  edits.sort((a, b) => b[0] - a[0])
  let out = text
  for (const [s, e, r] of edits) out = out.slice(0, s) + r + out.slice(e)

  if (!/import \{[^}]*\btr\b[^}]*\} from '@\/lib\/i18n'/.test(out)) {
    const imports = [...out.matchAll(/^import [\s\S]*?from '[^']+'\s*$/gm)]
    const last = imports.at(-1)
    const at = last ? last.index + last[0].length : 0
    out = out.slice(0, at) + (at ? '\n' : '') + "import { tr } from '@/lib/i18n'" + (at ? '' : '\n') + out.slice(at)
  }
  fs.writeFileSync(file, out, 'utf8')
  return edits.length
}

function walk(p, files) {
  const st = fs.statSync(p)
  if (st.isDirectory()) fs.readdirSync(p).forEach((n) => walk(path.join(p, n), files))
  else if (/\.(ts|tsx)$/.test(p) && !SKIP_FILES.some((r) => r.test(p))) files.push(p)
  return files
}

const targets = process.argv.slice(2).length ? process.argv.slice(2) : ['src']
let total = 0
for (const f of targets.flatMap((t) => walk(t, []))) {
  const n = transform(f)
  if (n) {
    total += n
    console.log(`${n}\t${f}`)
  }
}
console.log(`共 ${total} 处`)
