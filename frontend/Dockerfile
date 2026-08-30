# Build from repository root:
#   docker build -f frontend/Dockerfile --build-arg NGINX_CONF=deploy/nginx/same-origin.conf .
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
ARG NGINX_CONF=deploy/nginx/same-origin.conf
COPY --from=build /app/dist/finance-platform-web/browser /usr/share/nginx/html
COPY ${NGINX_CONF} /etc/nginx/conf.d/default.conf
EXPOSE 80
