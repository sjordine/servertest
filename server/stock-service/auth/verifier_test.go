package auth

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/go-jose/go-jose/v4"
	"github.com/go-jose/go-jose/v4/jwt"
)

const testIssuer = "http://idp.test/realms/servertest"

// fakeIdP serves a JWKS and signs tokens with the matching private key.
type fakeIdP struct {
	key  *rsa.PrivateKey
	jwks *httptest.Server
}

func newFakeIdP(t *testing.T) *fakeIdP {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	set := jose.JSONWebKeySet{Keys: []jose.JSONWebKey{{Key: &key.PublicKey, KeyID: "k1", Algorithm: "RS256", Use: "sig"}}}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_ = json.NewEncoder(w).Encode(set)
	}))
	t.Cleanup(srv.Close)
	return &fakeIdP{key: key, jwks: srv}
}

func (f *fakeIdP) sign(t *testing.T, key *rsa.PrivateKey, claims map[string]any) string {
	t.Helper()
	signer, err := jose.NewSigner(jose.SigningKey{Algorithm: jose.RS256, Key: key},
		(&jose.SignerOptions{}).WithType("JWT").WithHeader("kid", "k1"))
	if err != nil {
		t.Fatal(err)
	}
	raw, err := jwt.Signed(signer).Claims(claims).Serialize()
	if err != nil {
		t.Fatal(err)
	}
	return raw
}

func validClaims() map[string]any {
	return map[string]any{
		"iss":                testIssuer,
		"aud":                []string{"stock-service", "products-service"},
		"sub":                "user-1",
		"azp":                "web",
		"preferred_username": "alice",
		"scope":              "openid stock:read products:read",
		"exp":                time.Now().Add(5 * time.Minute).Unix(),
		"iat":                time.Now().Unix(),
	}
}

func TestMiddleware(t *testing.T) {
	idp := newFakeIdP(t)
	otherKey, _ := rsa.GenerateKey(rand.Reader, 2048)
	v := NewVerifier(context.Background(), Config{Issuer: testIssuer, JWKSURL: idp.jwks.URL, Audience: "stock-service"})

	var seen Claims
	h := v.Middleware("stock:read")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen, _ = ClaimsFrom(r.Context())
		w.WriteHeader(http.StatusOK)
	}))

	with := func(mut func(map[string]any)) string {
		c := validClaims()
		if mut != nil {
			mut(c)
		}
		return idp.sign(t, idp.key, c)
	}

	cases := []struct {
		name   string
		header string
		want   int
	}{
		{"valid token", "Bearer " + with(nil), http.StatusOK},
		{"no header", "", http.StatusUnauthorized},
		{"not bearer", "Basic abc", http.StatusUnauthorized},
		{"garbage", "Bearer not-a-jwt", http.StatusUnauthorized},
		{"expired", "Bearer " + with(func(c map[string]any) { c["exp"] = time.Now().Add(-time.Minute).Unix() }), http.StatusUnauthorized},
		{"wrong issuer", "Bearer " + with(func(c map[string]any) { c["iss"] = "http://evil.test" }), http.StatusUnauthorized},
		{"wrong audience", "Bearer " + with(func(c map[string]any) { c["aud"] = "products-service" }), http.StatusUnauthorized},
		{"signed by another key", "Bearer " + idp.sign(t, otherKey, validClaims()), http.StatusUnauthorized},
		{"missing scope", "Bearer " + with(func(c map[string]any) { c["scope"] = "openid products:read" }), http.StatusForbidden},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, "/api/stock/auth-check", nil)
			if tc.header != "" {
				req.Header.Set("Authorization", tc.header)
			}
			rec := httptest.NewRecorder()
			h.ServeHTTP(rec, req)
			if rec.Code != tc.want {
				t.Fatalf("status = %d, want %d (body %s)", rec.Code, tc.want, rec.Body)
			}
			if tc.want != http.StatusOK && rec.Header().Get("WWW-Authenticate") == "" {
				t.Fatal("missing WWW-Authenticate header")
			}
		})
	}

	if seen.Subject != "user-1" || seen.PreferredUsername != "alice" || seen.AuthorizedParty != "web" || !seen.HasScope("stock:read") {
		t.Fatalf("claims not passed to handler: %+v", seen)
	}
}
