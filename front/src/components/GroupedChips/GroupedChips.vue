<script setup lang="ts">
/**
 * 公共组件 · GroupedChips 分组标签选择（饮食食物 / 锻炼动作选择复用）
 * - groups：[{ label: 分组名, items: [{ id, label, sub(小字), favorite }] }]
 * - activeId：当前选中项 id（is-on 高亮，随模块主色 --cb-mod）
 * - showFav：显示收藏星标（点击 emit toggle-fav，父级处理切换）
 * - showEdit：chip 内显示编辑铅笔（hover 浮现，点击 emit edit，父级处理编辑）
 * - addLabel：尾部「自定义」虚线入口文字；空则不显示（点击 emit add）
 */
import { Plus, Pencil } from '@lucide/vue'

export interface GroupedChipItem {
  id: number | string
  label: string
  /** chip 内小字（如「89 kcal/100g」「MET 3.5」） */
  sub?: string
  /** 收藏状态（星标展示） */
  favorite?: boolean
}
export interface GroupedChipGroup {
  label: string
  items: GroupedChipItem[]
}

withDefaults(
  defineProps<{
    groups: GroupedChipGroup[]
    activeId?: number | string | null
    showFav?: boolean
    showEdit?: boolean
    addLabel?: string
  }>(),
  { activeId: null, showFav: false, showEdit: false, addLabel: '' }
)

const emit = defineEmits<{
  (e: 'select', item: GroupedChipItem): void
  (e: 'toggle-fav', item: GroupedChipItem): void
  (e: 'edit', item: GroupedChipItem): void
  (e: 'add'): void
}>()
</script>

<template>
  <div class="gc">
    <template v-for="g in groups" :key="g.label">
      <div class="gc__label">{{ g.label }}</div>
      <div class="gc__chips">
        <button
          v-for="c in g.items" :key="c.id"
          class="gc__chip" :class="{ 'is-on': c.id === activeId, 'gc__chip--fav': showFav && c.favorite }"
          type="button"
          @click="emit('select', c)"
        >
          {{ c.label }}<i v-if="c.sub">{{ c.sub }}</i>
          <span
            v-if="showFav"
            class="gc__fav" :class="{ 'is-fav': c.favorite }"
            :title="c.favorite ? '取消收藏' : '收藏'"
            @click.stop="emit('toggle-fav', c)"
          >{{ c.favorite ? '★' : '☆' }}</span>
          <span
            v-if="showEdit"
            class="gc__edit" title="编辑"
            @click.stop="emit('edit', c)"
          ><component :is="Pencil" :size="12" /></span>
        </button>
      </div>
    </template>
    <!-- 尾部自定义入口：全组共用一个，独立成行 -->
    <div v-if="addLabel" class="gc__chips">
      <button class="gc__chip gc__chip--add" type="button" @click="emit('add')">
        <component :is="Plus" :size="13" /> {{ addLabel }}
      </button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use './groupedChips';
</style>
