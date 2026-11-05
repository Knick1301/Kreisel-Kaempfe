import './assets/main.css'
import './assets/ui.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api'

const app = createApp(App)

app.use(createPinia())
app.use(router)

setUnauthorizedHandler(() => {
  const current = router.currentRoute.value
  if (current.name !== 'anmelden') {
    router.replace({ name: 'anmelden', query: { mode: 'login', redirect: current.fullPath } })
  }
})

app.mount('#app')
