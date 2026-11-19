<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <main class="soon">
    <h1>{{ route.meta.title }}</h1>
    <p>Diese Seite ist noch im Bau.</p>
  </main>
</template>

<style scoped>
.soon {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 4px;
  min-height: 60vh;
  padding: 16px;
  text-align: center;
}

h1 {
  margin: 0;
  font-size: 1.8rem;
}

p {
  margin: 0;
  color: var(--text-muted);
}
</style>
