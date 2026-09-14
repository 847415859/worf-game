-- AI狼人杀 MVP 数据库 Schema
-- 对应需求文档第7章，MVP阶段仅落地角色人格、角色/局型定义、房间与对局核心表。
-- 关系(character_relationship)/记忆(character_memory)/成长(character_growth_*)/
-- 观众高光(game_highlight)/孵化(is_incubation) 等表留到 V1/V2 阶段接入，见文档12节路线图。
--
-- 每张表都用 COMMENT 写了详细说明（表级用 CREATE TABLE ... COMMENT=，字段级用列定义后的 COMMENT），
-- 这些注释会持久化在数据库元数据里，执行 DESCRIBE/SHOW CREATE TABLE 或用图形化工具查看时都能看到，
-- 不是只存在于这份源文件里的普通 SQL 注释。

CREATE DATABASE IF NOT EXISTS wolf_game DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wolf_game;

-- ============================================================
-- app_user：极简用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS app_user (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID，被game_player.user_id引用',
  nickname          VARCHAR(64) NOT NULL COMMENT '玩家昵称，加入房间时填写，无需注册/登录',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次加入房间时创建的时间'
) COMMENT='极简用户表：MVP不做完整账号体系（无密码/无登录态），仅在真人玩家加入房间时按昵称登记一条记录，满足game_player.user_id外键需要，未来接入正式账号体系时可平滑替换';

-- ============================================================
-- ai_character：AI角色（人格）主表——对应文档4.1/7.1节
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_character (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'AI角色ID，跨对局持久不变，是"演员"本身而非某一局的临时身份',
  name              VARCHAR(64)  NOT NULL COMMENT '角色显示名，如"周明远"',
  avatar_url        VARCHAR(255) COMMENT '头像图片URL，MVP阶段可为空（前端用文字头像占位）',
  backstory         TEXT COMMENT '人物小传，自然语言描述性格/说话习惯/与其他角色的恩怨，会直接拼进AI的System Prompt',
  personality_vector JSON NOT NULL COMMENT '结构化人格向量，JSON对象，8个0-100的整数字段：extroversion外向性/aggression攻击性/logic_rigor逻辑严谨度/deception欺骗倾向/conformity从众性/emotional_stability情绪稳定性/grudge记仇强度/humor幽默感，见文档4.1节',
  speech_style      JSON COMMENT '说话风格参数，JSON对象：catchphrases(口头禅字符串数组)/sentence_length(short|medium|long)/emoji_usage(low|medium|high)，用于让不同角色"一听就能听出来是谁"',
  overall_score     INT DEFAULT 10 COMMENT '综合评分缓存字段，MVP阶段未接入真正的成长复盘系统，暂为固定初始值，不参与游戏逻辑判断',
  tier_key          VARCHAR(16) DEFAULT 'bronze' COMMENT '段位（当前仅使用bronze，成长体系V2上线后启用silver/gold/diamond/king等分层），对应3.2节房间配置里的auto_match_tier自动匹配依据',
  status            TINYINT DEFAULT 1 COMMENT '1=启用，可被匹配入座；0=封禁/下线，不再进入新对局但历史数据保留',
  source            VARCHAR(16) DEFAULT 'system' COMMENT '角色来源：system=内置种子角色；user_created=玩家自建角色（UGC，V3路线图规划，MVP阶段未开放前端入口）',
  created_by        BIGINT COMMENT '若source=user_created，记录创建者的app_user.id；系统内置角色为空',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '角色创建时间',
  updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '角色资料最后修改时间'
) COMMENT='AI角色人格主表：每一行是一个拥有独立人设、跨对局持久存在的"数字演员"，与某一局游戏里临时分配到的身份(werewolf/seer等)是两回事——同一个AI角色这局可能是狼人，下局可能是女巫，见文档第2章术语定义';

-- ============================================================
-- role_definition：狼人杀角色/技能定义表——数据驱动，替代硬编码枚举，对应3.4/7.6节
-- ============================================================
CREATE TABLE IF NOT EXISTS role_definition (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色定义ID',
  role_key          VARCHAR(32) NOT NULL UNIQUE COMMENT '角色唯一标识，被game_player.in_game_role/room_template.role_composition/game_event_log.claimed_role引用。当前已实现：werewolf狼人/seer预言家/witch女巫/hunter猎人/villager平民/idiot白痴',
  role_name         VARCHAR(32) NOT NULL COMMENT '角色中文展示名，如"预言家"',
  camp              VARCHAR(16) NOT NULL COMMENT '所属阵营：werewolf=狼人阵营；good=好人阵营（含神职与平民）。第三方阵营(third_party)预留但当前未启用任何角色',
  is_god_role       TINYINT DEFAULT 0 COMMENT '是否为"神职"（好人阵营里拥有主动技能的角色，如预言家/女巫/猎人/白痴），1=是，0=否（平民）。屠边胜负判定用它来区分"神职全灭"和"平民全灭"两种狼人获胜条件，见checkWinCondition',
  skill_config      JSON COMMENT '技能配置，数据驱动对局引擎的技能结算逻辑，不需要为新角色改代码。常规夜间技能形如{"night_action":{"order":显示行动顺序,"action_type":"kill|check_identity|use_potion","frequency":"every_night|limited","result_visible_scope":"self|werewolf_team",...}}；死亡触发型技能形如{"trigger_action":{"trigger_event":"on_death","condition":"death_cause != poison","action_type":"shoot"}}；无主动技能角色(如平民/白痴)此字段为NULL',
  description       VARCHAR(255) COMMENT '技能一句话说明，供管理后台/前端展示',
  status            TINYINT DEFAULT 1 COMMENT '1=当前版本启用；0=未启用/灰度中，用于新角色逐步上线',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='角色定义表：狼人杀的每个身份是什么、属于哪个阵营、有什么技能——全部定义成数据而不是写死在代码里，新增一个角色（如骑士/禁言长老）原则上只需插入一条记录+配置skill_config JSON，见文档3.4节';

-- ============================================================
-- room_template：局型模板表——常见人数对应的角色搭配，对应3.5/7.7节
-- ============================================================
CREATE TABLE IF NOT EXISTS room_template (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板ID，被game_room.template_id引用',
  template_name     VARCHAR(64) NOT NULL COMMENT '模板展示名，如"经典9人局""经典12人局"',
  total_seats       INT NOT NULL COMMENT '本模板固定的总席位数，必须等于role_composition里所有count之和',
  role_composition  JSON NOT NULL COMMENT '角色搭配，JSON数组，每项形如{"role_key":"werewolf","count":3}，role_key引用role_definition.role_key；开局时按此展开成角色列表再随机分配座位',
  template_type     VARCHAR(16) DEFAULT 'system' COMMENT 'system=内置标准局模板；custom=用户自建并保存的模板（当前后端已支持存储，前端尚未开放"保存为模板"入口）',
  created_by        BIGINT COMMENT '若template_type=custom，记录创建者的app_user.id',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '模板创建时间'
) COMMENT='标准局模板表：把"多少人配多少神职多少狼"这种搭配预先存成可复用的数据，开局时一键套用，也支持完全不选模板、手动自定义role_board，见文档3.4/3.5节';

-- ============================================================
-- game_room：房间表——一次开局配置，对应3.2/7.8节
-- ============================================================
CREATE TABLE IF NOT EXISTS game_room (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '房间ID',
  room_name         VARCHAR(64) COMMENT '房间名称，房主自定义，纯展示用途',
  mode              VARCHAR(16) NOT NULL COMMENT '开局模式：human_ai_mixed=人机混战；all_ai=全AI观赏局；custom=自定义席位与人数比例。三种模式在数据结构上不做区分，本质是seat_config取值不同的同一套房间配置，见文档3.1节',
  template_id       BIGINT COMMENT '引用room_template.id；为空表示未使用模板、完全自定义role_board',
  role_board        JSON NOT NULL COMMENT '本局最终生效的角色搭配（复制自模板或手动指定），结构同room_template.role_composition，即[{"role_key":...,"count":...}]',
  seat_config       JSON COMMENT '开局前的席位预分配快照，JSON数组，每项形如{"seat_no":1,"occupant_type":"human|ai","user_id":null,"character_id":null,"auto_match_tier":"bronze"}；human席位的user_id在玩家/api/rooms/{id}/join后回填，ai席位的character_id可预先指定或留空走auto_match_tier自动匹配，见文档3.2节',
  rule_config       JSON COMMENT '本局生效的规则基准配置快照，JSON对象，字段含义见backend的RuleConfig.java（警长票权/女巫用药限制/遗言策略/平票处理/非理性系数/超时兜底等，均对应需求文档3.2/14.2节的规则版本声明），开局时从RuleConfig默认值出发、按需覆盖后固化存储，同一局中途不再改变',
  spectator_public  TINYINT DEFAULT 1 COMMENT '1=允许非参与者围观本局；0=仅参与者可见（当前前端未做强制校验，字段已预留）',
  show_ai_inner_monologue TINYINT DEFAULT 0 COMMENT '观众是否可见AI的内心OS（内部推理链）。人机混战局(存在human席位)建库时会被RoomService强制置0，防止真人玩家或观众看到AI推理等于开天眼作弊，见文档8.4节',
  status            VARCHAR(24) DEFAULT 'created' COMMENT '房间生命周期状态：created=已建房未开局(理论初始值，当前RoomService建房后立即置为seat_filling)；seat_filling=等待真人玩家加入席位；in_progress=已点击开始、对局进行中；settled=对局已结算结束',
  created_by        BIGINT COMMENT '房主的app_user.id',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '建房时间'
) COMMENT='房间表：记录一次"开局配置"——角色板、席位安排、规则参数的组合，是game_session的上一层容器；一个房间理论上可以对应多个session（重开一局），但当前前端实现是房间与对局一一对应的简化用法';

-- ============================================================
-- game_session：对局会话表——房间实际跑起来的一局游戏，对应7.9节
-- ============================================================
CREATE TABLE IF NOT EXISTS game_session (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对局ID，即前端URL /sessions/{id} 与WebSocket /ws/sessions/{id} 中的sessionId',
  room_id           BIGINT NOT NULL COMMENT '所属房间，引用game_room.id',
  day_count         INT DEFAULT 0 COMMENT '当前天数（第几天），持久化的是"最后一次落库时"的快照值——对局进行中真正实时的天数以内存态RunningGame.dayCount为准，只有settleGame()结算时才会把最终值写回这个字段，见SessionQueryService里"优先读内存态"的说明',
  phase             VARCHAR(24) COMMENT '当前阶段快照，持久化时机同day_count（对局进行中以内存态RunningGame.phase为准）。取值：role_assigning角色分配/sheriff_election警长竞选/night夜晚/day_announce公布死讯/day_discuss白天讨论/day_vote投票/day_pk_revote平票PK重投/settling结算中/settled已结束',
  sheriff_player_id BIGINT COMMENT '当前警长的game_player.id快照，持久化时机同上；对局进行中以内存态RunningGame.sheriffPlayerId为准。为空表示未产生警长、警徽已被销毁、或未开启警长机制',
  status            VARCHAR(16) DEFAULT 'running' COMMENT '对局状态：running=进行中；settled=已结算（正常结束/胜负判定/房主强制结束(winner_camp=aborted)三种情况都会落到这个状态）',
  winner_camp       VARCHAR(16) COMMENT '获胜阵营，仅settled状态有值：werewolf=狼人屠边获胜；good=好人放逐所有狼人获胜；aborted=对局被房主/管理员强制结束，未分出胜负',
  started_at        DATETIME COMMENT '对局开始时间（房主点击"开始游戏"的时刻）',
  ended_at          DATETIME COMMENT '对局结算时间'
) COMMENT='对局会话表：一次真正跑起来的游戏进程。对局运行期间大量状态(day_count/phase/sheriff_player_id等)以内存态RunningGame为准、只在结算时统一落库，是为了避免每个回合都写DB，见GameEngineService的实现说明';

-- ============================================================
-- game_player：对局内玩家席位表——人类/AI统一建模，对应7.10节
-- ============================================================
CREATE TABLE IF NOT EXISTS game_player (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '席位ID，即前端/接口里的playerId，是对局内引用一个参与者的唯一方式（不直接用character_id或user_id，因为同一角色/用户不同局的id含义不同）',
  session_id        BIGINT NOT NULL COMMENT '所属对局，引用game_session.id；几乎所有查询都必须带上此过滤条件，绝不能只按character_id/user_id查，否则会跨局窜话，见文档6.4节上下文隔离要求',
  seat_no           INT NOT NULL COMMENT '座位号(1..N)，同一session内唯一，决定发言顺序、警长左右手位等',
  is_ai             TINYINT NOT NULL COMMENT '1=AI席位（character_id有效）；0=真人席位（user_id有效）',
  character_id      BIGINT COMMENT 'is_ai=1时有效，引用ai_character.id，记录本局由哪个AI角色出演该席位',
  user_id           BIGINT COMMENT 'is_ai=0时有效，引用app_user.id',
  in_game_role      VARCHAR(16) COMMENT '本局真实身份(狼人杀角色)，引用role_definition.role_key，开局时随机分配，游戏过程中除白痴翻牌等特殊规则外不公开给其他玩家',
  camp              VARCHAR(16) COMMENT '真实阵营，冗余自role_definition.camp，避免每次判定胜负都要JOIN查角色定义表',
  is_alive          TINYINT DEFAULT 1 COMMENT '1=存活；0=已出局（含各种死因，具体看eliminated_reason）',
  eliminated_day    INT COMMENT '出局发生的天数(day_count)，配合eliminated_reason用于判断遗言/警徽移交/猎人开枪等后续流程是否触发',
  eliminated_reason VARCHAR(32) COMMENT '出局原因：wolf_kill=夜间被狼人刀杀；poison=被女巫毒杀；vote_out=白天被投票放逐；hunter_shot=被猎人开枪带走；self_destruct=狼人白天自爆。不同死因对应不同的遗言/技能触发规则，见3.8/3.9.2节',
  is_revealed       TINYINT DEFAULT 0 COMMENT '身份是否已被强制公开（目前唯一场景：白痴被投票出局后翻牌免死，永久失去投票权但身份对所有人公开），1=已公开，查询时据此覆盖常规的身份可见性规则'
) COMMENT='对局内玩家席位表：把人类玩家和AI角色用同一套结构统一建模成"席位"，屏蔽掉背后是人还是AI的差异，游戏引擎/事件日志一律只认这张表的id（playerId）';

-- ============================================================
-- game_event_log：对局事件日志——发言/投票/技能/系统公告全量记录，对应7.11节
-- ============================================================
CREATE TABLE IF NOT EXISTS game_event_log (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '事件ID，前端按此升序展示时间线、视频生成服务按此顺序回放',
  session_id        BIGINT NOT NULL COMMENT '所属对局，引用game_session.id；查询"本局事件窗口"必须显式带上此过滤条件，见文档6.4节',
  day               INT NOT NULL COMMENT '事件发生时的天数(day_count)快照',
  phase             VARCHAR(24) NOT NULL COMMENT '事件发生时的阶段快照，取值同game_session.phase',
  actor_player_id   BIGINT NOT NULL COMMENT '事件的"发出方"，引用game_player.id。系统旁白类事件(death_announcement/vote_result/settlement/game_paused/game_resumed/night_announcement)借用第一个玩家的id满足NOT NULL约束，前端/回放展示时会特殊处理成"系统"而不是该玩家说的话，见EventBroadcastService.SYSTEM_EVENT_TYPES',
  event_type        VARCHAR(32) NOT NULL COMMENT '事件类型，当前已实现：speech发言/vote投票/night_action夜间行动/last_words遗言/sheriff_campaign_speech警长竞选发言/sheriff_vote警长竞选投票/sheriff_elected警长当选/pk_speech平票PK发言/pk_revote平票PK重投/vote_result投票结果/death_announcement死亡公告/badge_transfer警徽移交/badge_destroyed警徽销毁/skill_trigger技能触发(猎人开枪等)/settlement最终结算公告/role_assigned开局身份分配(仅本人可见)/inner_monologue内心OS(仅上帝视角可见)/game_paused对局暂停/game_resumed对局恢复/night_announcement法官夜间流程播报/idiot_revealed白痴翻牌',
  target_player_id  BIGINT COMMENT '事件指向的目标玩家(如投票对象/技能目标/开枪目标)，引用game_player.id；纯发言类事件无目标时为空',
  claimed_role      VARCHAR(16) COMMENT '发言中的"自称身份"(悍跳/倒钩等战术用)，与actor真实身份(game_player.in_game_role)分离存储，用于复盘"悍跳是否被识破"，见文档5.4节',
  content           TEXT COMMENT '事件文本内容：发言原文/系统公告文案/技能结果描述等',
  visible_scope     VARCHAR(16) DEFAULT 'public' COMMENT '可见范围，决定哪些客户端连接能收到推送、哪些角色的Prompt事件窗口能读到这条：public=所有人可见；werewolf_team=仅狼人阵营可见(如夜间刀人商议)；private_to_actor=仅事件发出者本人可见(如预言家查验结果)；god_view_only=仅开启内心OS的上帝视角可见(AI内部推理链)，见文档8.4节防作弊设计',
  is_fallback       TINYINT DEFAULT 0 COMMENT '是否为内容审核拦截或AI调用超时/失败后的安全兜底内容（而非AI/玩家的真实原始输出），1=是兜底，供事后分析AI稳定性，见文档8.7/8.8节',
  created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '事件写入时间，同一session内可配合id排序作为时间线'
) COMMENT='对局事件日志：全量记录一局游戏里发生的每一件事，是Prompt组装"本局事件窗口"的数据来源、前端对局记录展示的数据来源、也是对局视频生成服务(video-service)回放渲染的唯一数据来源';

-- 索引，见文档7.15节
CREATE INDEX idx_event_session_time ON game_event_log (session_id, created_at);
CREATE INDEX idx_player_session ON game_player (session_id);
