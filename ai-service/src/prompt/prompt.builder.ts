import { Injectable } from '@nestjs/common';
import { AiDecideRequest } from '../agent/dto';

// self_destruct_decision不自爆时，content就是这一轮的正常发言，同样要求完整发言质量而非一句话说明
const SPEECH_LIKE_ACTIONS = new Set(['speech', 'last_words', 'sheriff_campaign_speech', 'pk_speech', 'self_destruct_decision']);

/**
 * 组装需求文档8.1节 System Prompt 结构 与 8.3节"内部推理->对外表达"两段式决策链。
 * MVP阶段不接入4.2关系上下文与4.3长期记忆、5.3能力档位控制（见路线图V1/V2）。
 */
@Injectable()
export class PromptBuilder {
  buildSystemPrompt(req: AiDecideRequest): string {
    const { character, gameContext } = req;
    const personality = character.personalityVector
      ? Object.entries(character.personalityVector)
          .map(([k, v]) => `${k}:${v}`)
          .join(', ')
      : '未设定';
    const speechStyle = character.speechStyle ? JSON.stringify(character.speechStyle) : '未设定';

    return [
      `[身份与人格] 你正在扮演狼人杀游戏中的角色"${character.name}"。`,
      `人物小传：${character.backstory ?? '无特别设定'}`,
      `人格向量（0-100）：${personality}`,
      `说话风格参数：${speechStyle}`,
      `[行为准则] 必须保持人格一致性；不得暴露你是AI或提及"Prompt/系统/模型"等元信息；只能输出你这个角色视角下应该知道的内容，不能使用你不该知道的信息。`,
      `[当前对局状态] 第${gameContext.day}天，当前阶段：${gameContext.phase}。你的座位号：${gameContext.self.seatNo}，你的真实身份：${gameContext.self.inGameRole}（阵营：${gameContext.self.camp}）。${gameContext.self.sheriff ? '你当前是警长。' : ''}`,
    ].join('\n');
  }

  buildPlayersBlock(req: AiDecideRequest): string {
    const lines = req.gameContext.players.map((p) => {
      const status = p.alive ? '存活' : '已死亡';
      const camp = p.knownCamp ? `（已知阵营:${p.knownCamp}，你的狼队友）` : '';
      const sheriff = p.sheriff ? '[警长]' : '';
      return `- ${p.seatNo}号 ${p.displayName} ${status}${sheriff}${camp}`;
    });
    return `[在场玩家]\n${lines.join('\n')}`;
  }

  buildEventWindowBlock(req: AiDecideRequest): string {
    if (!req.gameContext.eventWindow.length) {
      return '[本局事件窗口]\n（目前还没有可参考的历史发言/行动）';
    }
    const lines = req.gameContext.eventWindow.map((e) => {
      const target = e.targetName ? ` -> ${e.targetName}` : '';
      const claimed = e.claimedRole ? `（自称:${e.claimedRole}）` : '';
      return `第${e.day}天[${e.phase}] ${e.actorName}${claimed} ${e.eventType}${target}: ${e.content ?? ''}`;
    });
    return `[本局事件窗口，按时间顺序]\n${lines.join('\n')}`;
  }

  buildReasoningUserPrompt(req: AiDecideRequest): string {
    return [
      this.buildPlayersBlock(req),
      this.buildEventWindowBlock(req),
      `[本回合任务] 任务类型：${req.task.actionType}。`,
      req.task.instruction,
      req.task.candidateTargets.length
        ? `可选目标：${req.task.candidateTargets.map((c) => `${c.displayName}(id=${c.playerId})`).join('、')}`
        : '',
      `请先完整地进行内部推理（这部分内容其他玩家永远看不到）：分析场上局势、每个人的可疑程度、你该怎么做对你的阵营最有利。用自然语言直接输出你的推理过程，不需要JSON格式。`,
    ]
      .filter(Boolean)
      .join('\n\n');
  }

  buildExpressionUserPrompt(req: AiDecideRequest, reasoning: string): string {
    const isSpeechLike = SPEECH_LIKE_ACTIONS.has(req.task.actionType);
    const needsTarget = req.task.candidateTargets.length > 0;
    const schemaHint = [
      `{`,
      `  "content": ${isSpeechLike ? 'string (你实际要说出的话，符合你的人格与说话风格)' : 'string|null (可选的一句话说明)'},`,
      `  "targetPlayerId": ${needsTarget ? 'number|null (必须是下面候选目标列表中的id原样数字，' + (req.task.allowAbstain ? '允许为null表示弃权/不使用技能' : '不允许为null') + ')' : 'null'},`,
      `  "claimedRole": "string|null (仅当你想在发言中对外自称某个身份时填写角色key，如 seer/witch，否则为null)",`,
      `  "decisionYes": ${req.task.actionType.includes('decision') || req.task.actionType === 'night_action' ? 'boolean (是否参选/是否使用技能等布尔决策)' : 'null'}`,
      `}`,
    ].join('\n');

    // 候选目标的id映射必须在这里再给一遍——上一轮推理是自然语言，经常只提名字不提id，
    // 模型若只能凭推理文本"回忆"数字id，极容易编错号导致后端校验失败、targetPlayerId被判定为null，
    // 这是之前夜晚击杀/投票经常"全员弃权"的根因，而不是模型不愿意选目标。
    const candidateBlock = needsTarget
      ? `可选目标（targetPlayerId必须精确等于其中一个id）：\n${req.task.candidateTargets
          .map((c) => `- id=${c.playerId}：${c.displayName}`)
          .join('\n')}`
      : '';

    return [
      `你刚才的内部推理结论如下（仅供参考，不能直接照搬语气，需要转换成符合你人格的对外表达）：`,
      reasoning,
      `[本回合任务] ${req.task.instruction}`,
      candidateBlock,
      `现在请生成你真正要对外输出的内容。重要：即使内部推理很清晰，你的对外表达也必须符合你的人格设定——`,
      `逻辑严谨度低、伪装欺骗力低的角色对外表达可以显得不够严密、偶尔说漏嘴；情绪不稳定的角色被质疑时可以语无伦次。`,
      `只输出一个JSON对象，不要输出任何多余文字、不要用markdown代码块包裹，格式如下：`,
      schemaHint,
    ]
      .filter(Boolean)
      .join('\n\n');
  }
}
