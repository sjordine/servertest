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

The gateway serves plain HTTP, so add this to `Info.plist`:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsLocalNetworking</key>
    <true/>
</dict>
```

For a physical device that uses the LAN IP, you may need `NSAllowsArbitraryLoads` for development only.
