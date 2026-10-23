FROM node:20-alpine AS build
WORKDIR /src

# corepack 按 package.json 的 packageManager 字段锁定 pnpm 版本，与本地一致
RUN corepack enable

# 先只拷依赖清单，让 node_modules 层在源码变动时仍能命中缓存
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
