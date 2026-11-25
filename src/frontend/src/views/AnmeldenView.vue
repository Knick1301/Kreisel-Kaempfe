<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '@/api'
import { setSession } from '@/auth'
import AppLogo from '@/components/AppLogo.vue'

const route = useRoute()
const logoSize = window.matchMedia?.('(max-width: 820px)').matches ? 110 : 190
const router = useRouter()

const mode = ref<'register' | 'login'>(route.query.mode === 'login' ? 'login' : 'register')
const form = reactive({ username: '', password: '', repeat: '' })
const touched = ref(false)
const busy = ref(false)
const serverError = ref<string | null>(null)

watch(mode, () => {
  touched.value = false
  serverError.value = null
})

const errors = computed(() => {
  const e: Partial<Record<'username' | 'password' | 'repeat', string>> = {}
  if (mode.value === 'login') {
    if (!form.username.trim()) e.username = 'Bitte Benutzernamen eingeben'
    if (!form.password) e.password = 'Bitte Passwort eingeben'
    return e
  }
  if (!/^[A-Za-z0-9_-]{3,20}$/.test(form.username.trim())) {
    e.username = '3–20 Zeichen: Buchstaben, Ziffern, _ und -'
  }
  if (form.password.length < 8) e.password = 'Mindestens 8 Zeichen'
  else if (!/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    e.password = 'Mindestens ein Buchstabe und eine Ziffer'
  }
  if (form.repeat !== form.password) e.repeat = 'Passwörter stimmen nicht überein'
  return e
})

async function submit() {
  touched.value = true
  serverError.value = null
  if (Object.keys(errors.value).length) return
  busy.value = true
  try {
    const isNew = mode.value === 'register'
    const res = isNew
      ? await register(form.username.trim(), form.password)
      : await login(form.username.trim(), form.password)
    setSession(res.token, res.username)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
    await router.replace(isNew ? { path: '/baukasten', query: { neu: '1' } } : (redirect ?? '/'))
  } catch (e) {
    serverError.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="intro">
      <AppLogo :size="logoSize" />
      <h1>Kreisel<br />Kämpfer</h1>
      <p>Bau deinen Kreisel, tritt in der Arena an und steig in der Rangliste auf.</p>
    </div>

    <section class="panel card" aria-label="Konto">
      <div class="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          Konto erstellen
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >
          Anmelden
        </button>
      </div>

      <form class="panel-body form" novalidate @submit.prevent="submit">
        <p class="lead">
          {{ mode === 'register' ? 'Zum Kämpfen brauchst du ein Konto.' : 'Willkommen zurück.' }}
        </p>

        <label class="field">
          <span class="label">Benutzername</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            maxlength="20"
            autofocus
          />
          <small v-if="touched && errors.username" class="error">{{ errors.username }}</small>
        </label>
        <label class="field">
          <span class="label">Passwort</span>
          <input
            v-model="form.password"
            type="password"
            :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
          />
          <small v-if="touched && errors.password" class="error">{{ errors.password }}</small>
        </label>
        <label v-if="mode === 'register'" class="field">
          <span class="label">Passwort wiederholen</span>
          <input v-model="form.repeat" type="password" autocomplete="new-password" />
          <small v-if="touched && errors.repeat" class="error">{{ errors.repeat }}</small>
        </label>

        <p v-if="serverError" class="notice" role="alert">{{ serverError }}</p>

        <button type="submit" class="cta" :disabled="busy">
          <span>
            {{ busy ? 'Einen Moment …' : mode === 'register' ? 'Konto erstellen' : 'Anmelden' }}
          </span>
          <span class="cta-arrow" aria-hidden="true">→</span>
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 440px);
  align-items: center;
  gap: 64px;
  max-width: 1040px;
  min-height: 100svh;
  margin: 0 auto;
  padding: 48px 32px;
}

.intro {
  display: grid;
  justify-items: start;
  gap: 22px;
}

.intro h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.5rem);
  font-weight: 600;
  line-height: 0.92;
  letter-spacing: -0.05em;
}

.intro p {
  max-width: 380px;
  margin: 0;
  font-size: 1.05rem;
  color: var(--text-muted);
}

.card {
  align-self: center;
}

.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid var(--line);
}

.tabs button {
  position: relative;
  padding: 16px;
  border: 0;
  background: none;
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.15s;
}

.tabs button::after {
  content: '';
  position: absolute;
  right: 22px;
  bottom: -1px;
  left: 22px;
  height: 2px;
  background: var(--text);
  transform: scaleX(0);
  transition: transform 0.2s;
}

.tabs button.active {
  color: var(--text);
}

.tabs button.active::after {
  transform: scaleX(1);
}

.form {
  display: grid;
  gap: 16px;
}

.lead {
  margin: 0 0 4px;
  color: var(--text-muted);
}

.field {
  display: grid;
  gap: 8px;
}

input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: #0c0c0e;
  color: var(--text);
  font: inherit;
}

input:focus {
  outline: none;
  border-color: var(--text-muted);
}

.error {
  font-size: 0.8rem;
}

.error::before {
  content: '× ';
  color: var(--text-muted);
}

.notice {
  margin: 0;
  padding: 10px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  font-size: 0.88rem;
}

.form .cta {
  justify-content: space-between;
  margin-top: 6px;
  box-shadow: none;
}

@media (max-width: 820px) {
  .auth-page {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 32px 16px;
  }
}
</style>
