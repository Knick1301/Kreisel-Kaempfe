<script setup lang="ts">
import { auth, initials } from '@/auth'
import { computed, watch } from 'vue'
import { me, refreshMe } from '@/stores/me'
import AppLogo from './AppLogo.vue'
import LeagueBadge from './LeagueBadge.vue'

const links = [
  { to: '/', label: 'Startseite' },
  { to: '/baukasten', label: 'Baukasten' },
  { to: '/rangliste', label: 'Rangliste' },
]

const rank = computed(() => me.rank)
const divisionPoints = computed(() =>
  rank.value ? rank.value.rating - rank.value.divisionFloor : 0,
)
const divisionSize = computed(() =>
  rank.value?.pointsToNext != null ? divisionPoints.value + rank.value.pointsToNext : null,
)
const xpProgress = computed(() => {
  const r = rank.value
  if (!r) return 0
  return Math.min(1, (r.xp - r.xpLevelStart) / Math.max(1, r.xpNextLevel - r.xpLevelStart))
})
const RING = 2 * Math.PI * 21

watch(
  () => auth.loggedIn,
  (loggedIn) => {
    if (loggedIn) refreshMe()
  },
  { immediate: true },
)
</script>

<template>
  <header class="header">
    <RouterLink to="/" class="logo" aria-label="Kreisel Kämpfer – Startseite">
      <AppLogo />
      <span class="wordmark">Kreisel<br />Kämpfer</span>
    </RouterLink>

    <nav v-if="auth.loggedIn" class="nav">
      <RouterLink
        v-for="(link, i) in links"
        :key="link.to"
        :to="link.to"
        class="nav-link"
        exact-active-class="active"
      >
        <span class="nav-no">{{ String(i + 1).padStart(2, '0') }}</span>
        {{ link.label }}
      </RouterLink>
    </nav>

    <div v-if="auth.loggedIn" class="me">
      <RouterLink v-if="rank" to="/rangliste" class="league" :title="`${rank.rating} Liga-Punkte`">
        <LeagueBadge :tier="rank.tier" :division="rank.division" :size="26" />
        <span class="league-text">
          <span class="league-top">
            <span>{{ rank.league }}</span>
            <span v-if="divisionSize" class="faint">{{ divisionPoints }}/{{ divisionSize }}</span>
            <span v-else class="faint">{{ rank.rating }}</span>
          </span>
          <span class="league-bar"><span :style="{ width: `${rank.progress}%` }" /></span>
        </span>
      </RouterLink>

      <RouterLink
        to="/profil"
        class="avatar"
        :aria-label="`Profil von ${auth.username}`"
        :title="auth.username"
        exact-active-class="active"
      >
        <svg class="ring" viewBox="0 0 46 46" aria-hidden="true">
          <circle cx="23" cy="23" r="21" class="ring-track" />
          <circle
            cx="23"
            cy="23"
            r="21"
            class="ring-fill"
            :stroke-dasharray="`${RING * xpProgress} ${RING}`"
          />
        </svg>
        <span class="initials">{{ initials }}</span>
        <span v-if="rank" class="level" :title="`Level ${rank.level} · ${rank.xp} XP`">{{
          rank.level
        }}</span>
      </RouterLink>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 56px;
  height: var(--header-h);
  padding: 0 32px;
  background: var(--bg);
  border-bottom: 1px solid var(--line);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wordmark {
  font-size: 1.3rem;
  font-weight: 600;
  line-height: 1.02;
  letter-spacing: -0.03em;
}

.nav {
  display: flex;
  align-self: stretch;
  gap: 8px;
}

.nav-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  font-size: 1.12rem;
  font-weight: 500;
  color: var(--text-muted);
  transition: color 0.15s;
}

.nav-no {
  font-family: var(--font-mono);
  font-size: 0.74rem;
  color: var(--text-faint);
}

.nav-link::after {
  content: '';
  position: absolute;
  right: 18px;
  bottom: -1px;
  left: 18px;
  height: 2px;
  background: var(--text);
  transform: scaleX(0);
  transition: transform 0.2s;
}

.nav-link:hover {
  color: var(--text);
}

.nav-link.active {
  color: var(--text);
}

.nav-link.active .nav-no {
  color: var(--text-muted);
}

.nav-link.active::after {
  transform: scaleX(1);
}

.me {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: auto;
}

.league {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 220px;
  padding: 9px 14px 9px 12px;
  border: 1px solid var(--line);
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  transition: border-color 0.15s;
}

.league-text {
  display: grid;
  flex: 1;
  gap: 8px;
}

.league:hover {
  border-color: var(--line-strong);
}

.league-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.faint {
  color: var(--text-faint);
}

.league-bar {
  height: 3px;
  background: var(--line);
}

.league-bar span {
  display: block;
  height: 100%;
  background: var(--text);
}

.avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
}

.ring {
  position: absolute;
  inset: 0;
  transform: rotate(-90deg);
}

.ring circle {
  fill: none;
  stroke-width: 2;
}

.ring-track {
  stroke: var(--line-strong);
}

.ring-fill {
  stroke: var(--text);
}

.initials {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--surface-raised);
  font-size: 0.95rem;
  font-weight: 600;
  transition: background-color 0.15s;
}

.avatar:hover .initials,
.avatar.active .initials {
  background: var(--line-strong);
}

.level {
  position: absolute;
  right: -4px;
  bottom: -2px;
  padding: 1px 4px;
  border-radius: 2px;
  background: var(--text);
  color: var(--bg);
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
}

@media (max-width: 860px) {
  .league,
  .nav-no {
    display: none;
  }
}

@media (max-width: 640px) {
  .header {
    gap: 12px;
    padding: 0 16px;
  }

  .wordmark {
    display: none;
  }

  .nav {
    gap: 0;
  }

  .nav-link {
    padding: 0 8px;
    font-size: 0.88rem;
  }

  .nav-link::after {
    right: 8px;
    left: 8px;
  }
}
</style>
