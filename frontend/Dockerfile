FROM public.ecr.aws/docker/library/node:22-alpine AS build

WORKDIR /app

ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry "$NPM_REGISTRY"

COPY package.json package-lock.json ./
# lock 偶发与 npm 主版本不完全同步时回退 install，保证云构建可过
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

COPY . .
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

FROM public.ecr.aws/docker/library/nginx:1.27-alpine

COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
  CMD wget -qO- http://127.0.0.1/healthz || exit 1
