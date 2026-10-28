<script setup lang="ts">
const links = [
  { to: '/', label: 'Startseite' },
  { to: '/baukasten', label: 'Baukasten' },
  { to: '/rangliste', label: 'Rangliste' },
]
</script>

<template>
  <header class="header">
    <RouterLink to="/" class="logo">Kreisel<br />Kämpfer</RouterLink>

    <nav class="nav">
      <RouterLink
        v-for="link in links"
        :key="link.to"
        :to="link.to"
        class="nav-link"
        exact-active-class="active"
      >
        {{ link.label }}
      </RouterLink>
    </nav>

    <RouterLink to="/profil" class="avatar" aria-label="Profil" exact-active-class="active">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
        <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </svg>
    </RouterLink>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 28px;
  background: rgba(11, 23, 48, 0.88);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
}

.logo {
  font-size: 1.45rem;
  font-weight: 800;
  line-height: 1.05;
  letter-spacing: -0.01em;
}

.nav {
  flex: 1;
  display: flex;
  justify-content: space-around;
  max-width: 560px;
  margin: 0 auto;
}

.nav-link {
  padding: 8px 22px;
  border-radius: var(--radius-sm);
  font-weight: 500;
  color: var(--text-muted);
  transition:
    background-color 0.2s,
    color 0.2s;
}

.nav-link:hover {
  color: var(--text);
  background: rgba(38, 89, 166, 0.25);
}

.nav-link.active {
  color: var(--text);
  background: var(--primary);
  box-shadow: 0 0 18px var(--accent-soft);
}

.avatar {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  border-radius: 50%;
  color: var(--text-muted);
  background: radial-gradient(circle at 30% 25%, #2a4a80, #0d1a33 70%);
  border: 2px solid var(--border);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.avatar svg {
  width: 26px;
  height: 26px;
}

.avatar:hover,
.avatar.active {
  border-color: var(--accent);
  box-shadow: 0 0 16px var(--accent-soft);
  color: var(--text);
}

@media (max-width: 640px) {
  .header {
    flex-wrap: wrap;
    gap: 8px 12px;
    padding: 10px 16px;
  }

  .logo {
    font-size: 1.15rem;
  }

  .avatar {
    width: 42px;
    height: 42px;
    margin-left: auto;
  }

  .nav {
    order: 3;
    flex-basis: 100%;
    justify-content: space-between;
  }

  .nav-link {
    padding: 6px 12px;
    font-size: 0.9rem;
  }
}
</style>
