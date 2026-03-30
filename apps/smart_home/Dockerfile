# Build stage
FROM golang:1.22-alpine AS builder

# Set working directory
WORKDIR /app

# Copy source (tidy will resolve any new deps)
COPY go.mod ./
COPY . .

# Resolve dependencies (updates go.sum inside the build container)
RUN go mod tidy

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -o smarthome

# Runtime stage
FROM alpine:latest

# Install necessary runtime dependencies
RUN apk --no-cache add ca-certificates tzdata

# Set working directory
WORKDIR /app

# Copy the binary from the builder stage
COPY --from=builder /app/smarthome .

# Expose the port the app runs on
EXPOSE 8080

# Command to run the application
CMD ["./smarthome"]