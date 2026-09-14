import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

@Injectable()
export class DeepSeekClient {
  private readonly logger = new Logger(DeepSeekClient.name);

  constructor(private readonly config: ConfigService) {}

  async chat(messages: ChatMessage[], temperature = 0.9): Promise<string> {
    const apiKey = this.config.get<string>('DEEPSEEK_API_KEY');
    const baseUrl = this.config.get<string>('DEEPSEEK_BASE_URL') ?? 'https://api.deepseek.com';
    const model = this.config.get<string>('DEEPSEEK_MODEL') ?? 'deepseek-chat';

    if (!apiKey) {
      throw new Error('DEEPSEEK_API_KEY未配置');
    }

    const resp = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        messages,
        temperature,
        max_tokens: 700,
      }),
    });

    if (!resp.ok) {
      const text = await resp.text().catch(() => '');
      this.logger.warn(`DeepSeek调用失败 status=${resp.status} body=${text}`);
      throw new Error(`DeepSeek调用失败: ${resp.status}`);
    }

    const data: any = await resp.json();
    const content = data?.choices?.[0]?.message?.content;
    if (typeof content !== 'string') {
      throw new Error('DeepSeek返回内容格式异常');
    }
    return content;
  }
}
