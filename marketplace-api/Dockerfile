FROM node:24.22.2-bookworm-slim AS build
WORKDIR /workspace

COPY doom-cli/package*.json ./doom-cli/
COPY doom-cli/tsconfig.json ./doom-cli/
COPY doom-cli/src ./doom-cli/src
COPY doom-cli/scripts ./doom-cli/scripts
COPY packages/manifest-schema ./packages/manifest-schema
COPY sample-mods/templates ./sample-mods/templates
RUN npm --prefix doom-cli ci --ignore-scripts && npm --prefix doom-cli run build

COPY marketplace-api/package*.json ./marketplace-api/
WORKDIR /workspace/marketplace-api
RUN npm ci --ignore-scripts
COPY marketplace-api/tsconfig.json ./
COPY marketplace-api/src ./src
COPY marketplace-api/migrations ./migrations
COPY marketplace-api/openapi.yaml ./openapi.yaml
RUN npm run build && npm prune --omit=dev --ignore-scripts

FROM node:24.22.2-bookworm-slim AS runtime
ENV NODE_ENV=production
WORKDIR /app/marketplace-api
COPY --from=build --chown=node:node /workspace/marketplace-api/node_modules ./node_modules
COPY --from=build --chown=node:node /workspace/marketplace-api/dist ./dist
COPY --from=build --chown=node:node /workspace/marketplace-api/migrations ./migrations
COPY --from=build --chown=node:node /workspace/marketplace-api/openapi.yaml ./openapi.yaml
COPY --from=build --chown=node:node /workspace/marketplace-api/package.json ./package.json

USER node
EXPOSE 8787
CMD ["node", "dist/server.js"]
