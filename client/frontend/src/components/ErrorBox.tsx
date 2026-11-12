import { ApiError } from "../api";

/** Shows an API error (detail + per-field messages) or any other error. */
export default function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const fields = error instanceof ApiError ? Object.entries(error.fieldErrors) : [];
  return (
    <div className="error-box" role="alert">
      <strong>{error instanceof Error ? error.message : String(error)}</strong>
      {fields.length > 0 && (
        <ul>
          {fields.map(([field, msg]) => (
            <li key={field}>
              <code>{field}</code>: {msg}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
