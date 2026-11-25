# --- Stufe 1: Vue-App mit Vite bauen ------------------------------------------
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Lint/Tests laufen bereits im CI-Job "frontend", hier nur bauen
RUN npm run build-only

# --- Stufe 2: statische Dateien mit nginx ausliefern ---------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
