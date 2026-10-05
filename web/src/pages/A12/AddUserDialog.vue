<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { A12Role } from '@/mock/A12'
import { SIDE_NAME } from './useUsers'

const props = defineProps<{ roles: A12Role[]; busy: boolean; submit: (f: { name: string; login: string; role: string; org: string }) => Promise<boolean> }>()
const open = defineModel<boolean>('open', { required: true })

const form = reactive({ name: '', login: '', role: '', org: '' })
const touched = ref(false)

const errors = computed(() => ({
  name: !form.name.trim() ? '请填写姓名' : form.name.trim().length > 32 ? '姓名不超过 32 个字符' : '',
  login: !/^[a-z][a-z0-9_]{2,31}$/.test(form.login) ? '3–32 位小写字母、数字或下划线,以字母开头' : '',
  role: !form.role ? '请选择角色' : '',
}))
const valid = computed(() => !errors.value.name && !errors.value.login && !errors.value.role)

watch(open, o => {
  if (o) {
    form.name = ''
    form.login = ''
    form.role = ''
    form.org = ''
    touched.value = false
  }
})

async function go() {
  touched.value = true
  if (!valid.value) return
  if (await props.submit({ name: form.name.trim(), login: form.login, role: form.role, org: form.org.trim() })) open.value = false
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
      :show-close="false"
      class="z-[96] flex w-[min(480px,calc(100%-64px))] flex-col gap-4 rounded-2xl border-0 bg-white p-6 shadow-[0_24px_64px_rgba(11,21,38,.3)]"
    >
      <div>
        <DialogTitle class="text-lg font-semibold">新增用户申请</DialogTitle>
        <DialogDescription class="mt-0.5 text-xs text-ink-4">提交后保存为"待复核",须由另一名召集人 / 行政管理组复核通过才会创建账号。</DialogDescription>
      </div>
      <div class="grid grid-cols-2 gap-3.5">
        <label class="flex flex-col gap-1 text-xs text-ink-3">姓名
          <Input v-model="form.name" placeholder="如 孙磊" class="h-9 text-[13px]" />
          <span v-if="touched && errors.name" class="text-[11px] text-bad-ink">{{ errors.name }}</span>
        </label>
        <label class="flex flex-col gap-1 text-xs text-ink-3">登录名
          <Input v-model="form.login" placeholder="如 sunlei" class="h-9 text-[13px]" autocomplete="off" />
          <span v-if="touched && errors.login" class="text-[11px] text-bad-ink">{{ errors.login }}</span>
        </label>
        <div class="flex flex-col gap-1 text-xs text-ink-3">角色
          <Select v-model="form.role">
            <SelectTrigger class="h-9 w-full text-[13px]" data-testid="add-role"><SelectValue placeholder="选择角色" /></SelectTrigger>
            <SelectContent class="z-[110]">
              <SelectItem v-for="r in roles" :key="r.code" :value="r.code">{{ r.name }} · {{ SIDE_NAME[r.side] }}</SelectItem>
            </SelectContent>
          </Select>
          <span v-if="touched && errors.role" class="text-[11px] text-bad-ink">{{ errors.role }}</span>
        </div>
        <label class="flex flex-col gap-1 text-xs text-ink-3">所属机构(可选)
          <Input v-model="form.org" placeholder="机构名称或编码,默认按角色" class="h-9 text-[13px]" />
        </label>
      </div>
      <div class="flex justify-end gap-2">
        <Button variant="outline" class="h-[38px] px-4 font-normal" @click="open = false">取消</Button>
        <Button class="h-[38px] px-5" :disabled="busy" @click="go">提交复核申请</Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
