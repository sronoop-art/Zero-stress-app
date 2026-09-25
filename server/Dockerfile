# Always-on Firestore -> Knock bridge.
# Build from the repository root:  docker build -f server/Dockerfile -t zs-knock-bridge server
# Secrets are NEVER baked into the image - pass them at run time:
#   docker run -d --restart unless-stopped -p 8080:8080 \
#     -e KNOCK_API_KEY=... -e FIREBASE_SERVICE_ACCOUNT=... zs-knock-bridge
FROM node:22-slim

ENV NODE_ENV=production
WORKDIR /app

# Dependencies first so code edits do not invalidate the install layer.
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund

COPY lib.js index.js verify.js ./

# Non-root, no shell, no write access to the app directory.
USER node

EXPOSE 8080
CMD ["node", "index.js"]
