import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule, { cors: true });
  const port = process.env.AI_SERVICE_PORT ? Number(process.env.AI_SERVICE_PORT) : 3100;
  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`wolf-ai-service listening on :${port}`);
}
bootstrap();
