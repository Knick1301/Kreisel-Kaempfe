import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

const ComingSoonView = () => import('@/views/ComingSoonView.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { title: 'Startseite' } },
    {
      path: '/baukasten',
      name: 'baukasten',
      component: ComingSoonView,
      meta: { title: 'Baukasten' },
    },
    {
      path: '/rangliste',
      name: 'rangliste',
      component: ComingSoonView,
      meta: { title: 'Rangliste' },
    },
    { path: '/profil', name: 'profil', component: ComingSoonView, meta: { title: 'Profil' } },
  ],
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? ''} · Kreisel Kämpfer`
})

export default router
