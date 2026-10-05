import type { Component } from 'vue'
import {
  BellRing, CircleHelp, ClipboardCheck, Database, Eye, FileChartColumn, GraduationCap, Hospital, Landmark,
  Layers, LayoutDashboard, LayoutTemplate, Lightbulb, MapPinned, MessageSquare, MessageSquareWarning, Palette,
  Rocket, ScrollText, Send, Settings, ShieldCheck, SlidersHorizontal, Smartphone, Sparkles, Stethoscope,
  Swords, Users, Workflow,
} from '@lucide/vue'

/** 菜单图标名 → lucide 组件;nav.ts 里的 `icon` 字段都从这里取。 */
export const ICONS: Record<string, Component> = {
  landmark: Landmark,
  hospital: Hospital,
  'layout-dashboard': LayoutDashboard,
  database: Database,
  'sliders-horizontal': SlidersHorizontal,
  'layout-template': LayoutTemplate,
  eye: Eye,
  lightbulb: Lightbulb,
  sparkles: Sparkles,
  stethoscope: Stethoscope,
  send: Send,
  rocket: Rocket,
  workflow: Workflow,
  'message-square': MessageSquare,
  'message-square-warning': MessageSquareWarning,
  'bell-ring': BellRing,
  settings: Settings,
  users: Users,
  'scroll-text': ScrollText,
  palette: Palette,
  layers: Layers,
  swords: Swords,
  'map-pinned': MapPinned,
  'shield-check': ShieldCheck,
  'file-chart-column': FileChartColumn,
  'clipboard-check': ClipboardCheck,
  smartphone: Smartphone,
  'graduation-cap': GraduationCap,
}

export function iconOf(name: string): Component {
  return ICONS[name] ?? CircleHelp
}
