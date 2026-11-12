import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, Category } from "../api";
import ErrorBox from "../components/ErrorBox";

type Draft = { name: string; description: string };
const EMPTY: Draft = { name: "", description: "" };

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [editing, setEditing] = useState<{ id: string } & Draft | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(() => {
    api.categories.list(showArchived).then(setCategories).catch(setError);
  }, [showArchived]);
  useEffect(load, [load]);

  const run = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      load();
      return true;
    } catch (e) {
      setError(e);
      return false;
    }
  };

  const create = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(() => api.categories.create(draft))) setDraft(EMPTY);
  };

  const save = async () => {
    if (editing && (await run(() => api.categories.update(editing.id, editing)))) setEditing(null);
  };

  return (
    <>
      <header>
        <h1>Categories</h1>
        <p>Group related products, e.g. "Whole milk" for the 1 l and the 0.5 l bottles.</p>
      </header>

      <form className="panel inline-form" onSubmit={create}>
        <label>
          Name
          <input value={draft.name} maxLength={128} required placeholder="e.g. Whole milk"
            onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </label>
        <label className="grow">
          Description
          <input value={draft.description} maxLength={2000} placeholder="optional"
            onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
        </label>
        <button className="primary" type="submit">Add category</button>
      </form>

      <ErrorBox error={error} />

      <div className="toolbar">
        <label className="check">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived
        </label>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Name</th><th>Description</th><th className="num">Products</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {categories.map((c) =>
              editing?.id === c.id ? (
                <tr key={c.id}>
                  <td><input value={editing.name} maxLength={128} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></td>
                  <td><input value={editing.description} maxLength={2000} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></td>
                  <td /><td />
                  <td className="actions">
                    <button className="primary" onClick={save}>Save</button>
                    <button onClick={() => setEditing(null)}>Cancel</button>
                  </td>
                </tr>
              ) : (
                <tr key={c.id} className={c.archived ? "archived" : ""}>
                  <td>{c.name}</td>
                  <td className="muted">{c.description}</td>
                  <td className="num">
                    {c.productCount > 0 ? <Link to={`/?categoryId=${c.id}`}>{c.productCount}</Link> : 0}
                  </td>
                  <td>{c.archived ? <span className="badge">archived</span> : <span className="badge ok">active</span>}</td>
                  <td className="actions">
                    {c.archived ? (
                      <button onClick={() => run(() => api.categories.restore(c.id))}>Restore</button>
                    ) : (
                      <>
                        <button onClick={() => setEditing({ id: c.id, name: c.name, description: c.description ?? "" })}>Edit</button>
                        <button onClick={() => confirm(`Archive category "${c.name}"? Its products keep it, but it can't be assigned anymore.`) && run(() => api.categories.archive(c.id))}>
                          Archive
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ),
            )}
            {categories.length === 0 && (
              <tr><td colSpan={5} className="empty">No categories yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
