// Package auth protects Go services with the JWTs issued by Keycloak and lets them call
// other services with their own (client-credentials) token.
//
// It holds nothing service-specific: every value comes from environment variables, so it can
// be moved into a shared Go module once a second Go service needs it.
//
// Incoming tokens are validated locally (signature against the cached JWKS, issuer, audience,
// expiry), so Keycloak is not called for each request and the service works the same when it
// runs on a different host from Keycloak.
package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"slices"
	"strings"

	"github.com/coreos/go-oidc/v3/oidc"
)

// Config tells the verifier which tokens to accept.
type Config struct {
	Issuer   string // must equal the token's `iss`, e.g. http://localhost/auth/realms/servertest
	JWKSURL  string // where the public keys are fetched, e.g. http://keycloak:8080/auth/realms/servertest/protocol/openid-connect/certs
	Audience string // must be in the token's `aud`, e.g. stock-service
}

// ConfigFromEnv reads OAUTH_ISSUER, OAUTH_JWKS_URI and OAUTH_AUDIENCE.
func ConfigFromEnv() (Config, error) {
	c := Config{
		Issuer:   os.Getenv("OAUTH_ISSUER"),
		JWKSURL:  os.Getenv("OAUTH_JWKS_URI"),
		Audience: os.Getenv("OAUTH_AUDIENCE"),
	}
	if c.Issuer == "" || c.JWKSURL == "" || c.Audience == "" {
		return c, errors.New("OAUTH_ISSUER, OAUTH_JWKS_URI and OAUTH_AUDIENCE must be set")
	}
	return c, nil
}

// Claims are the parts of a validated access token that handlers use.
type Claims struct {
	Subject           string   `json:"sub"`
	PreferredUsername string   `json:"preferred_username,omitempty"` // empty for service tokens
	AuthorizedParty   string   `json:"azp"`                          // client that requested the token: web, ios, stock-service...
	Scopes            []string `json:"-"`
}

// HasScope reports whether the token grants scope.
func (c Claims) HasScope(scope string) bool { return slices.Contains(c.Scopes, scope) }

// Verifier validates bearer tokens.
type Verifier struct {
	verifier *oidc.IDTokenVerifier
}

// NewVerifier builds a verifier. Keys are fetched on first use, cached, and re-fetched when
// Keycloak rotates them (unknown `kid`), so Keycloak does not need to be up at startup.
func NewVerifier(ctx context.Context, cfg Config) *Verifier {
	keys := oidc.NewRemoteKeySet(ctx, cfg.JWKSURL)
	return &Verifier{verifier: oidc.NewVerifier(cfg.Issuer, keys, &oidc.Config{
		ClientID:             cfg.Audience, // checked against `aud`
		SupportedSigningAlgs: []string{oidc.RS256},
	})}
}

// Verify validates a raw JWT and returns its claims.
func (v *Verifier) Verify(ctx context.Context, rawToken string) (Claims, error) {
	tok, err := v.verifier.Verify(ctx, rawToken)
	if err != nil {
		return Claims{}, err
	}
	var raw struct {
		Claims
		Scope string `json:"scope"`
	}
	if err := tok.Claims(&raw); err != nil {
		return Claims{}, fmt.Errorf("reading claims: %w", err)
	}
	raw.Claims.Scopes = strings.Fields(raw.Scope)
	return raw.Claims, nil
}

type ctxKey struct{}

// ClaimsFrom returns the claims that Middleware stored in the request context.
func ClaimsFrom(ctx context.Context) (Claims, bool) {
	c, ok := ctx.Value(ctxKey{}).(Claims)
	return c, ok
}

// Middleware requires a valid bearer token that grants requiredScope.
// It answers 401 when the token is missing or invalid, 403 when the scope is missing.
func (v *Verifier) Middleware(requiredScope string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			raw, ok := bearerToken(r)
			if !ok {
				deny(w, http.StatusUnauthorized, `Bearer`, "a valid bearer token is required")
				return
			}
			claims, err := v.Verify(r.Context(), raw)
			if err != nil {
				deny(w, http.StatusUnauthorized, `Bearer error="invalid_token"`, "invalid token")
				return
			}
			if !claims.HasScope(requiredScope) {
				deny(w, http.StatusForbidden,
					fmt.Sprintf(`Bearer error="insufficient_scope", scope=%q`, requiredScope),
					"the token does not grant the scope "+requiredScope)
				return
			}
			next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), ctxKey{}, claims)))
		})
	}
}

func bearerToken(r *http.Request) (string, bool) {
	h := r.Header.Get("Authorization")
	scheme, token, found := strings.Cut(h, " ")
	if !found || !strings.EqualFold(scheme, "Bearer") || strings.TrimSpace(token) == "" {
		return "", false
	}
	return strings.TrimSpace(token), true
}

// deny writes an RFC 7807 problem, matching the products service's error format.
func deny(w http.ResponseWriter, status int, wwwAuthenticate, detail string) {
	w.Header().Set("WWW-Authenticate", wwwAuthenticate)
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"type":   "about:blank",
		"title":  http.StatusText(status),
		"status": status,
		"detail": detail,
	})
}
