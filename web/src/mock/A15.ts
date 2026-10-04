/** A15 外观配置 — option lists (same shape as GET /api/v1/pages/A15). The saved appearance itself lives in `@/app/appearance`. */

export interface A15Palette {
  name: string
  /** cockpit background */
  bg: string
  /** panel fill */
  panel: string
  /** accent / glow */
  accent: string
}

export interface A15PreviewRow {
  name: string
  value: string
  /** positive = over budget (red), negative = saving (green) */
  tone: 'bad' | 'ok'
}

export interface A15Data {
  /** names of the 5 theme colours (swatches come from THEME_COLORS) */
  themeNames: string[]
  density: string[]
  radius: string[]
  fontSize: string[]
  cardStyle: string[]
  motion: string[]
  watermark: string[]
  /** cockpit palettes */
  palettes: A15Palette[]
  rotation: { min: number; max: number; step: number }
  previewRows: A15PreviewRow[]
}

export const A15_SEED: A15Data = {
  themeNames: ['政务蓝', '医保青', '深海蓝', '中国红', '墨绿'],
  density: ['紧凑', '标准', '宽松'],
  radius: ['直角 4', '标准 12', '圆润 18'],
  fontSize: ['12px', '13px', '14px'],
  cardStyle: ['描边', '渐变', '投影'],
  motion: ['关闭', '标准', '丰富'],
  watermark: ['浅', '标准', '深'],
  palettes: [
    { name: '深空蓝', bg: '#040A16', panel: 'rgba(30,91,216,.22)', accent: '#3AA0FF' },
    { name: '墨黑', bg: '#0A0B0D', panel: 'rgba(255,255,255,.07)', accent: '#F5B74E' },
    { name: '政务蓝', bg: '#0B2A66', panel: 'rgba(255,255,255,.1)', accent: '#7FD3FF' },
  ],
  rotation: { min: 5, max: 120, step: 5 },
  previewRows: [
    { name: 'BR25 脑缺血性疾患', value: '+1,860', tone: 'bad' },
    { name: 'ES35 呼吸系统感染', value: '+240', tone: 'bad' },
    { name: 'FM19 经皮心血管操作', value: '−2,150', tone: 'ok' },
  ],
}

/**
 * ACTIONS:
 * publishAppearance(Appearance: { c, dens, rad, font, card, scr, mot, rot, wm, name }) — 保存并发布: saves the platform-wide appearance; applies to all users;
 * resetAppearance({}) — 恢复默认: restores the default appearance platform-wide.
 */
