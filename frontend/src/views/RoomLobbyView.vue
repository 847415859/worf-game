<template>
  <div class="lobby">
    <div class="card" v-if="room">
      <h2>{{ room.roomName || ('房间#' + room.id) }}</h2>
      <p class="muted">状态：{{ room.status }} · 模式：{{ room.mode }}</p>

      <div class="form-row" v-if="!myUserId()">
        <label>你的昵称</label>
        <input v-model="nickname" placeholder="玩家" />
      </div>

      <div class="seats">
        <div v-for="seat in seats" :key="seat.seat_no" class="seat" :class="{ filled: isFilled(seat) }">
          <div class="seat-no">{{ seat.seat_no }}号</div>
          <div class="seat-type">{{ seat.occupant_type === 'human' ? '真人' : 'AI' }}</div>
          <button v-if="seat.occupant_type === 'human' && !seat.user_id" @click="joinSeat(seat)">
            加入
          </button>
          <div v-else-if="seat.occupant_type === 'human'" class="seat-filled-label">已加入</div>
        </div>
      </div>

      <div class="actions">
        <button :disabled="!allHumanFilled || starting" @click="startGame">
          {{ starting ? '开局中...' : '开始游戏' }}
        </button>
        <span v-if="!allHumanFilled" class="muted">等待所有真人玩家加入…</span>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
    <div v-else class="card">加载中...</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '../api';

const props = defineProps({ roomId: [String, Number] });
const route = useRoute();
const router = useRouter();

const room = ref(null);
const error = ref('');
const starting = ref(false);
const nickname = ref(localStorage.getItem('wolf_nickname') || '玩家' + Math.floor(Math.random() * 1000));
let pollTimer = null;

const seats = computed(() => (room.value ? JSON.parse(JSON.stringify(room.value.seatConfig || [])) : []));
const allHumanFilled = computed(() => seats.value.filter((s) => s.occupant_type === 'human').every((s) => s.user_id));

function isFilled(seat) {
  return seat.occupant_type === 'ai' || !!seat.user_id;
}

function myUserId() {
  return localStorage.getItem('wolf_user_id');
}

async function refresh() {
  try {
    room.value = await api.getRoom(props.roomId);
    if (room.value.status === 'in_progress' || room.value.status === 'settled') {
      const { sessionId } = await api.getRoomSession(props.roomId);
      if (sessionId) {
        router.replace({ name: 'session', params: { sessionId } });
      }
    }
  } catch (e) {
    error.value = e.message;
  }
}

async function joinSeat(seat) {
  error.value = '';
  try {
    localStorage.setItem('wolf_nickname', nickname.value);
    const payload = { seatNo: seat.seat_no, nickname: nickname.value };
    const existingUserId = myUserId();
    if (existingUserId) payload.userId = Number(existingUserId);
    const result = await api.joinRoom(props.roomId, payload);
    localStorage.setItem('wolf_user_id', String(result.user_id));
    localStorage.setItem(`wolf_seat_${props.roomId}`, String(result.seat_no));
    await refresh();
  } catch (e) {
    error.value = e.message;
  }
}

async function startGame() {
  error.value = '';
  starting.value = true;
  try {
    const { sessionId } = await api.startRoom(props.roomId);
    router.push({ name: 'session', params: { sessionId } });
  } catch (e) {
    error.value = e.message;
    starting.value = false;
  }
}

onMounted(() => {
  refresh();
  pollTimer = setInterval(refresh, 2000);
});
onUnmounted(() => clearInterval(pollTimer));
</script>

<style scoped>
.lobby {
  max-width: 720px;
  margin: 0 auto;
}
.muted {
  color: var(--text-dim);
  font-size: 13px;
}
.form-row {
  margin: 14px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 260px;
}
.seats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 10px;
  margin: 16px 0;
}
.seat {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  text-align: center;
  background: var(--panel-alt);
}
.seat.filled {
  border-color: var(--good);
}
.seat-no {
  font-weight: 700;
}
.seat-type {
  color: var(--text-dim);
  font-size: 12px;
  margin: 4px 0;
}
.seat-filled-label {
  color: var(--good);
  font-size: 12px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.error {
  color: var(--wolf);
  margin-top: 10px;
}
</style>
