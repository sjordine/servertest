import { FormEvent, useCallback, useEffect, useState } from "react";
import { api, Unit } from "../api";
import ErrorBox from "../components/ErrorBox";

type Draft = { code: string; name: string };
const EMPTY: Draft = { code: "", name: "" };

export default function UnitsPage() {
  const [units, setUnits] = useState<Unit[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [editing, setEditing] = useState<{ id: string } & Draft | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(() => {
    api.units.list(showArchived).then(setUnits).catch(setError);
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
    if (await run(() => api.units.create(draft))) setDraft(EMPTY);
  };

  const save = async () => {
    if (editing && (await run(() => api.units.update(editing.id, editing)))) setEditing(null);
  };

  return (
    <>
      <header>
        <h1>Units of measurement</h1>
        <p>Units used to describe product content and package quantities (un, kg, l…).</p>
      </header>

      <form className="panel inline-form" onSubmit={create}>
        <label>
          Code
          <input value={draft.code} maxLength={16} required placeholder="e.g. kg"
            onChange={(e) => setDraft({ ...draft, code: e.target.value })} />
        </label>
        <label className="grow">
          Name
          <input value={draft.name} maxLength={64} required placeholder="e.g. Kilogram"
            onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </label>
        <button className="primary" type="submit">Add unit</button>
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
            <tr><th>Code</th><th>Name</th><th>Status</th><th /></tr>
          </thead>
          <tbody>
            {units.map((u) =>
              editing?.id === u.id ? (
                <tr key={u.id}>
                  <td><input value={editing.code} maxLength={16} onChange={(e) => setEditing({ ...editing, code: e.target.value })} /></td>
                  <td><input value={editing.name} maxLength={64} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></td>
                  <td />
                  <td className="actions">
                    <button className="primary" onClick={save}>Save</button>
                    <button onClick={() => setEditing(null)}>Cancel</button>
                  </td>
                </tr>
              ) : (
                <tr key={u.id} className={u.archived ? "archived" : ""}>
                  <td><code>{u.code}</code></td>
                  <td>{u.name}</td>
                  <td>{u.archived ? <span className="badge">archived</span> : <span className="badge ok">active</span>}</td>
                  <td className="actions">
                    {u.archived ? (
                      <button onClick={() => run(() => api.units.restore(u.id))}>Restore</button>
                    ) : (
                      <>
                        <button onClick={() => setEditing({ id: u.id, code: u.code, name: u.name })}>Edit</button>
                        <button onClick={() => confirm(`Archive unit "${u.code}"? Existing packages keep it, new ones can't use it.`) && run(() => api.units.archive(u.id))}>
                          Archive
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ),
            )}
            {units.length === 0 && (
              <tr><td colSpan={4} className="empty">No units.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
