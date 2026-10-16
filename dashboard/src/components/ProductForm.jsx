import { useState } from "react";
import { api } from "../api.js";
import Modal from "./Modal.jsx";
import Notice from "./Notice.jsx";

export default function ProductForm({ product, categories, onSaved, onClose }) {
  const editing = Boolean(product);
  const [form, setForm] = useState({
    sku: product?.sku ?? "",
    description: product?.description ?? "",
    category: "",
    unit: product?.unit ?? "UN",
    minimumStock: product?.minimumStock ?? 0,
    salePrice: product?.salePrice ?? 0,
    controlsExpiry: true,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const set = (field) => (event) => {
    const value = event.target.type === "checkbox" ? event.target.checked : event.target.value;
    setForm((current) => ({ ...current, [field]: value }));
  };

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const numbers = { minimumStock: Number(form.minimumStock), salePrice: Number(form.salePrice) };
      if (editing) {
        await api.updateProduct(product.sku, { description: form.description, unit: form.unit, ...numbers });
      } else {
        await api.createProduct({ ...form, ...numbers });
      }
      onSaved();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={editing ? `Edit ${product.sku}` : "New product"} onClose={onClose}>
      <form onSubmit={submit}>
        <Notice>{error}</Notice>
        <div className="form-grid">
          <label className="field">
            <span>SKU</span>
            <input value={form.sku} onChange={set("sku")} disabled={editing} required />
          </label>
          <label className="field">
            <span>Unit</span>
            <input value={form.unit} onChange={set("unit")} maxLength={10} required />
          </label>
          <label className="field wide">
            <span>Description</span>
            <input value={form.description} onChange={set("description")} required />
          </label>
          {!editing && (
            <label className="field wide">
              <span>Category (pick one or type a new name)</span>
              <input list="categories" value={form.category} onChange={set("category")} required />
              <datalist id="categories">
                {categories.map((name) => <option key={name} value={name} />)}
              </datalist>
            </label>
          )}
          <label className="field">
            <span>Minimum stock</span>
            <input type="number" min="0" step="any" value={form.minimumStock} onChange={set("minimumStock")} required />
          </label>
          <label className="field">
            <span>Sale price</span>
            <input type="number" min="0" step="0.01" value={form.salePrice} onChange={set("salePrice")} required />
          </label>
          {!editing && (
            <label className="check field wide">
              <input type="checkbox" checked={form.controlsExpiry} onChange={set("controlsExpiry")} />
              <span>Controls expiry date (FEFO)</span>
            </label>
          )}
        </div>
        <div className="actions">
          <button type="button" className="ghost" onClick={onClose}>Cancel</button>
          <button type="submit" disabled={busy}>{busy ? "Saving..." : "Save"}</button>
        </div>
      </form>
    </Modal>
  );
}
