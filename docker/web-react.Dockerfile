# syntax=docker/dockerfile:1

FROM node:20-alpine AS build
WORKDIR /app
COPY web-react/package*.json ./
RUN npm install --no-audit --no-fund
COPY web-react/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
