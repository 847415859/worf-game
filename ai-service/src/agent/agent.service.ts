import { Injectable, Logger } from '@nestjs/common';
import { DeepSeekClient } from '../deepseek/deepseek.client';
import { PromptBuilder } from '../prompt/prompt.builder';
import { AiDecideRequest, AiDecideResponse } from './dto';

interface ParsedExpression {
  content: string | null;
  targetPlayerId: number | null;
  claimedRole: string | null;
  decisionYes: boolean | null;
}

@Injectable()
export class AgentService {
  private readonly logger = new Logger(AgentService.name);

  constructor(
    private readonly deepseek: DeepSeekClient,
    private readonly promptBuilder: PromptBuilder,
  ) {}

  /** 见需求文档8.3节：内部推理调用 -> 对外表达调用 两段式决策链 */
  async decide(req: AiDecideRequest): Promise<AiDecideResponse> {
    const systemPrompt = this.promptBuilder.buildSystemPrompt(req);

    const reasoningPrompt = this.promptBuilder.buildReasoningUserPrompt(req);
    const reasoning = await this.deepseek.chat(
      [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: reasoningPrompt },
      ],
      0.7,
    );

    const expressionPrompt = this.promptBuilder.buildExpressionUserPrompt(req, reasoning);
    const expressionRaw = await this.deepseek.chat(
      [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: expressionPrompt },
      ],
      1.0,
    );

    const parsed = this.parseExpression(expressionRaw, req);

    return {
      content: parsed.content,
      targetPlayerId: parsed.targetPlayerId,
      claimedRole: parsed.claimedRole,
      decisionYes: parsed.decisionYes,
      innerMonologue: reasoning,
    };
  }

  private parseExpression(raw: string, req: AiDecideRequest): ParsedExpression {
    const jsonText = this.extractJson(raw);
    try {
      const obj = JSON.parse(jsonText);
      const validTargetIds = new Set(req.task.candidateTargets.map((c) => c.playerId));
      // 模型偶尔会把id输出成带引号的数字字符串，这里做宽容转换，真正的防线是
      // buildExpressionUserPrompt里重新给出的候选id列表（否则模型压根不知道数字是几）
      const rawTarget = obj.targetPlayerId;
      const numericTarget = typeof rawTarget === 'number' ? rawTarget : typeof rawTarget === 'string' ? Number(rawTarget) : NaN;
      const targetPlayerId = Number.isFinite(numericTarget) && validTargetIds.has(numericTarget) ? numericTarget : null;
      const rawDecision = obj.decisionYes;
      const decisionYes =
        typeof rawDecision === 'boolean' ? rawDecision : typeof rawDecision === 'string' ? rawDecision.toLowerCase() === 'true' : null;
      return {
        content: typeof obj.content === 'string' ? obj.content : null,
        targetPlayerId,
        claimedRole: typeof obj.claimedRole === 'string' ? obj.claimedRole : null,
        decisionYes,
      };
    } catch (e) {
      this.logger.warn(`对外表达JSON解析失败，原文: ${raw}`);
      // 兜底：把原始文本当作发言内容，其余字段留空，由后端8.7/8.8节兜底逻辑接管风险更高的动作类回合
      return { content: raw.trim().slice(0, 300), targetPlayerId: null, claimedRole: null, decisionYes: null };
    }
  }

  private extractJson(raw: string): string {
    const trimmed = raw.trim();
    const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
    if (fenced) return fenced[1].trim();
    const start = trimmed.indexOf('{');
    const end = trimmed.lastIndexOf('}');
    if (start >= 0 && end > start) return trimmed.slice(start, end + 1);
    return trimmed;
  }
}
