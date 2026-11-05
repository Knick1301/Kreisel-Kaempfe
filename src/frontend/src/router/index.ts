import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { auth } from '@/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: 'Startseite' } },
    {
      path: '/anmelden',
      name: 'anmelden',
      component: () => import('@/views/AnmeldenView.vue'),
      meta: { title: 'Anmelden', public: true },
    },
    {
      path: '/baukasten',
      name: 'baukasten',
      component: () => import('@/views/BaukastenView.vue'),
      meta: { title: 'Baukasten' },
    },
    {
      path: '/rangliste',
      name: 'rangliste',
      component: () => import('@/views/RanglisteView.vue'),
      meta: { title: 'Rangliste' },
    },
    {
      path: '/lobby',
      name: 'lobby',
      component: () => import('@/views/LobbyView.vue'),
      meta: { title: 'Lobby' },
    },
    {
      path: '/profil',
      name: 'profil',
      component: () => import('@/views/ProfilView.vue'),
      meta: { title: 'Profil' },
    },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !auth.loggedIn) {
    return { name: 'anmelden', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.name === 'anmelden' && auth.loggedIn) return { name: 'home' }
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? ''} · Kreisel Kämpfer`
})

export default router
