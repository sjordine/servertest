// Typed client for the products service (through the gateway at /api/products).

export type UnitRef = { id: string; code: string; name: string };
export type Unit = UnitRef & { archived: boolean };

export type CategoryRef = { id: string; name: string };
export type Category = CategoryRef & { description?: string; productCount: number; archived: boolean };

export type Content = { quantity: number; unit: UnitRef; label: string };

export type ProductRef = { id: string; sku: string; description: string; archived: boolean };

export type ProductSummary = {
  id: string;
  sku: string;
  description: string;
  category?: CategoryRef;
  content?: Content;
  packageCount: number;
  archived: boolean;
};

export type Package = {
  id: string;
  code: string;
  description?: string;
  quantity: number;
  unit: UnitRef;
  label: string;
  product: ProductRef;
  archived: boolean;
};

export type ProductDetail = Omit<ProductSummary, "packageCount"> & {
  createdAt: string;
  updatedAt: string;
  archivedAt?: string;
  packages: Package[];
};

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

export type ProductInput = {
  description: string;
  categoryId?: string | null;
  contentQuantity?: number | null;
  contentUnitId?: string | null;
};

/** Error carrying the server's ProblemDetail: `message` is its detail, `fieldErrors` the per-field messages. */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

const BASE = "/api/products";

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: any = undefined;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    /* not JSON */
  }
  if (!res.ok) {
    throw new ApiError(res.status, data?.detail ?? data?.error ?? `HTTP ${res.status}`, data?.errors ?? {});
  }
  return data as T;
}

const qs = (params: Record<string, string | number | boolean | undefined | null>) => {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "" && v !== false) p.set(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : "";
};

export const api = {
  units: {
    list: (includeArchived = false) => request<Unit[]>("GET", `/units${qs({ includeArchived })}`),
    create: (body: { code: string; name: string }) => request<Unit>("POST", "/units", body),
    update: (id: string, body: { code: string; name: string }) => request<Unit>("PUT", `/units/${id}`, body),
    archive: (id: string) => request<void>("DELETE", `/units/${id}`),
    restore: (id: string) => request<Unit>("POST", `/units/${id}/restore`),
  },
  categories: {
    list: (includeArchived = false) => request<Category[]>("GET", `/categories${qs({ includeArchived })}`),
    create: (body: { name: string; description?: string }) => request<Category>("POST", "/categories", body),
    update: (id: string, body: { name: string; description?: string }) =>
      request<Category>("PUT", `/categories/${id}`, body),
    archive: (id: string) => request<void>("DELETE", `/categories/${id}`),
    restore: (id: string) => request<Category>("POST", `/categories/${id}/restore`),
  },
  products: {
    list: (params: { q?: string; categoryId?: string; includeArchived?: boolean; page?: number; size?: number }) =>
      request<Page<ProductSummary>>("GET", qs(params)),
    get: (id: string) => request<ProductDetail>("GET", `/${id}`),
    create: (body: ProductInput & { sku: string }) => request<ProductDetail>("POST", "", body),
    update: (id: string, body: ProductInput) => request<ProductDetail>("PUT", `/${id}`, body),
    archive: (id: string) => request<void>("DELETE", `/${id}`),
    restore: (id: string) => request<ProductDetail>("POST", `/${id}/restore`),
  },
  packages: {
    list: (productId: string, includeArchived = false) =>
      request<Package[]>("GET", `/${productId}/packages${qs({ includeArchived })}`),
    create: (productId: string, body: { code?: string; description?: string; unitId: string; quantity: number }) =>
      request<Package>("POST", `/${productId}/packages`, body),
    update: (productId: string, packageId: string, body: { description?: string }) =>
      request<Package>("PUT", `/${productId}/packages/${packageId}`, body),
    archive: (productId: string, packageId: string) =>
      request<void>("DELETE", `/${productId}/packages/${packageId}`),
    restore: (productId: string, packageId: string) =>
      request<Package>("POST", `/${productId}/packages/${packageId}/restore`),
  },
};

/** Mirrors the server's default package code: <SKU>-<QTY><UNIT>, e.g. MILK-WH-1L-6UN. */
export function suggestPackageCode(sku: string, quantity: string, unitCode?: string): string {
  const q = Number(quantity);
  if (!sku || !unitCode || !quantity || !(q > 0)) return "";
  return `${sku}-${String(q)}${unitCode}`.toUpperCase();
}
