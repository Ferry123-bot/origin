import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePracticeStore = defineStore('practice', () => {
  const currentIndex = ref(0)
  const started = ref(false)
  return { currentIndex, started }
})
