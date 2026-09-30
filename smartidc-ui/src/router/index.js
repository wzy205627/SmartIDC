import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue')
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '监控指挥态势' }
      },
      {
        path: 'asset/rack',
        name: 'AssetRack',
        component: () => import('@/views/asset/rack.vue'),
        meta: { title: '机架拓扑与U位' }
      },
      {
        path: 'ticket',
        name: 'WorkTicketCenter',
        component: () => import('@/views/ticket/index.vue'),
        meta: { title: '排障工单协同中心' }
      }
    ]
  },
  {
    path: '/screen',
    name: 'DigitalTwinScreen',
    component: () => import('@/views/screen/index.vue'),
    meta: { title: '数字孪生动环拓扑大屏' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
