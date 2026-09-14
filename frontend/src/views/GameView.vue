<template>
  <div class="game" v-if="state">
    <div class="top-info card">
      <div>第 {{ state.dayCount }} 天 · {{ phaseLabel(state.phase) }}</div>
      <div v-if="state.status === 'settled'" class="winner">
        对局结束，获胜阵营：{{ winnerLabel(state.winnerCamp) }}
      </div>
      <div v-if="state.status === 'settled'" class="video-panel">
        <button v-if="!videoJob || videoJob.status === 'error'" :disabled="videoGenerating" @click="startVideoGeneration">
          {{ videoJob && videoJob.status === 'error' ? '生成失败，重试' : '生成对局视频' }}
        </button>
        <span v-else-if="videoJob.status === 'running'" class="muted">
          视频生成中 {{ videoJob.done }}/{{ videoJob.total || '?' }}...
        </span>
        <a v-else-if="videoJob.status === 'done'" :href="videoDownloadUrl" target="_blank" class="video-download">
          下载对局视频
        </a>
        <span v-if="videoJob && videoJob.status === 'error'" class="error-text">{{ videoJob.error }}</span>
      </div>
      <div v-if="myPlayer" class="my-role">
        我是 {{ myPlayer.seatNo }} 号（{{ myPlayer.displayName }}）
        <span v-if="state.myInGameRole"> · 身份：{{ roleLabel(state.myInGameRole) }}</span>
      </div>
      <div v-if="state.status !== 'settled'" class="control-panel">
        <span v-if="state.paused" class="paused-badge">已暂停</span>
        <button :disabled="controlBusy" @click="togglePause">
          {{ state.paused ? '继续对局' : '暂停对局' }}
        </button>
        <button :disabled="controlBusy" class="end-btn" @click="handleEndClick">
          {{ endConfirming ? '再点一次确认结束' : '结束对局' }}
        </button>
      </div>
    </div>

    <div class="board card">
      <div v-for="p in state.players" :key="p.playerId" class="seat-card" :class="{ dead: !p.alive, me: p.playerId === myPlayerId }">
        <div class="seat-head">
          <span>{{ p.seatNo }}号</span>
          <span v-if="p.sheriff" class="badge">警长</span>
        </div>
        <div class="seat-name">{{ p.displayName }}<span v-if="p.ai" class="ai-tag">AI</span></div>
        <div class="seat-status">{{ p.alive ? '存活' : '出局(' + reasonLabel(p.eliminatedReason) + ')' }}</div>
        <div v-if="p.inGameRole" class="seat-role">{{ roleLabel(p.inGameRole) }}</div>
      </div>
    </div>

    <div class="main-area">
      <div class="log card">
        <h3>对局记录</h3>
        <div class="log-list" ref="logListEl">
          <div v-for="e in state.recentEvents" :key="e.id" class="log-item">
            <span class="log-meta">[D{{ e.day }} {{ phaseLabel(e.phase) }}]</span>
            <span class="log-actor">{{ e.actorName }}</span>
            <span class="log-type">{{ eventTypeLabel(e.eventType) }}</span>
            <span v-if="e.targetName">→ {{ e.targetName }}</span>
            <span v-if="e.claimedRole" class="log-claim">（自称：{{ roleLabel(e.claimedRole) }}）</span>
            <div v-if="e.content" class="log-content">{{ e.content }}</div>
          </div>
        </div>
      </div>

      <div class="action-panel card">
        <h3>行动面板</h3>
        <div v-if="!pendingAction" class="muted">当前轮不到你行动，耐心等待...</div>
        <div v-else>
          <p class="hint">{{ pendingAction.promptHint }}</p>
          <p class="countdown" :class="{ warn: countdown <= 10 }">剩余 {{ countdown }} 秒（超时将自动弃权/沉默）</p>

          <template v-if="pendingAction.actionType === 'self_destruct_decision'">
            <label class="claim-row">
              <input type="checkbox" v-model="draftSelfDestruct" />
              现在自爆（立即结束白天、进入黑夜，你会当场出局、没有遗言机会）
            </label>
            <textarea v-model="draftContent" rows="4"
                      :placeholder="draftSelfDestruct ? '输入你的自爆宣言...' : '不自爆的话，正常发表你的看法...'"></textarea>
            <div class="claim-row" v-if="!draftSelfDestruct">
              <label>自称身份（可选，用于悍跳等战术）：</label>
              <select v-model="draftClaimedRole">
                <option value="">不声明</option>
                <option v-for="r in roleDefinitions" :key="r.roleKey" :value="r.roleKey">{{ r.roleName }}</option>
              </select>
            </div>
            <button :disabled="submitting" @click="submitSelfDestructDecision">发送</button>
          </template>

          <template v-else-if="isTextAction(pendingAction.actionType)">
            <textarea v-model="draftContent" rows="4" placeholder="输入你要说的话..."></textarea>
            <div class="claim-row">
              <label>自称身份（可选，用于悍跳等战术）：</label>
              <select v-model="draftClaimedRole">
                <option value="">不声明</option>
                <option v-for="r in roleDefinitions" :key="r.roleKey" :value="r.roleKey">{{ r.roleName }}</option>
              </select>
            </div>
            <button :disabled="submitting" @click="submitSpeech">发送</button>
          </template>

          <template v-else-if="pendingAction.candidateTargetPlayerIds && pendingAction.candidateTargetPlayerIds.length">
            <div class="target-list">
              <button v-for="pid in pendingAction.candidateTargetPlayerIds" :key="pid" :disabled="submitting" @click="submitTarget(pid)">
                {{ targetLabel(pid) }}
              </button>
            </div>
            <button :disabled="submitting" @click="submitAbstain">弃权 / 不使用</button>
          </template>

          <template v-else>
            <div class="yes-no">
              <button :disabled="submitting" @click="submitYesNo(true)">是</button>
              <button :disabled="submitting" @click="submitYesNo(false)">否</button>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="card">加载中...</div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { api, WS_BASE } from '../api';

const props = defineProps({ sessionId: [String, Number] });

const state = ref(null);
const roleDefinitions = ref([]);
const myPlayerId = ref(null);
const draftContent = ref('');
const draftClaimedRole = ref('');
const draftSelfDestruct = ref(false);
const submitting = ref(false);
const logListEl = ref(null);
let ws = null;
let pollTimer = null;

const myPlayer = computed(() => state.value?.players.find((p) => p.playerId === myPlayerId.value));
const pendingAction = computed(() => state.value?.pendingAction);
const countdown = ref(0);
let countdownTimer = null;

const controlBusy = ref(false);
const endConfirming = ref(false);
let endConfirmTimer = null;

async function togglePause() {
  controlBusy.value = true;
  try {
    if (state.value.paused) {
      await api.resumeSession(props.sessionId);
    } else {
      await api.pauseSession(props.sessionId);
    }
    await refresh();
  } catch (e) {
    alert(e.message);
  } finally {
    controlBusy.value = false;
  }
}

function handleEndClick() {
  if (!endConfirming.value) {
    endConfirming.value = true;
    clearTimeout(endConfirmTimer);
    endConfirmTimer = setTimeout(() => (endConfirming.value = false), 4000);
    return;
  }
  clearTimeout(endConfirmTimer);
  endConfirming.value = false;
  endGame();
}

async function endGame() {
  controlBusy.value = true;
  try {
    await api.endSession(props.sessionId);
    await refresh();
  } catch (e) {
    alert(e.message);
  } finally {
    controlBusy.value = false;
  }
}

const videoJob = ref(null);
const videoGenerating = computed(() => videoJob.value && videoJob.value.status === 'running');
const videoDownloadUrl = computed(() => (videoJob.value ? api.videoDownloadUrl(videoJob.value.jobId) : null));
let videoPollTimer = null;

async function checkExistingVideoJob() {
  // 页面刷新/重新进入时，如果视频服务里已经有这局的任务（生成中或已完成），直接接上状态，
  // 避免用户看到"生成对局视频"按钮又手滑点一次，白白重跑一遍生成流程
  try {
    const status = await api.getVideoStatus(props.sessionId);
    videoJob.value = status;
    if (status.status === 'running') {
      clearInterval(videoPollTimer);
      videoPollTimer = setInterval(pollVideoStatus, 3000);
    }
  } catch (e) {
    // video-service 没启动或这局还没生成过视频都是正常情况，静默忽略即可
  }
}

async function startVideoGeneration() {
  try {
    const result = await api.generateVideo(props.sessionId);
    videoJob.value = { jobId: result.jobId, status: result.status, done: 0, total: 0 };
    clearInterval(videoPollTimer);
    videoPollTimer = setInterval(pollVideoStatus, 3000);
  } catch (e) {
    videoJob.value = { jobId: String(props.sessionId), status: 'error', error: e.message };
  }
}
async function pollVideoStatus() {
  if (!videoJob.value) return;
  try {
    const status = await api.getVideoStatus(videoJob.value.jobId);
    videoJob.value = status;
    if (status.status !== 'running') clearInterval(videoPollTimer);
  } catch (e) {
    videoJob.value = { ...videoJob.value, status: 'error', error: e.message };
    clearInterval(videoPollTimer);
  }
}

watch(pendingAction, (next, prev) => {
  if (next && !prev) {
    countdown.value = next.timeoutSeconds || 30;
    clearInterval(countdownTimer);
    countdownTimer = setInterval(() => {
      if (countdown.value > 0) countdown.value -= 1;
    }, 1000);
  } else if (!next) {
    clearInterval(countdownTimer);
    countdown.value = 0;
  }
});

function phaseLabel(phase) {
  const map = {
    role_assigning: '角色分配',
    sheriff_election: '警长竞选',
    night: '夜晚',
    day_announce: '公布死讯',
    day_discuss: '白天讨论',
    day_vote: '投票',
    day_pk_revote: 'PK重新投票',
    settling: '结算',
    settled: '已结束',
  };
  return map[phase] || phase;
}
function roleLabel(roleKey) {
  const def = roleDefinitions.value.find((r) => r.roleKey === roleKey);
  return def ? def.roleName : roleKey;
}
function reasonLabel(reason) {
  const map = { wolf_kill: '被狼人杀害', poison: '被毒杀', vote_out: '被投票出局', hunter_shot: '被开枪带走', self_destruct: '自爆' };
  return map[reason] || reason || '';
}
function eventTypeLabel(type) {
  const map = {
    speech: '发言', vote: '投票', night_action: '夜晚行动', last_words: '遗言',
    sheriff_campaign_speech: '竞选发言', sheriff_vote: '警长投票', sheriff_elected: '当选警长',
    pk_speech: 'PK发言', pk_revote: 'PK重投', vote_result: '投票结果', death_announcement: '死亡公告',
    badge_transfer: '警徽移交', badge_destroyed: '警徽销毁', skill_trigger: '技能触发',
    settlement: '结算公告', role_assigned: '身份分配', inner_monologue: '内心OS',
    game_paused: '暂停', game_resumed: '恢复',
    night_announcement: '法官播报', self_destruct: '自爆', idiot_revealed: '白痴翻牌',
  };
  return map[type] || type;
}
function winnerLabel(camp) {
  if (camp === 'werewolf') return '狼人';
  if (camp === 'good') return '好人';
  return '无（对局已被提前结束）';
}
function targetLabel(playerId) {
  const p = state.value?.players.find((pp) => pp.playerId === playerId);
  return p ? `${p.seatNo}号 ${p.displayName}` : playerId;
}
function isTextAction(type) {
  return ['speech', 'last_words', 'sheriff_campaign_speech', 'pk_speech'].includes(type);
}

async function refresh() {
  const data = await api.getState(props.sessionId, myPlayerId.value, false);
  if (myPlayerId.value === null) {
    const seatKey = `wolf_seat_${data.roomId}`;
    const mySeat = Number(localStorage.getItem(seatKey));
    const mine = data.players.find((p) => p.seatNo === mySeat && !p.ai);
    if (mine) {
      myPlayerId.value = mine.playerId;
      const fresh = await api.getState(props.sessionId, myPlayerId.value, false);
      state.value = fresh;
      return;
    }
  }
  state.value = data;
}

async function submitAction(payload) {
  submitting.value = true;
  try {
    await api.submitAction(props.sessionId, { playerId: myPlayerId.value, ...payload });
    draftContent.value = '';
    draftClaimedRole.value = '';
    draftSelfDestruct.value = false;
    await refresh();
  } catch (e) {
    alert(e.message);
  } finally {
    submitting.value = false;
  }
}
function submitSpeech() {
  submitAction({ actionType: pendingAction.value.actionType, content: draftContent.value, claimedRole: draftClaimedRole.value || null });
}
function submitSelfDestructDecision() {
  submitAction({
    actionType: 'self_destruct_decision',
    content: draftContent.value,
    decisionYes: draftSelfDestruct.value,
    claimedRole: draftSelfDestruct.value ? null : draftClaimedRole.value || null,
  });
}
function submitTarget(pid) {
  submitAction({ actionType: pendingAction.value.actionType, targetPlayerId: pid, decisionYes: true });
}
function submitAbstain() {
  submitAction({ actionType: pendingAction.value.actionType, targetPlayerId: null, decisionYes: false });
}
function submitYesNo(yes) {
  submitAction({ actionType: pendingAction.value.actionType, decisionYes: yes });
}

function connectWs() {
  const params = myPlayerId.value ? `?playerId=${myPlayerId.value}` : '';
  ws = new WebSocket(`${WS_BASE}/ws/sessions/${props.sessionId}${params}`);
  ws.onmessage = () => refresh();
  ws.onclose = () => {
    setTimeout(() => {
      if (!document.hidden) connectWs();
    }, 2000);
  };
}

watch(
  () => state.value?.status,
  (status) => {
    if (status === 'settled' && !videoJob.value) checkExistingVideoJob();
  },
);

watch(
  () => state.value?.recentEvents?.length,
  async () => {
    await nextTick();
    if (logListEl.value) logListEl.value.scrollTop = logListEl.value.scrollHeight;
  },
);

onMounted(async () => {
  roleDefinitions.value = await api.listRoleDefinitions();
  await refresh();
  connectWs();
  pollTimer = setInterval(refresh, 5000);
});
onUnmounted(() => {
  if (ws) ws.close();
  clearInterval(pollTimer);
  clearInterval(countdownTimer);
  clearInterval(videoPollTimer);
  clearTimeout(endConfirmTimer);
});
</script>

<style scoped>
.game {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.top-info {
  display: flex;
  gap: 20px;
  align-items: center;
}
.winner {
  color: var(--good);
  font-weight: 700;
}
.control-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.paused-badge {
  color: #f0c419;
  font-weight: 700;
  font-size: 12px;
  border: 1px solid #f0c419;
  border-radius: 999px;
  padding: 2px 10px;
}
.end-btn {
  border-color: var(--wolf);
  color: var(--wolf);
}
.board {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}
.seat-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px;
  background: var(--panel-alt);
  font-size: 13px;
}
.seat-card.dead {
  opacity: 0.45;
}
.seat-card.me {
  border-color: var(--accent);
}
.seat-head {
  display: flex;
  justify-content: space-between;
  font-weight: 700;
}
.badge {
  color: #f0c419;
  font-size: 11px;
}
.ai-tag {
  color: var(--text-dim);
  font-size: 10px;
  margin-left: 4px;
}
.seat-role {
  color: var(--accent);
}
.main-area {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.log-list {
  max-height: 480px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.log-item {
  border-bottom: 1px solid var(--border);
  padding-bottom: 6px;
  font-size: 13px;
}
.log-meta {
  color: var(--text-dim);
  margin-right: 6px;
  font-size: 11px;
}
.log-actor {
  font-weight: 700;
  margin-right: 4px;
}
.log-type {
  color: var(--text-dim);
  margin-right: 4px;
}
.log-claim {
  color: var(--accent);
  font-size: 12px;
}
.log-content {
  margin-top: 4px;
  line-height: 1.4;
}
.action-panel textarea {
  width: 100%;
  resize: vertical;
}
.claim-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
  font-size: 13px;
}
.target-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.yes-no {
  display: flex;
  gap: 10px;
}
.hint {
  color: var(--text-dim);
  font-size: 13px;
}
.countdown {
  font-size: 12px;
  color: var(--good);
  margin: 4px 0 10px;
}
.countdown.warn {
  color: var(--wolf);
  font-weight: 700;
}
.muted {
  color: var(--text-dim);
}
.video-panel {
  display: flex;
  align-items: center;
  gap: 10px;
}
.video-download {
  color: var(--good);
  font-weight: 700;
  text-decoration: none;
  border: 1px solid var(--good);
  border-radius: 6px;
  padding: 6px 14px;
}
.error-text {
  color: var(--wolf);
  font-size: 12px;
}
</style>
