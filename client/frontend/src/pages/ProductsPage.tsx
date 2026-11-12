import { Fragment, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, Category, Package, Page, ProductSummary } from "../api";
import { useCanWrite } from "../auth";
import ErrorBox from "../components/ErrorBox";

const PAGE_SIZE = 20;

export default function ProductsPage() {
  const canWrite = useCanWrite();
  // Filters live in the URL so they survive reloads and can be linked to (e.g. from Categories).
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const categoryId = params.get("categoryId") ?? "";
  const includeArchived = params.get("archived") === "1";
  const page = Number(params.get("page") ?? 0);

  const [search, setSearch] = useState(q);
  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<Page<ProductSummary> | null>(null);
  const [expanded, setExpanded] = useState<Record<string, Package[] | "loading">>({});
  const [error, setError] = useState<unknown>(null);

  const setParam = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (!("page" in changes)) next.delete("page");
    setParams(next, { replace: true });
  };

  useEffect(() => {
    api.categories.list(true).then(setCategories).catch(setError);
  }, []);

  // Debounce the search box into the URL.
  useEffect(() => {
    const t = setTimeout(() => search !== q && setParam({ q: search }), 300);
    return () => clearTimeout(t);
  }, [search]);

  useEffect(() => {
    setError(null);
    setExpanded({});
    api.products
      .list({ q, categoryId: categoryId || undefined, includeArchived, page, size: PAGE_SIZE })
      .then(setResult)
      .catch(setError);
  }, [q, categoryId, includeArchived, page]);

  const toggle = async (p: ProductSummary) => {
    if (expanded[p.id]) {
      const { [p.id]: _, ...rest } = expanded;
      setExpanded(rest);
      return;
    }
    setExpanded((e) => ({ ...e, [p.id]: "loading" }));
    try {
      const pkgs = await api.packages.list(p.id, includeArchived);
      setExpanded((e) => ({ ...e, [p.id]: pkgs }));
    } catch (err) {
      setError(err);
    }
  };

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Products</h1>
          <p>Each product is one sellable item (SKU). Expand a row to see how it is packaged.</p>
        </div>
        {canWrite && <Link className="button primary" to="/products/new">New product</Link>}
      </header>

      <div className="toolbar">
        <input type="search" className="grow" placeholder="Search SKU or description…" value={search}
          onChange={(e) => setSearch(e.target.value)} />
        <select value={categoryId} onChange={(e) => setParam({ categoryId: e.target.value })}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}{c.archived ? " (archived)" : ""}</option>
          ))}
        </select>
        <label className="check">
          <input type="checkbox" checked={includeArchived}
            onChange={(e) => setParam({ archived: e.target.checked ? "1" : "" })} />
          Show archived
        </label>
      </div>

      <ErrorBox error={error} />

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th className="toggle" />
              <th>SKU</th>
              <th>Description</th>
              <th>Category</th>
              <th>Content</th>
              <th className="num">Packages</th>
            </tr>
          </thead>
          <tbody>
            {result?.items.map((p) => {
              const pkgs = expanded[p.id];
              return (
                <Fragment key={p.id}>
                  <tr className={`clickable ${p.archived ? "archived" : ""}`} onClick={() => toggle(p)}>
                    <td className="toggle">{pkgs ? "▾" : "▸"}</td>
                    <td>
                      <Link to={`/products/${p.id}`} onClick={(e) => e.stopPropagation()}><code>{p.sku}</code></Link>
                      {p.archived && <span className="badge">archived</span>}
                    </td>
                    <td>{p.description}</td>
                    <td className="muted">{p.category?.name}</td>
                    <td>{p.content?.label}</td>
                    <td className="num">{p.packageCount}</td>
                  </tr>
                  {pkgs && (
                    <tr className="drill">
                      <td />
                      <td colSpan={5}>
                        {pkgs === "loading" ? (
                          <span className="muted">Loading packages…</span>
                        ) : pkgs.length === 0 ? (
                          <span className="muted">
                            No packages. <Link to={`/products/${p.id}`}>Add one</Link>
                          </span>
                        ) : (
                          <table className="sub">
                            <thead>
                              <tr><th>Code</th><th>Package</th><th>Description</th><th /></tr>
                            </thead>
                            <tbody>
                              {pkgs.map((k) => (
                                <tr key={k.id} className={k.archived ? "archived" : ""}>
                                  <td><code>{k.code}</code></td>
                                  <td>{k.label}</td>
                                  <td className="muted">{k.description}</td>
                                  <td>{k.archived && <span className="badge">archived</span>}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
            {result && result.items.length === 0 && (
              <tr><td colSpan={6} className="empty">No products found.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {result && result.totalPages > 1 && (
        <div className="pager">
          <button disabled={page <= 0} onClick={() => setParam({ page: String(page - 1) })}>← Previous</button>
          <span className="muted">
            Page {page + 1} of {result.totalPages} · {result.totalItems} products
          </span>
          <button disabled={page + 1 >= result.totalPages} onClick={() => setParam({ page: String(page + 1) })}>
            Next →
          </button>
        </div>
      )}
    </>
  );
}
