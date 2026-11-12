<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <section class="scaffold">
    <el-empty description="功能开发中" />
  </section>
</template>

<style scoped>
.scaffold {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
</style>
