import { createRouter, createWebHistory } from 'vue-router'
import { getRoleHomeRoute, getToken, hasAnyRole } from '../utils/storage'
import LoginView from '../views/LoginView.vue'
import AppLayout from '../layouts/AppLayout.vue'
import HomeView from '../views/HomeView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import UserTicketListView from '../views/user/UserTicketListView.vue'
import UserTicketCreateView from '../views/user/UserTicketCreateView.vue'
import UserTicketDetailView from '../views/user/UserTicketDetailView.vue'
import AgentTicketListView from '../views/agent/AgentTicketListView.vue'
import AgentTicketDetailView from '../views/agent/AgentTicketDetailView.vue'

const routes = [
  { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'home', component: HomeView },
      { path: 'user', name: 'user-home', component: UserTicketListView, meta: { roles: ['USER'] } },
      { path: 'user/tickets/new', name: 'user-ticket-create', component: UserTicketCreateView, meta: { roles: ['USER'] } },
      { path: 'user/tickets/:id', name: 'user-ticket-detail', component: UserTicketDetailView, meta: { roles: ['USER'] } },
      { path: 'agent', name: 'agent-home', component: AgentTicketListView, meta: { roles: ['AGENT'] } },
      { path: 'agent/tickets/:id', name: 'agent-ticket-detail', component: AgentTicketDetailView, meta: { roles: ['AGENT'] } },
      { path: 'admin', name: 'admin-home', component: HomeView, meta: { roles: ['ADMIN'] } },
      { path: 'forbidden', name: 'forbidden', component: ForbiddenView },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const authenticated = Boolean(getToken())
  if (to.meta.guestOnly && authenticated) return getRoleHomeRoute()
  if (to.meta.requiresAuth && !authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'home' && authenticated) return getRoleHomeRoute()
  if (to.meta.roles && !hasAnyRole(to.meta.roles)) return { name: 'forbidden' }
  return true
})

export default router
