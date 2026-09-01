# =============================================================================
# Multi-stage build for the Angular admin console.
# Build context is this directory (frontend/).
# =============================================================================

# --- Stage 1: build ----------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

# node:22-alpine bundles npm 10, whose peer-dep resolver rejects this lockfile
# (vitest 4 vs @angular/build's peerOptional vitest ^3 range) even though it
# resolves clean under npm 11+. Match the npm major the lockfile was cut with
# instead of forcing --legacy-peer-deps and hiding a real conflict if one ever appears.
RUN npm install -g npm@11

# Dependencies in their own layer: a source-only change should not re-run npm ci.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Defaults to "development" (this is the courier-dev stack — see
# docker-compose.yml's "courier-dev" project name): environment.production
# stays false, which is what shows the dev quick-fill login buttons testers
# rely on and the manual company-code field. A deployment fronting real
# customers passes --build-arg BUILD_CONFIGURATION=production (docker-compose.yml
# wires this to the FRONTEND_BUILD_CONFIGURATION env var) to strip both.
ARG BUILD_CONFIGURATION=development
RUN npm run build -- --configuration=${BUILD_CONFIGURATION}

# --- Stage 2: runtime (Nginx) -------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist/courier-saas-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ || exit 1
