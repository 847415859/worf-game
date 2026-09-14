<template>
  <div class="app-shell">
    <header class="topbar">
      <router-link to="/" class="brand">AI狼人杀</router-link>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>

<script setup></script>

<style>
:root {
  color-scheme: light;
  --bg: #14171c;
  --panel: #1d2129;
  --panel-alt: #262b35;
  --border: #333947;
  --text: #e8eaed;
  --text-dim: #9aa2b1;
  --accent: #e0574c;
  --good: #4caf82;
  --wolf: #d9534f;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
}

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  padding: 14px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--panel);
}

.brand {
  color: var(--accent);
  font-weight: 700;
  font-size: 20px;
  text-decoration: none;
  letter-spacing: 2px;
}

main {
  flex: 1;
  padding: 24px;
}

button {
  cursor: pointer;
  border: 1px solid var(--border);
  background: var(--panel-alt);
  color: var(--text);
  border-radius: 6px;
  padding: 8px 14px;
  font-size: 14px;
}

button:hover {
  border-color: var(--accent);
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

input, textarea, select {
  background: var(--panel-alt);
  border: 1px solid var(--border);
  color: var(--text);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 14px;
}

.card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px;
}
</style>
