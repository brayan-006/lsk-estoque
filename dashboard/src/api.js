const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

async function request(path, options = {}) {
  const response = await fetch(BASE + path, {
    ...options,
    headers: options.body ? { "Content-Type": "application/json" } : undefined,
  });
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const problem = await response.json();
      message = problem.detail ?? problem.title ?? message;
    } catch {
      // keep the default message
    }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

const send = (method, body) => ({ method, body: JSON.stringify(body) });

export const api = {
  products: () => request("/api/v1/products"),
  alerts: () => request("/api/v1/alerts"),
  movements: () => request("/api/v1/movements?limit=50"),
  categories: () => request("/api/v1/categories"),
  systemStatus: () => request("/api/v1/system/status"),
  stock: (sku) => request(`/api/v1/estoque/${encodeURIComponent(sku)}`),
  recalculateAbc: () => request("/api/v1/estoque/curva-abc/recalcular", { method: "POST" }),
  sell: (payload) => request("/api/v1/sales", send("POST", payload)),
  receive: (payload) => request("/api/v1/estoque/entradas/nfe", send("POST", payload)),
  createProduct: (payload) => request("/api/v1/products", send("POST", payload)),
  updateProduct: (sku, payload) => request(`/api/v1/products/${encodeURIComponent(sku)}`, send("PUT", payload)),
  deactivateProduct: (sku) => request(`/api/v1/products/${encodeURIComponent(sku)}`, { method: "DELETE" }),
  retrySap: (id) => request(`/api/v1/movements/${id}/sap-retry`, { method: "POST" }),
};