import { useState } from "react";
import { api } from "../api.js";
import { number, money } from "../format.js";
import { usePolling } from "../hooks/usePolling.js";
import Badge, { sapTone } from "../components/Badge.jsx";
import Notice from "../components/Notice.jsx";

const emptyItem = () => ({ sku: "", quantity: "1", unitPrice: "" });

export default function Sell() {
  const products = usePolling(api.products, 20000);
  const [items, setItems] = useState([emptyItem()]);
  const [orderRef, setOrderRef] = useState("");
  const [key, setKey] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const catalog = products.data ?? [];
  const bySku = Object.fromEntries(catalog.map((p) => [p.sku, p]));
  const total = items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0);

  const update = (index, patch) =>
    setItems((list) => list.map((item, i) => (i === index ? { ...item, ...patch } : item)));

  const pickSku = (index, sku) =>
    update(index, { sku, unitPrice: bySku[sku] ? String(bySku[sku].salePrice) : "" });

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.sell({
        idempotencyKey: key,
        orderRef: orderRef.trim() || null,
        items: items.map((item) => ({
          sku: item.sku,
          quantity: Number(item.quantity),
          unitPrice: item.unitPrice === "" ? null : Number(item.unitPrice),
        })),
      });
      setResult(response);
      setKey(crypto.randomUUID());
      setItems([emptyItem()]);
      setOrderRef("");
      products.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <form className="card" onSubmit={submit}>
        <div className="card-header">
          <h3>New sale</h3>
          <input placeholder="Order reference (optional)" value={orderRef} onChange={(e) => setOrderRef(e.target.value)} />
        </div>
        <Notice>{error}</Notice>

        <div className="lines">
          {items.map((item, index) => (
            <div className="line sale" key={index}>
              <label className="field">
                <span>Product</span>
                <select value={item.sku} onChange={(e) => pickSku(index, e.target.value)} required>
                  <option value="">Select a product</option>
                  {catalog.map((p) => (
                    <option key={p.sku} value={p.sku}>{p.sku} (available: {number(p.balance)})</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>Quantity</span>
                <input type="number" min="0.001" step="any" value={item.quantity} onChange={(e) => update(index, { quantity: e.target.value })} required />
              </label>
              <label className="field">
                <span>Unit price</span>
                <input type="number" min="0" step="0.01" value={item.unitPrice} onChange={(e) => update(index, { unitPrice: e.target.value })} />
              </label>
              <button type="button" className="danger small" disabled={items.length === 1}
                onClick={() => setItems((list) => list.filter((_, i) => i !== index))}>Remove</button>
            </div>
          ))}
        </div>

        <button type="button" className="ghost" onClick={() => setItems((list) => [...list, emptyItem()])}>Add item</button>
        <div className="actions">
          <span className="total">Total: {money(total)}</span>
          <button type="submit" disabled={busy}>{busy ? "Processing..." : "Confirm sale"}</button>
        </div>
      </form>

      {result && (
        <div className="card">
          <Notice tone="ok">
            {result.status === "DUPLICATE" ? "This sale was already processed." : "Sale registered."} Reference {result.reference}
          </Notice>
          <h4>Batches used (picking order)</h4>
          <table>
            <thead><tr><th>SKU</th><th>Batch</th><th className="num">Quantity</th><th>SAP</th></tr></thead>
            <tbody>
              {result.movements.map((m) => (
                <tr key={m.id}>
                  <td>{m.sku}</td>
                  <td>{m.batchCode}</td>
                  <td className="num">{number(m.quantity)}</td>
                  <td><Badge tone={sapTone[m.sapStatus]}>{m.sapStatus}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
