import { onUnmounted, ref } from 'vue'

export function useSmsCountdown() {
  const seconds = ref(0)
  let timer = 0

  function start(total = 60) {
    seconds.value = total
    window.clearInterval(timer)
    timer = window.setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) {
        window.clearInterval(timer)
      }
    }, 1000)
  }

  onUnmounted(() => window.clearInterval(timer))
  return { seconds, start }
}
