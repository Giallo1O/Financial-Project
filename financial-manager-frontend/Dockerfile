# Stage 1: Build the React application
FROM node:20-alpine AS build
WORKDIR /app

# Copy package files and install dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the application code
COPY . .

# Pass build arguments to be used as environment variables during Vite build
ARG VITE_DASHBOARD_API_URL
ARG VITE_TRANSACTION_API_URL
ARG VITE_USER_API_URL
ENV VITE_DASHBOARD_API_URL=$VITE_DASHBOARD_API_URL
ENV VITE_TRANSACTION_API_URL=$VITE_TRANSACTION_API_URL
ENV VITE_USER_API_URL=$VITE_USER_API_URL

# Build for production
RUN npm run build

# Stage 2: Serve the application with Nginx
FROM nginx:alpine

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Add custom Nginx configuration for React Router
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port (default for nginx is 80)
EXPOSE 80

# Start server
CMD ["nginx", "-g", "daemon off;"]
