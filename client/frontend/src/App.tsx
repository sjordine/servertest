import { ReactNode, useEffect, useRef } from "react";
import { hasAuthParams, useAuth } from "react-oidc-context";
import { NavLink, Route, Routes } from "react-router-dom";
import { useCanWrite } from "./auth";
import CategoriesPage from "./pages/CategoriesPage";
import EnvironmentCheckPage from "./pages/EnvironmentCheckPage";
import ProductFormPage from "./pages/ProductFormPage";
import ProductsPage from "./pages/ProductsPage";
import UnitsPage from "./pages/UnitsPage";

export default function App() {
  useSilentSignInOnLoad();

  return (
    <>
      <nav className="topnav">
        <span className="brand">servertest</span>
        <NavLink to="/" end>Products</NavLink>
        <NavLink to="/categories">Categories</NavLink>
        <NavLink to="/units">Units</NavLink>
        <NavLink to="/health">Environment check</NavLink>
        <UserMenu />
      </nav>
      <main>
        <Routes>
          <Route path="/" element={<RequireAuth><ProductsPage /></RequireAuth>} />
          <Route path="/products/new" element={<RequireAuth><ProductFormPage /></RequireAuth>} />
          <Route path="/products/:id" element={<RequireAuth><ProductFormPage /></RequireAuth>} />
          <Route path="/categories" element={<RequireAuth><CategoriesPage /></RequireAuth>} />
          <Route path="/units" element={<RequireAuth><UnitsPage /></RequireAuth>} />
          <Route path="/health" element={<EnvironmentCheckPage />} />
          <Route path="*" element={<p>Page not found.</p>} />
        </Routes>
      </main>
    </>
  );
}

/**
 * Tokens live in memory, so a reload starts signed out. Try once to sign in silently
 * (hidden iframe, no login form) using the Keycloak session cookie.
 */
function useSilentSignInOnLoad() {
  const auth = useAuth();
  const tried = useRef(false);
  useEffect(() => {
    if (tried.current || hasAuthParams() || auth.isAuthenticated || auth.activeNavigator || auth.isLoading) return;
    tried.current = true;
    auth.signinSilent().catch(() => {
      /* no Keycloak session: the user signs in with the button */
    });
  }, [auth]);
}

function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();
  if (auth.isLoading || auth.activeNavigator) return <p className="muted">Signing in…</p>;
  if (!auth.isAuthenticated) {
    return (
      <section className="panel">
        <h1>Sign in required</h1>
        <p>The catalogue is protected. Sign in with your servertest account.</p>
        <button className="primary" onClick={() => auth.signinRedirect({ redirect_uri: window.location.href })}>
          Sign in
        </button>
      </section>
    );
  }
  return <>{children}</>;
}

function UserMenu() {
  const auth = useAuth();
  const canWrite = useCanWrite();
  if (auth.isLoading || auth.activeNavigator) return null;
  return (
    <span className="user-menu">
      {auth.isAuthenticated ? (
        <>
          <span>{auth.user?.profile.preferred_username}</span>
          {!canWrite && <span className="badge">read-only</span>}
          <button onClick={() => auth.signoutRedirect()}>Sign out</button>
        </>
      ) : (
        <button className="primary" onClick={() => auth.signinRedirect()}>Sign in</button>
      )}
    </span>
  );
}
