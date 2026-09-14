<template>
  <div class="home">
    <section class="card">
      <h2>开一局</h2>
      <div class="form-row">
        <label>房间名称</label>
        <input v-model="roomName" placeholder="6号包间" />
      </div>

      <div class="form-row">
        <label>局型模板</label>
        <select v-model="selectedTemplateId">
          <option v-for="t in templates" :key="t.id" :value="t.id">
            {{ t.templateName }}（{{ t.totalSeats }}人）
          </option>
        </select>
      </div>

      <div class="form-row" v-if="selectedTemplate">
        <label>角色板</label>
        <div class="role-board">
          <span v-for="item in roleComposition" :key="item.role_key" class="role-chip">
            {{ roleLabel(item.role_key) }} x{{ item.count }}
          </span>
        </div>
      </div>

      <div class="form-row" v-if="selectedTemplate">
        <label>真人席位数（其余自动匹配AI）</label>
        <input type="number" v-model.number="humanSeatCount" min="0" :max="selectedTemplate.totalSeats" />
      </div>

      <div class="form-row" v-if="humanSeatCount === 0">
        <label>
          <input type="checkbox" v-model="showInnerMonologue" />
          观众可见AI内心OS（仅全AI观赏局可开启）
        </label>
      </div>

      <button :disabled="!selectedTemplate || creating" @click="createRoom">
        {{ creating ? '创建中...' : '创建房间' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card" style="margin-top: 20px">
      <h2>AI角色库</h2>
      <div class="char-grid">
        <div v-for="c in characters" :key="c.id" class="char-card">
          <div class="char-name">{{ c.name }}</div>
          <div class="char-tier">段位：{{ c.tierKey }}</div>
          <div class="char-backstory">{{ c.backstory }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';

const router = useRouter();
const templates = ref([]);
const roleDefinitions = ref([]);
const characters = ref([]);
const selectedTemplateId = ref(null);
const humanSeatCount = ref(1);
const roomName = ref('6号包间');
const showInnerMonologue = ref(false);
const creating = ref(false);
const error = ref('');

const selectedTemplate = computed(() => templates.value.find((t) => t.id === selectedTemplateId.value));
const roleComposition = computed(() => (selectedTemplate.value ? selectedTemplate.value.roleComposition : []));

function roleLabel(roleKey) {
  const def = roleDefinitions.value.find((r) => r.roleKey === roleKey);
  return def ? def.roleName : roleKey;
}

onMounted(async () => {
  try {
    [templates.value, roleDefinitions.value, characters.value] = await Promise.all([
      api.listRoomTemplates(),
      api.listRoleDefinitions(),
      api.listCharacters(),
    ]);
    if (templates.value.length) selectedTemplateId.value = templates.value[0].id;
  } catch (e) {
    error.value = e.message;
  }
});

async function createRoom() {
  error.value = '';
  creating.value = true;
  try {
    const total = selectedTemplate.value.totalSeats;
    const humanCount = Math.min(Math.max(humanSeatCount.value, 0), total);
    const seats = [];
    for (let i = 1; i <= total; i++) {
      if (i <= humanCount) {
        seats.push({ seat_no: i, occupant_type: 'human' });
      } else {
        seats.push({ seat_no: i, occupant_type: 'ai', auto_match_tier: 'bronze' });
      }
    }
    const room = await api.createRoom({
      roomName: roomName.value,
      mode: humanCount === 0 ? 'all_ai' : 'custom',
      templateId: selectedTemplate.value.id,
      roleBoard: roleComposition.value.map((r) => ({ role_key: r.role_key, count: r.count })),
      seats,
      spectatorPublic: true,
      showAiInnerMonologue: humanCount === 0 && showInnerMonologue.value,
    });
    router.push({ name: 'room', params: { roomId: room.id } });
  } catch (e) {
    error.value = e.message;
  } finally {
    creating.value = false;
  }
}
</script>

<style scoped>
.home {
  max-width: 720px;
  margin: 0 auto;
}
.form-row {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.role-board {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.role-chip {
  background: var(--panel-alt);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 13px;
}
.error {
  color: var(--wolf);
}
.char-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.char-card {
  background: var(--panel-alt);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px;
  font-size: 13px;
}
.char-name {
  font-weight: 700;
  margin-bottom: 4px;
}
.char-tier {
  color: var(--text-dim);
  margin-bottom: 6px;
}
.char-backstory {
  color: var(--text-dim);
  line-height: 1.4;
}
</style>
