import { createRouter, createWebHistory } from 'vue-router';
import HomeView from './views/HomeView.vue';
import RoomLobbyView from './views/RoomLobbyView.vue';
import GameView from './views/GameView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/rooms/:roomId', name: 'room', component: RoomLobbyView, props: true },
    { path: '/sessions/:sessionId', name: 'session', component: GameView, props: true },
  ],
});

export default router;
