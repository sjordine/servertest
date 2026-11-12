# Mobile client (iOS)

Create the Xcode project in this folder.

## Talking to the backend
All requests go through the Nginx gateway on port 80:

| Where the app runs | Base URL |
|---|---|
| iOS Simulator | `http://localhost` |
| Physical iPhone (same Wi-Fi) | `http://<your-Mac-LAN-IP>` (System Settings → Wi-Fi → Details) |

Endpoints available now:
- `GET /api/products/hello`
- `GET /api/stock/hello`

## Login (OpenID Connect)
Every API except `/hello` needs an access token. The app signs in through Keycloak with **Authorization Code + PKCE**, the standard flow for native apps. [AppAuth-iOS](https://github.com/openid/AppAuth-iOS) does the work:

| Setting | Value |
|---|---|
| Issuer (discovery) | `<base URL>/auth/realms/servertest` |
| Client ID | `ios` (public client, no secret) |
| Redirect URI | `servertest://oauth/callback`. Register the `servertest` URL scheme in the app target. |
| Scopes | `openid profile products:read products:write stock:read`, plus `offline_access` to stay signed in across launches |

Send the token on each request as `Authorization: Bearer <access token>`. Store the refresh token in the Keychain, and let AppAuth's `performAction(freshTokens:)` renew the 5-minute access token.

Dev users: `alice` / `alice` (editor) and `bob` / `bob` (read-only).

Note: on a physical iPhone, the base URL is your Mac's LAN IP, but Keycloak issues tokens for `http://localhost/auth/...` (`KEYCLOAK_HOSTNAME` in `.env`). To test on a device, set `KEYCLOAK_HOSTNAME=http://<your-Mac-LAN-IP>/auth`, then restart keycloak, products and stock (`docker compose up -d`).

The gateway serves plain HTTP, so add this to `Info.plist`:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsLocalNetworking</key>
    <true/>
</dict>
```

For a physical device that uses the LAN IP, you may need `NSAllowsArbitraryLoads` for development only.
