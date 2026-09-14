# AI狼人杀

一个"AI玩家会记仇、会变强"的狼人杀平台（MVP阶段）。AI角色不是简单的规则机器人，而是一批拥有独立人设、说话风格的数字玩家；本仓库当前实现的是可玩通的核心对局引擎 + 真实LLM驱动的AI决策 + 对局回放视频生成，长期记忆/社交关系/成长系统等留待后续迭代（见路线图）。

完整产品设计见 [`AI狼人杀_产品设计与需求文档.md`](AI狼人杀_产品设计与需求文档.md)（本README只讲"现在跑起来的是什么、怎么跑"）。

## 架构总览

四个独立服务 + 一个MySQL数据库，彼此用HTTP/WebSocket通信：

```
frontend (Vue3/Vite, :5173)
   │  REST + WebSocket
   ▼
backend (Spring Boot, :8080) ──── MySQL (wolf_game)
   │  HTTP（每回合决策请求）        │  HTTP（拉取整局事件回放）
   ▼                              ▼
ai-service (NestJS, :3100)    video-service (Express, :3200)
   │
   ▼
DeepSeek API（对外LLM调用）
```

- **backend**：核心业务与对局引擎。房间/席位管理、狼人杀状态机（角色分配→警长竞选→昼夜循环→结算）、技能结算、WebSocket实时推送、按`visible_scope`做的防作弊信息隔离。每局游戏在独立线程内同步驱动，AI回合同步调用ai-service，人类回合阻塞等待前端提交。
- **ai-service**：AI决策编排。收到backend的决策请求后，用"内部推理→对外表达"两段式调用DeepSeek，把人格、当前局面、事件窗口拼进Prompt，解析出发言/投票/技能选择等结构化结果。
- **video-service**：把已结算对局的完整事件日志渲染成一条带逐句配音的回放视频（座位面板 + 系统字幕 + 发言卡片，配音用微软Edge TTS免费在线音色，每个座位固定分配不同音色）。
- **frontend**：建房/加入/对局界面，WebSocket实时同步 + 轮询兜底，行动面板按当前回合类型自动切换UI，对局结算后可一键生成回放视频。

## 目录结构

```
wolf/
├── AI狼人杀_产品设计与需求文档.md   完整产品设计文档
├── db/
│   ├── schema.sql                  建库+建表（含详细字段COMMENT）
│   └── seed.sql                    角色定义/局型模板/AI角色人格种子数据
├── backend/                        Spring Boot 3 + MyBatis-Plus（Java 17）
├── ai-service/                     NestJS，调用DeepSeek
├── video-service/                  Express，回放渲染+TTS配音
├── frontend/                       Vue3 + Vite
├── .env / .env.example             DeepSeek Key、MySQL连接信息
└── .gitignore
```

## 技术栈

| 服务 | 技术 |
|---|---|
| backend | Java 17、Spring Boot 3.2、MyBatis-Plus、MySQL、原生WebSocket |
| ai-service | NestJS、TypeScript、DeepSeek Chat Completions API |
| video-service | Express、TypeScript、`@napi-rs/canvas`（帧渲染）、`node-edge-tts`（配音）、ffmpeg（音视频合成） |
| frontend | Vue 3、Vite、原生WebSocket |
| 数据库 | MySQL 5.7+ |

## 快速开始

### 前置条件

- MySQL（本机跑一个实例即可，Windows下通常已注册为系统服务，无需手动启动）
- JDK 17（backend编译/运行需要；系统默认JDK版本无所谓，运行时单独指定`JAVA_HOME`即可）
- Node.js 18+（ai-service / video-service / frontend）
- ffmpeg 在 PATH 中可用（video-service合成视频需要）

### 1. 初始化数据库

```powershell
mysql -uroot -p < db\schema.sql
mysql -uroot -p < db\seed.sql
```

`schema.sql`会自动创建`wolf_game`库；`seed.sql`会写入内置角色定义（狼人/预言家/女巫/猎人/平民/白痴）、三套标准局模板（经典9/10/12人局）与12个AI角色人格。

### 2. 配置环境变量

复制`.env.example`为`.env`并填入：

```
DEEPSEEK_API_KEY=你的DeepSeek密钥
MYSQL_USERNAME=root
MYSQL_PASSWORD=你的MySQL密码
```

backend单独读取`backend\.env`（需要额外复制一份到该目录，或建一个软链接）；ai-service从上一级目录读取`wolf\.env`；video-service不依赖`.env`，直接用环境变量默认值。

### 3. 依次启动四个服务（各开一个终端窗口）

**backend**（需要JDK 17）：

```powershell
cd backend
$env:JAVA_HOME = "你的JDK17安装路径"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run
```

**ai-service**：

```powershell
cd ai-service
npx nest start
```

**video-service**：

```powershell
cd video-service
npm run build
npm run start
```

**frontend**：

```powershell
cd frontend
npx vite
```

打开 http://localhost:5173 即可建房开局。只想跑通房间/对局流程，起backend就够；要AI玩家真正说话决策，需要ai-service也在跑；video-service只有点"生成对局视频"时才用得到。

## 核心功能清单（当前已实现）

- **开局配置**：人机混战 / 全AI观赏 / 自定义三种模式，角色板可选模板或完全自定义，AI席位支持指定具体角色或按段位自动匹配
- **狼人杀标准规则引擎**：警长竞选（含平票处理）、夜间技能收集式结算（先收集所有目标再统一结算，避免顺序覆盖歧义）、遗言规则（含"仅首夜夜间死亡有遗言"的竞技规则）、平票PK重投、屠边胜负判定（好人放逐全部狼人 / 狼人杀光全部神职或全部平民）
- **已实现角色**：狼人、预言家、女巫（含同晚双药互斥、首夜禁自救）、猎人（含被毒杀不能开枪）、白痴（投票出局翻牌免死、永久失去投票权）、平民；狼人可在白天自爆（终止当日流程、警上自爆强制毁徽）
- **信息隔离**：按`visible_scope`（public/狼人阵营内部/仅本人/仅上帝视角）严格过滤每个客户端能收到的事件，人机混战局强制关闭AI内心OS展示
- **AI两段式决策**：内部推理（不对外可见）→ 对外表达（按人格/说话风格包装），可选择悍跳/倒钩等自称身份战术
- **容错**：AI调用失败重试+安全兜底文案，人类超时自动弃权/沉默，单局最长时长兜底
- **对局中控制**：暂停/恢复/强制结束
- **对局回放视频生成**：结算后一键生成带逐句配音的mp4回放

## 数据库表结构

8张表，字段均写了详细`COMMENT`（`DESCRIBE 表名`或`SHOW CREATE TABLE 表名`可直接查看）：

| 表 | 说明 |
|---|---|
| `app_user` | 极简用户表，仅按昵称登记真人玩家 |
| `ai_character` | AI角色人格主表，跨对局持久 |
| `role_definition` | 数据驱动的角色/技能定义 |
| `room_template` | 标准局模板（角色搭配） |
| `game_room` | 一次开局配置 |
| `game_session` | 实际跑起来的一局对局 |
| `game_player` | 对局内席位（人类/AI统一建模） |
| `game_event_log` | 全量事件日志，是Prompt组装/前端展示/视频生成的唯一数据来源 |

## 主要API

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/characters` | GET | AI角色库列表 |
| `/api/role-definitions` `/api/room-templates` | GET | 角色定义/局型模板列表 |
| `/api/rooms` | POST | 创建房间 |
| `/api/rooms/{roomId}` | GET | 房间详情 |
| `/api/rooms/{roomId}/join` | POST | 真人玩家加入席位 |
| `/api/rooms/{roomId}/start` | POST | 开局 |
| `/api/rooms/{roomId}/session` | GET | 查询本房间当前/最近一局的sessionId |
| `/api/sessions/{sessionId}/state` | GET | 拉取当前局面快照（按调用者权限过滤可见信息） |
| `/api/sessions/{sessionId}/actions` | POST | 提交人类玩家的发言/投票/技能等行动 |
| `/api/sessions/{sessionId}/pause` `/resume` `/end` | POST | 暂停/恢复/强制结束对局 |
| `/api/sessions/{sessionId}/replay` | GET | 对局全量回放数据 |
| `/ws/sessions/{sessionId}` | WebSocket | 实时事件推送 |
| `/videos/{sessionId}/generate` | POST（video-service） | 触发对局回放视频生成 |
| `/videos/{jobId}/status` `/download` | GET（video-service） | 查询生成进度/下载视频 |

## 已知限制（MVP边界）

- **无账号体系**：真人玩家只按昵称登记，无密码/登录态；对局暂停/结束等控制目前任何人可操作，无房主权限校验
- **AI决策为串行调用**：一局内多个AI的回合按座位顺序依次调用DeepSeek，未做并发优化，人数多时单个昼夜循环可能耗时较长
- **长期记忆/社交关系/成长体系未接入**：AI角色目前每局都是"重新认识"的状态，人设文档里的beef/成长机制是后续路线图（V1/V2），当前只有角色人格本身跨局持久
- **内容审核未接入**：文档里提到的敏感词/模型自审拦截层尚未实现，仅有AI调用失败/超时的兜底逻辑
