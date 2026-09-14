import { Body, Controller, Post } from '@nestjs/common';
import { AgentService } from './agent.service';
import { AiDecideRequest, AiDecideResponse } from './dto';

/** 后端GameEngineService调用的内部接口，见需求文档8.3/6.2节"AI Agent编排服务" */
@Controller('agent')
export class AgentController {
  constructor(private readonly agentService: AgentService) {}

  @Post('decide')
  async decide(@Body() body: AiDecideRequest): Promise<AiDecideResponse> {
    return this.agentService.decide(body);
  }
}
