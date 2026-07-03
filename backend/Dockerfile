# --- Étape de build ---
FROM node:22-alpine AS build
WORKDIR /app

# Dépendances (cache) puis build TypeScript.
COPY package*.json ./
RUN npm ci
COPY tsconfig*.json nest-cli.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

# --- Étape runtime ---
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

# N'embarque que le nécessaire à l'exécution.
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY package.json ./

# Ne pas tourner en root : l'image node fournit déjà un utilisateur `node`
# non privilégié. L'API ne persiste rien sur le disque (stockage → MinIO), une
# arborescence /app en lecture seule suffit.
USER node

EXPOSE 3000
CMD ["node", "dist/main.js"]
