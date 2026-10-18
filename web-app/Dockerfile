# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

ARG VITE_API_BASE_URL=/api
ARG VITE_KEYCLOAK_URL=https://auth.hvtra.id.vn
ARG VITE_KEYCLOAK_REALM=hvt
ARG VITE_KEYCLOAK_CLIENT_ID=hvtra-pos
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---- serve ----
FROM nginx:1.27-alpine
ENV API_GATEWAY_URL=http://api-gateway:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
