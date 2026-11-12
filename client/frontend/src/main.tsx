import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "react-oidc-context";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { SILENT_CALLBACK_PATH, userManager } from "./auth";
import "./styles.css";

if (window.location.pathname === SILENT_CALLBACK_PATH) {
  // Runs inside the hidden iframe of a silent sign-in: hand the result to the parent window.
  userManager.signinSilentCallback().catch(() => {});
} else {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <AuthProvider
        userManager={userManager}
        // Remove the ?code=&state= login response from the address bar, keeping the page's own query.
        onSigninCallback={() => {
          const url = new URL(window.location.href);
          ["code", "state", "session_state", "iss"].forEach((p) => url.searchParams.delete(p));
          window.history.replaceState({}, document.title, url.pathname + url.search);
        }}
      >
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </StrictMode>,
  );
}
