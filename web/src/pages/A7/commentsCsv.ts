/**
 * 意见单 export as CSV (opens in Excel). Every cell is quoted; a cell that would start a formula
 * (= + - @, tab or CR — CSV/formula injection) is prefixed with an apostrophe so Excel shows it as text.
 */
export interface CsvComment { sec: string; who: string; role: string; text: string; time: string; status: string }

export function csvCell(v: unknown): string {
  let s = v == null ? '' : String(v)
  if (/^[=+\-@\t\r]/.test(s)) s = "'" + s
  return '"' + s.replace(/"/g, '""') + '"'
}

export function commentsCsv(rows: CsvComment[], meta: { title: string; docNo: string; traceNo: string; watermark: string }): string {
  const lines: unknown[][] = [
    [meta.title],
    ['意见单编号', meta.docNo],
    ['追溯编号', meta.traceNo],
    ['水印', meta.watermark],
    [],
    ['序号', '段落', '提出人', '角色', '意见内容', '时间', '状态'],
    ...rows.map((r, i) => [i + 1, '§ ' + r.sec, r.who, r.role, r.text, r.time, r.status]),
    [],
    ['仅供核对使用 · ' + meta.watermark],
  ]
  // UTF-8 BOM so Excel detects the encoding of the Chinese text
  return '﻿' + lines.map(l => l.map(csvCell).join(',')).join('\r\n') + '\r\n'
}

export function downloadText(fileName: string, text: string, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
