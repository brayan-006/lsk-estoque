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
          <span className="muted">{stock?.descricao}</span>
        </div>
        <button className="ghost" onClick={onClose}>Close</button>
      </div>

      {error && <div className="banner">{error}</div>}
      {!stock && !error && <p className="muted">Loading...</p>}

      {stock && (
        <>
          <div className="stats">
            <div><span className="muted">Balance</span><strong>{number(stock.saldoDisponivel)} {stock.unidade}</strong></div>
            <div><span className="muted">Minimum</span><strong>{number(stock.estoqueMinimo)}</strong></div>
            <div><span className="muted">Average cost</span><strong>{money(stock.custoMedio)}</strong></div>
            <div><span className="muted">ABC class</span><strong>{stock.classeAbc}</strong></div>
          </div>

          <h4>Batches in picking order</h4>
          <table>
            <thead>
              <tr><th>#</th><th>Batch</th><th>Expiry</th><th>Days left</th><th className="num">Qty</th><th>Rule</th></tr>
            </thead>
            <tbody>
              {stock.lotesEmOrdemDeSaida.map((batch) => (
                <tr key={batch.codigoLote}>
                  <td>{batch.ordemDeSaida}</td>
                  <td>{batch.codigoLote}</td>
                  <td>{batch.dataValidade ?? "-"}</td>
                  <td>{batch.diasParaVencer ?? "-"}</td>
                  <td className="num">{number(batch.quantidade)}</td>
                  <td><Badge tone={batch.criterio === "FEFO" ? "info" : "neutral"}>{batch.criterio}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </aside>
  );
}