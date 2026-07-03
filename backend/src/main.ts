import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import helmet from 'helmet';
import { AppModule } from './app.module';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);
  const config = app.get(ConfigService);
  const isProduction =
    config.get<string>('NODE_ENV', 'development') === 'production';

  app.setGlobalPrefix('api/v1');

  // En-têtes de sécurité HTTP (nosniff, HSTS, frameguard…). L'API sert du JSON
  // et, en dev, Swagger UI : on désactive la CSP par défaut de helmet (trop
  // stricte pour Swagger UI) — l'API n'ayant pas de surface HTML propre.
  app.use(helmet({ contentSecurityPolicy: false }));

  // CORS restreint aux origines déclarées (CORS_ORIGINS, liste séparée par des
  // virgules). L'app Android n'est pas un navigateur (CORS inopérant) ; ce
  // réglage ne sert qu'à un éventuel front web (vitrine). Défaut : aucune
  // origine autorisée (plus de `*`) — définir CORS_ORIGINS pour ouvrir.
  const corsOrigins = (config.get<string>('CORS_ORIGINS', '') ?? '')
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean);
  app.enableCors({
    origin: corsOrigins.length > 0 ? corsOrigins : false,
  });

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true, // retire les champs non déclarés dans les DTO
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  // Documentation OpenAPI/Swagger (NODE-36) exposée sur /api/docs. Masquée en
  // production par défaut (ne pas exposer la surface d'API publiquement) : mettre
  // SWAGGER_ENABLED=true pour la réactiver ponctuellement sur un env de prod.
  const swaggerEnabled =
    !isProduction || config.get<string>('SWAGGER_ENABLED') === 'true';
  if (swaggerEnabled) {
    const swaggerConfig = new DocumentBuilder()
      .setTitle('FloraPin API')
      .setDescription('API backend FloraPin (comptes, fleurs, partage, carte).')
      .setVersion('0.1.0')
      .addBearerAuth(
        { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' },
        'access-token',
      )
      .build();
    const document = SwaggerModule.createDocument(app, swaggerConfig);
    SwaggerModule.setup('api/docs', app, document);
  }

  const port = config.get<number>('PORT', 3000);
  await app.listen(port);
}

void bootstrap();
