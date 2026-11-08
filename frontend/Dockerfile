FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx/ /etc/nginx/
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80