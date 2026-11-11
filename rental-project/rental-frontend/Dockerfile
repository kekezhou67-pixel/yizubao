# 前端镜像（AI 改造 · 阶段8）：node 构建 → nginx 托管静态资源
# 多阶段构建：最终镜像只含 dist + nginx（<50MB）
FROM node:20-alpine AS build
WORKDIR /app

# 先 COPY package 文件再装依赖 —— 利用 Docker 层缓存（讲义01 最佳实践）
COPY package*.json ./
RUN npm ci --registry=https://registry.npmmirror.com

COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# SPA 路由回退：任意路径都返回 index.html
RUN printf 'server {\n  listen 80;\n  location / {\n    root /usr/share/nginx/html;\n    index index.html;\n    try_files $uri $uri/ /index.html;\n  }\n}\n' > /etc/nginx/conf.d/default.conf
EXPOSE 80
