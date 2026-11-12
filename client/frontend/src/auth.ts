// Login with Keycloak (OpenID Connect, Authorization Code + PKCE).
//
// Keycloak is served by the same gateway, under /auth, so it shares the app's origin: no CORS,
// and its session cookie is first-party (silent sign-in works in an iframe).
//
// Tokens are kept in memory only (never localStorage), so injected scripts can't read them
// from storage. After a page reload the app signs in again silently using Keycloak's session.
import { useAuth } from "react-oidc-context";
import { InMemoryWebStorage, User, UserManager, WebStorageStateStore } from "oidc-client-ts";

const origin = window.location.origin;

export const SILENT_CALLBACK_PATH = "/silent-callback";

export const userManager = new UserManager({
  authority: `${origin}/auth/realms/servertest`,
  client_id: "web",
  redirect_uri: `${origin}/`,
  silent_redirect_uri: `${origin}${SILENT_CALLBACK_PATH}`,
  post_logout_redirect_uri: `${origin}/`,
  // products:write is only granted to users with the catalog-editor role; Keycloak drops it otherwise.
  scope: "openid profile products:read products:write stock:read",
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  automaticSilentRenew: true, // renews with the refresh token before the 5-minute access token expires
});

/** Current access token, or undefined when signed out or expired. */
export async function getAccessToken(): Promise<string | undefined> {
  const user = await userManager.getUser();
  return user && !user.expired ? user.access_token : undefined;
}

export function hasScope(user: User | null | undefined, scope: string): boolean {
  return !!user?.scope?.split(" ").includes(scope);
}

/**
 * Whether the signed-in user may change the catalogue. Only hides buttons for a nicer UI:
 * the products service enforces the products:write scope regardless.
 */
export function useCanWrite(): boolean {
  return hasScope(useAuth().user, "products:write");
}
