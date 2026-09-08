<script setup lang="ts">
/**
 * 基础数据管理（开发端）：食物模板 / 动作模板 / 分类模板
 * 模板 user_id=0 全局共享，所有业务用户查询时与本人自定义取并集
 */
import { ref } from 'vue'
import BlockTitle from '@/components/BlockTitle/BlockTitle.vue'
import FoodTemplate from './FoodTemplate.vue'
import ExerciseTemplate from './ExerciseTemplate.vue'
import CategoryTemplate from './CategoryTemplate.vue'

const activeTab = ref('food')
</script>

<template>
  <div class="tmpl">
    <BlockTitle title="基础数据管理" hint="全局共享模板，业务用户查询时自动并集" />

    <el-tabs v-model="activeTab" class="tmpl__tabs">
      <el-tab-pane label="食物模板" name="food">
        <FoodTemplate />
      </el-tab-pane>
      <el-tab-pane label="动作模板" name="exercise">
        <ExerciseTemplate />
      </el-tab-pane>
      <el-tab-pane label="分类模板" name="category">
        <CategoryTemplate />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
@use './templateManage';
</style>
