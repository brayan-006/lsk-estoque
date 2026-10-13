import { useState } from "react";
import { api } from "../api.js";
import { number, money } from "../format.js";
import { usePolling } from "../hooks/usePolling.js";
import Badge from "../components/Badge.jsx";
import ErrorBanner from "../components/ErrorBanner.jsx";
import Notice from "../components/Notice.jsx";
import ProductForm from "../components/ProductForm.jsx";
import StockDetail from "../components/StockDetail.jsx";

export default function Products() {
  const { data, error, reload } = usePolling(api.products);
  const categories = usePolling(api.categories, 60000);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState(null);
  const [notice, setNotice] = useState(null);

  const term = search.trim().toLowerCase();
  const rows = (data ?? []).filter(
    (p) => p.sku.toLowerCase().includes(term) || p.description.toLowerCase().includes(term)
  );

  async function remove(product) {
    if (!window.confirm(`Remove ${product.sku}? It will be hidden from the lists, but its history is kept.`)) return;
    setNotice(null);
    try {
      await api.deactivateProduct(product.sku);
      setNotice({ tone: "ok", text: `${product.sku} removed.` });
      reload();
    } catch (e) {
      setNotice({ tone: "bad", text: e.message });
    }
  }

  function saved() {
    setForm(null);
    reload();
  }

  return (
    <>
      <ErrorBanner error={error} />
      {notice && <Notice tone={notice.tone}>{notice.text}</Notice>}
      <div className="card">
        <div className="card-header">
          <h3>Products</h3>
          <div className="toolbar">
            <input placeholder="Search by SKU or description" value={search} onChange={(e) => setSearch(e.target.value)} />
            <button onClick={() => setForm({ product: null })}>New product</button>
          </div>
        </div>
        <table className="clickable">
          <thead>
            <tr><th>SKU</th><th>Description</th><th>Class</th><th className="num">Balance</th><th className="num">Minimum</th><th>Level</th><th className="num">Price</th><th></th></tr>
          </thead>
          <tbody>
            {rows.map((p) => {
              const scale = Math.max(Number(p.minimumStock) * 3, Number(p.balance), 1);
              const percent = Math.min(100, (Number(p.balance) / scale) * 100);
              return (
                <tr key={p.sku} onClick={() => setSelected(p.sku)}>
                  <td>{p.sku}</td>
                  <td>{p.description}</td>
                  <td><Badge tone={p.abcClass === "A" ? "info" : "neutral"}>{p.abcClass}</Badge></td>
                  <td className="num">{number(p.balance)} {p.unit}</td>
                  <td className="num">{number(p.minimumStock)}</td>
                  <td>
                    <div className="bar"><div className={p.belowMinimum ? "bad" : "ok"} style={{ width: `${percent}%` }} /></div>
                  </td>
                  <td className="num">{money(p.salePrice)}</td>
                  <td>
                    <button className="ghost small" onClick={(e) => { e.stopPropagation(); setForm({ product: p }); }}>Edit</button>{" "}
                    <button className="danger small" onClick={(e) => { e.stopPropagation(); remove(p); }}>Remove</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {rows.length === 0 && <p className="muted">No products found.</p>}
      </div>
      {selected && <StockDetail sku={selected} onClose={() => setSelected(null)} />}
      {form && (
        <ProductForm product={form.product} categories={categories.data ?? []} onSaved={saved} onClose={() => setForm(null)} />
      )}
    </>
  );
}