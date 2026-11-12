<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section class="scaffold">
    <h2>{{ title }}</h2>
    <el-empty description="功能开发中" />
  </section>
</template>

<style scoped>
.scaffold {
  padding: 8px 0 24px;
}
</style>
