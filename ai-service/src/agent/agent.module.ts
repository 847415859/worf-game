import { Module } from '@nestjs/common';
import { AgentController } from './agent.controller';
import { AgentService } from './agent.service';
import { DeepSeekClient } from '../deepseek/deepseek.client';
import { PromptBuilder } from '../prompt/prompt.builder';

@Module({
  controllers: [AgentController],
  providers: [AgentService, DeepSeekClient, PromptBuilder],
})
export class AgentModule {}
