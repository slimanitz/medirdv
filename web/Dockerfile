FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

# Unprivileged nginx (UID 101, port 8080): compatible with runAsNonRoot and a read-only root filesystem.
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 docker-entrypoint.d/40-medirdv-config.sh /docker-entrypoint.d/40-medirdv-config.sh
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
EXPOSE 8080
