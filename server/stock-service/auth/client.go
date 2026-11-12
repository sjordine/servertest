package auth

import (
	"context"
	"errors"
	"net/http"
	"os"
	"time"

	"golang.org/x/oauth2/clientcredentials"
)

// ServiceClientFromEnv returns an *http.Client that authenticates as this service
// (OAuth2 client credentials) on every request it sends.
//
// It reads OAUTH_TOKEN_URL, OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET. The token is fetched on
// the first call, cached, and renewed shortly before it expires. Scopes are the client's
// default scopes, configured in Keycloak (e.g. products:read for stock-service).
func ServiceClientFromEnv(ctx context.Context) (*http.Client, error) {
	cfg := clientcredentials.Config{
		TokenURL:     os.Getenv("OAUTH_TOKEN_URL"),
		ClientID:     os.Getenv("OAUTH_CLIENT_ID"),
		ClientSecret: os.Getenv("OAUTH_CLIENT_SECRET"),
	}
	if cfg.TokenURL == "" || cfg.ClientID == "" || cfg.ClientSecret == "" {
		return nil, errors.New("OAUTH_TOKEN_URL, OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET must be set")
	}
	client := cfg.Client(ctx)
	client.Timeout = 10 * time.Second
	return client, nil
}
