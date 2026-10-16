import { useEffect, useState } from "react";
import { api } from "../api.js";
import { number, money } from "../format.js";
import Badge from "./Badge.jsx";

export default function StockDetail({ sku, onClose }) {
  const [stock, setStock] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    setStock(null);
    setError(null);
    api.stock(sku).then(setStock).catch((e) => setError(e.message));
  }, [sku]);

  return (
    <aside className="drawer">
      <div className="drawer-header">
        <div>
          <h3>{sku}</h3>
          <span className="muted">{stock?.description}</span>
        </div>
        <button className="ghost" onClick={onClose}>Close</button>
      </div>

      {error && <div className="banner">{error}</div>}
      {!stock && !error && <p className="muted">Loading...</p>}

      {stock && (
        <>
          <div className="stats">
            <div><span className="muted">Balance</span><strong>{number(stock.availableBalance)} {stock.unit}</strong></div>
            <div><span className="muted">Minimum</span><strong>{number(stock.minimumStock)}</strong></div>
            <div><span className="muted">Average cost</span><strong>{money(stock.averageCost)}</strong></div>
            <div><span className="muted">ABC class</span><strong>{stock.abcClass}</strong></div>
          </div>

          <h4>Batches in picking order</h4>
          <table>
            <thead>
              <tr><th>#</th><th>Batch</th><th>Expiry</th><th>Days left</th><th className="num">Qty</th><th>Rule</th></tr>
            </thead>
            <tbody>
              {stock.batchesInPickingOrder.map((batch) => (
                <tr key={batch.batchCode}>
                  <td>{batch.pickingOrder}</td>
                  <td>{batch.batchCode}</td>
                  <td>{batch.expiryDate ?? "-"}</td>
                  <td>{batch.daysUntilExpiry ?? "-"}</td>
                  <td className="num">{number(batch.quantity)}</td>
                  <td><Badge tone={batch.criterion === "FEFO" ? "info" : "neutral"}>{batch.criterion}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </aside>
  );
}
