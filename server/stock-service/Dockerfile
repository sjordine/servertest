# ---- dev: source is bind-mounted at /app, air rebuilds on change ----
FROM golang:1.25 AS dev
WORKDIR /app
RUN go install github.com/air-verse/air@v1.61.7
COPY go.mod go.sum ./
RUN go mod download
EXPOSE 8080
CMD ["air", "-c", ".air.toml"]

# ---- build: static binary ----
FROM golang:1.25 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /stock .

# ---- prod: tiny runtime image ----
FROM gcr.io/distroless/static-debian12 AS prod
COPY --from=build /stock /stock
EXPOSE 8080
ENTRYPOINT ["/stock"]
