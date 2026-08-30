# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build — Node.js builds the static React/Vite bundle
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files first (layer-cache: only re-runs npm install if they change)
COPY package.json package-lock.json ./

# Install all dependencies (including devDependencies needed for build)
RUN npm ci

# Copy source and build the production bundle
COPY . .

# VITE_BASE_URL is passed at build time via --build-arg
ARG VITE_BASE_URL=http://localhost:8080
ENV VITE_BASE_URL=$VITE_BASE_URL

RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — Nginx serves the static files
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:alpine AS runtime

# Remove default Nginx static content
RUN rm -rf /usr/share/nginx/html/*

# Copy built React app from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom Nginx config (handles React Router SPA routing)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
