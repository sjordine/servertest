import { useState } from "react";
import { useAuth } from "react-oidc-context";
import { getAccessToken } from "../auth";

type Target = {
  key: string;
  label: string;
  path: string;
  route: string;
  /** Send the signed-in user's access token. */
  auth?: boolean;
};

const TARGETS: Target[] = [
  {
    key: "products",
    label: "Products",
    path: "/api/products/hello",
    route: "Nginx → Spring Boot (Java) → products_db",
  },
  {
    key: "stock",
    label: "Stock",
    path: "/api/stock/hello",
    route: "Nginx → Go → stock_db",
  },
  {
    key: "auth",
    label: "Auth (user → stock → products)",
    path: "/api/stock/auth-check",
    route: "Your token (stock:read) → Go validates it → calls products with its own service token",
    auth: true,
  },
];

type Result =
  | { state: "idle" }
  | { state: "loading" }
  | { state: "ok"; ms: number; body: unknown }
  | { state: "error"; ms: number; error: string };

async function check(t: Target): Promise<Result> {
  const start = performance.now();
  try {
    const token = t.auth ? await getAccessToken() : undefined;
    if (t.auth && !token) return { state: "error", ms: 0, error: "Sign in first: this check needs your access token." };
    const res = await fetch(t.path, { headers: token ? { Authorization: `Bearer ${token}` } : undefined });
    const text = await res.text();
    const ms = Math.round(performance.now() - start);
    let body: unknown = text;
    try {
      body = JSON.parse(text);
    } catch {
      /* not JSON, keep raw text */
    }
    if (!res.ok) {
      return { state: "error", ms, error: `HTTP ${res.status}: ${typeof body === "string" ? body : JSON.stringify(body)}` };
    }
    return { state: "ok", ms, body };
  } catch (e) {
    return { state: "error", ms: Math.round(performance.now() - start), error: String(e) };
  }
}

export default function EnvironmentCheckPage() {
  const [results, setResults] = useState<Record<string, Result>>({});
  const auth = useAuth();
  const signingIn = auth.isLoading || !!auth.activeNavigator;

  const run = async (t: Target) => {
    setResults((r) => ({ ...r, [t.key]: { state: "loading" } }));
    const result = await check(t);
    setResults((r) => ({ ...r, [t.key]: result }));
  };

  const runAll = () => TARGETS.filter((t) => !(t.auth && signingIn)).forEach(run);

  return (
    <>
      <header>
        <h1>Environment check</h1>
        <p>Each button calls a service through the gateway; the service reads a row from its own Postgres database.</p>
        <button className="primary" onClick={runAll}>Check all</button>
      </header>

      <section className="cards">
        {TARGETS.map((t) => {
          const r = results[t.key] ?? { state: "idle" };
          return (
            <article key={t.key} className={`card ${r.state}`}>
              <div className="card-head">
                <div>
                  <h2>{t.label}</h2>
                  <code>GET {t.path}</code>
                  <p className="route">{t.route}</p>
                </div>
                <button onClick={() => run(t)} disabled={r.state === "loading" || (t.auth && signingIn)}>
                  {r.state === "loading" ? "Checking…" : t.auth && signingIn ? "Signing in…" : "Check"}
                </button>
              </div>
              <Status result={r} />
            </article>
          );
        })}
      </section>
    </>
  );
}

function Status({ result }: { result: Result }) {
  switch (result.state) {
    case "idle":
      return <p className="status">Not checked yet</p>;
    case "loading":
      return <p className="status">Calling…</p>;
    case "ok":
      return (
        <>
          <p className="status">✓ OK · {result.ms} ms</p>
          <pre>{JSON.stringify(result.body, null, 2)}</pre>
        </>
      );
    case "error":
      return (
        <>
          <p className="status">✗ Failed · {result.ms} ms</p>
          <pre>{result.error}</pre>
        </>
      );
  }
}
