import { useState } from "react";
import { api } from "../api.js";
import { number } from "../format.js";
import { usePolling } from "../hooks/usePolling.js";
import Notice from "../components/Notice.jsx";

const emptyLine = () => ({ sku: "", batch: "", quantity: "1", cost: "", manufactured: "", expiry: "" });

const demoKey = () => "35" + Array.from({ length: 42 }, () => Math.floor(Math.random() * 10)).join("");

export default function Receive() {
  const products = usePolling(api.products, 30000);
  const [invoiceNumber, setInvoiceNumber] = useState("1");
  const [accessKey, setAccessKey] = useState(demoKey);
  const [lines, setLines] = useState([emptyLine()]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const catalog = products.data ?? [];
  const update = (index, patch) =>
    setLines((list) => list.map((line, i) => (i === index ? { ...line, ...patch } : line)));

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.receive({
        chaveAcesso: accessKey.trim(),
        numero: invoiceNumber.trim(),
        itens: lines.map((line) => ({
          sku: line.sku,
          codigoLote: line.batch.trim(),
          quantidade: Number(line.quantity),
          custoUnitario: Number(line.cost),
          dataFabricacao: line.manufactured || null,
          dataValidade: line.expiry || null,
        })),
      });
      setResult(response);
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
        <div className="card-header"><h3>Receive goods (NF-e)</h3></div>
        <Notice>{error}</Notice>

        <div className="form-grid">
          <label className="field">
            <span>Invoice number</span>
            <input value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} required />
          </label>
          <label className="field">
            <span>Access key (44 digits)</span>
            <input value={accessKey} onChange={(e) => setAccessKey(e.target.value)} minLength={44} maxLength={44} required />
          </label>
        </div>
        <button type="button" className="ghost small" onClick={() => setAccessKey(demoKey())}>Generate demo key</button>

        <h4 style={{ marginTop: 18 }}>Items</h4>
        <div className="lines">
          {lines.map((line, index) => (
            <div className="line receipt" key={index}>
              <label className="field">
                <span>Product</span>
                <select value={line.sku} onChange={(e) => update(index, { sku: e.target.value })} required>
                  <option value="">Select a product</option>
                  {catalog.map((p) => <option key={p.sku} value={p.sku}>{p.sku}</option>)}
                </select>
              </label>
              <label className="field">
                <span>Batch</span>
                <input value={line.batch} onChange={(e) => update(index, { batch: e.target.value })} required />
              </label>
              <label className="field">
                <span>Quantity</span>
                <input type="number" min="0.001" step="any" value={line.quantity} onChange={(e) => update(index, { quantity: e.target.value })} required />
              </label>
              <label className="field">
                <span>Unit cost</span>
                <input type="number" min="0" step="0.01" value={line.cost} onChange={(e) => update(index, { cost: e.target.value })} required />
              </label>
              <label className="field">
                <span>Manufactured</span>
                <input type="date" value={line.manufactured} onChange={(e) => update(index, { manufactured: e.target.value })} />
              </label>
              <label className="field">
                <span>Expiry</span>
                <input type="date" value={line.expiry} onChange={(e) => update(index, { expiry: e.target.value })} />
              </label>
              <button type="button" className="danger small" disabled={lines.length === 1}
                onClick={() => setLines((list) => list.filter((_, i) => i !== index))}>Remove</button>
            </div>
          ))}
        </div>

        <button type="button" className="ghost" onClick={() => setLines((list) => [...list, emptyLine()])}>Add item</button>
        <div className="actions">
          <span className="total">Submitting the same access key again returns a conflict (idempotency).</span>
          <button type="submit" disabled={busy}>{busy ? "Saving..." : "Register receipt"}</button>
        </div>
      </form>

      {result && (
        <div className="card">
          <Notice tone="ok">Receipt registered. Reference {result.referencia} ({result.itensProcessados} item(s)).</Notice>
          <table>
            <thead><tr><th>SKU</th><th>Batch</th><th className="num">Received</th><th className="num">New balance</th></tr></thead>
            <tbody>
              {result.lotes.map((batch) => (
                <tr key={batch.codigoLote}>
                  <td>{batch.sku}</td>
                  <td>{batch.codigoLote}</td>
                  <td className="num">{number(batch.quantidade)}</td>
                  <td className="num">{number(batch.saldoDoSku)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}