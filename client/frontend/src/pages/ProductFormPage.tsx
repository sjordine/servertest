import { FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, Category, Package, ProductDetail, suggestPackageCode, Unit } from "../api";
import { useCanWrite } from "../auth";
import ErrorBox from "../components/ErrorBox";

type ProductDraft = { sku: string; description: string; categoryId: string; contentQuantity: string; contentUnitId: string };
type PackageDraft = { code: string; description: string; quantity: string; unitId: string };

const EMPTY_PRODUCT: ProductDraft = { sku: "", description: "", categoryId: "", contentQuantity: "", contentUnitId: "" };
const EMPTY_PACKAGE: PackageDraft = { code: "", description: "", quantity: "", unitId: "" };

function toDraft(p: ProductDetail): ProductDraft {
  return {
    sku: p.sku,
    description: p.description,
    categoryId: p.category?.id ?? "",
    contentQuantity: p.content ? String(p.content.quantity) : "",
    contentUnitId: p.content?.unit.id ?? "",
  };
}

export default function ProductFormPage() {
  const { id } = useParams();
  const isNew = !id;
  const navigate = useNavigate();

  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [draft, setDraft] = useState<ProductDraft>(EMPTY_PRODUCT);
  const [categories, setCategories] = useState<Category[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api.categories.list(true).then(setCategories).catch(setError);
    api.units.list(true).then(setUnits).catch(setError);
  }, []);

  useEffect(() => {
    setError(null);
    if (!id) {
      setProduct(null);
      setDraft(EMPTY_PRODUCT);
      return;
    }
    api.products
      .get(id)
      .then((p) => {
        setProduct(p);
        setDraft(toDraft(p));
      })
      .catch(setError);
  }, [id]);

  // Archived options are listed only when they are the current value.
  const categoryOptions = categories.filter((c) => !c.archived || c.id === draft.categoryId);
  const unitOptions = (current: string) => units.filter((u) => !u.archived || u.id === current);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaved(false);
    const body = {
      description: draft.description,
      categoryId: draft.categoryId || null,
      contentQuantity: draft.contentQuantity ? Number(draft.contentQuantity) : null,
      contentUnitId: draft.contentUnitId || null,
    };
    try {
      if (isNew) {
        const created = await api.products.create({ ...body, sku: draft.sku });
        navigate(`/products/${created.id}`, { replace: true });
      } else {
        const updated = await api.products.update(id!, body);
        setProduct(updated);
        setDraft(toDraft(updated));
        setSaved(true);
      }
    } catch (err) {
      setError(err);
    }
  };

  const archiveOrRestore = async () => {
    if (!product) return;
    setError(null);
    try {
      if (product.archived) {
        setProduct(await api.products.restore(product.id));
      } else {
        if (!confirm(`Archive ${product.sku} and all its packages? History is kept and it can be restored.`)) return;
        await api.products.archive(product.id);
        setProduct(await api.products.get(product.id));
      }
    } catch (err) {
      setError(err);
    }
  };

  const canWrite = useCanWrite();
  const readOnly = !!product?.archived || !canWrite;

  return (
    <>
      <p className="crumbs"><Link to="/">← Products</Link></p>
      <header className="page-head">
        <div>
          <h1>
            {isNew ? "New product" : product ? <code>{product.sku}</code> : "Product"}
            {product?.archived && <span className="badge">archived</span>}
          </h1>
          {product && <p>{product.description}</p>}
        </div>
        {product && canWrite && (
          <button onClick={archiveOrRestore}>{product.archived ? "Restore product" : "Archive product"}</button>
        )}
      </header>

      <ErrorBox error={error} />

      <form className="panel form-grid" onSubmit={submit}>
        <label>
          SKU
          <input value={draft.sku} maxLength={64} required disabled={!isNew} placeholder="e.g. MILK-WH-1L"
            onChange={(e) => setDraft({ ...draft, sku: e.target.value })} />
          <small>{isNew ? "Unique; stored in upper case. Can't be changed later." : "The SKU can't be changed."}</small>
        </label>
        <label>
          Category
          <select value={draft.categoryId} disabled={readOnly} onChange={(e) => setDraft({ ...draft, categoryId: e.target.value })}>
            <option value="">— none —</option>
            {categoryOptions.map((c) => (
              <option key={c.id} value={c.id}>{c.name}{c.archived ? " (archived)" : ""}</option>
            ))}
          </select>
          <small><Link to="/categories">Manage categories</Link></small>
        </label>
        <label className="span-2">
          Description
          <input value={draft.description} maxLength={2000} required disabled={readOnly}
            placeholder="e.g. Whole milk 1 l bottle" onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
        </label>
        <fieldset className="span-2">
          <legend>Content of one item (optional)</legend>
          <div className="row">
            <input type="number" min="0" step="0.001" placeholder="e.g. 1" value={draft.contentQuantity} disabled={readOnly}
              onChange={(e) => setDraft({ ...draft, contentQuantity: e.target.value })} />
            <select value={draft.contentUnitId} disabled={readOnly} onChange={(e) => setDraft({ ...draft, contentUnitId: e.target.value })}>
              <option value="">— unit —</option>
              {unitOptions(draft.contentUnitId).map((u) => (
                <option key={u.id} value={u.id}>{u.code} · {u.name}</option>
              ))}
            </select>
          </div>
        </fieldset>
        <div className="span-2 form-actions">
          <button className="primary" type="submit" disabled={readOnly}>{isNew ? "Create product" : "Save changes"}</button>
          {saved && <span className="ok-text">Saved ✓</span>}
        </div>
      </form>

      {product && (
        <PackagesSection product={product} units={units} canWrite={canWrite} onChange={() => api.products.get(product.id).then(setProduct)} />
      )}
    </>
  );
}

function PackagesSection({ product, units, canWrite, onChange }: { product: ProductDetail; units: Unit[]; canWrite: boolean; onChange: () => void }) {
  const editable = canWrite && !product.archived;
  const [draft, setDraft] = useState<PackageDraft>(EMPTY_PACKAGE);
  const [editing, setEditing] = useState<{ id: string; description: string } | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const activeUnits = units.filter((u) => !u.archived);
  const unitCode = units.find((u) => u.id === draft.unitId)?.code;
  const suggested = suggestPackageCode(product.sku, draft.quantity, unitCode);
  const visible = product.packages.filter((p) => showArchived || !p.archived);
  const archivedCount = product.packages.length - product.packages.filter((p) => !p.archived).length;

  const run = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      onChange();
      return true;
    } catch (e) {
      setError(e);
      return false;
    }
  };

  const create = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await run(() =>
      api.packages.create(product.id, {
        code: draft.code || undefined,
        description: draft.description || undefined,
        unitId: draft.unitId,
        quantity: Number(draft.quantity),
      }),
    );
    if (ok) setDraft(EMPTY_PACKAGE);
  };

  const saveEdit = async () => {
    if (editing && (await run(() => api.packages.update(product.id, editing.id, { description: editing.description })))) {
      setEditing(null);
    }
  };

  const archive = (p: Package) =>
    confirm(`Archive package ${p.code}? Its history is kept.`) && run(() => api.packages.archive(product.id, p.id));

  return (
    <section className="section">
      <div className="section-head">
        <h2>Packages</h2>
        {archivedCount > 0 && (
          <label className="check">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
            Show archived ({archivedCount})
          </label>
        )}
      </div>

      <ErrorBox error={error} />

      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Code</th><th>Package</th><th>Description</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {visible.map((p) => (
              <tr key={p.id} className={p.archived ? "archived" : ""}>
                <td><code>{p.code}</code></td>
                <td>{p.label}</td>
                <td>
                  {editing?.id === p.id ? (
                    <input value={editing.description} maxLength={2000} autoFocus
                      onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
                  ) : (
                    <span className="muted">{p.description}</span>
                  )}
                </td>
                <td>{p.archived ? <span className="badge">archived</span> : <span className="badge ok">active</span>}</td>
                <td className="actions">
                  {editing?.id === p.id ? (
                    <>
                      <button className="primary" onClick={saveEdit}>Save</button>
                      <button onClick={() => setEditing(null)}>Cancel</button>
                    </>
                  ) : p.archived ? (
                    editable && <button onClick={() => run(() => api.packages.restore(product.id, p.id))}>Restore</button>
                  ) : (
                    editable && (
                      <>
                        <button onClick={() => setEditing({ id: p.id, description: p.description ?? "" })}>Edit</button>
                        <button onClick={() => archive(p)}>Archive</button>
                      </>
                    )
                  )}
                </td>
              </tr>
            ))}
            {visible.length === 0 && (
              <tr><td colSpan={5} className="empty">No packages yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {editable && (
        <form className="panel form-grid" onSubmit={create}>
          <h3 className="span-2">Add package</h3>
          <fieldset className="span-2">
            <legend>Quantity and unit</legend>
            <div className="row">
              <input type="number" min="0" step="0.001" required placeholder="e.g. 6" value={draft.quantity}
                onChange={(e) => setDraft({ ...draft, quantity: e.target.value })} />
              <select required value={draft.unitId} onChange={(e) => setDraft({ ...draft, unitId: e.target.value })}>
                <option value="">— unit —</option>
                {activeUnits.map((u) => (
                  <option key={u.id} value={u.id}>{u.code} · {u.name}</option>
                ))}
              </select>
            </div>
          </fieldset>
          <label>
            Code
            <input value={draft.code} maxLength={64} placeholder={suggested || "generated from SKU + quantity + unit"}
              onChange={(e) => setDraft({ ...draft, code: e.target.value })} />
            <small>Leave empty to use {suggested ? <code>{suggested}</code> : "the generated code"}. Can't be changed later.</small>
          </label>
          <label>
            Description
            <input value={draft.description} maxLength={2000} placeholder="e.g. Box with 6 bottles"
              onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
          </label>
          <div className="span-2 form-actions">
            <button className="primary" type="submit">Add package</button>
            <small className="muted">Code, quantity and unit are fixed once created: archive and add a new package to change them.</small>
          </div>
        </form>
      )}
    </section>
  );
}
