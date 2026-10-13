import { useState } from "react";
import { api } from "../api.js";
import { number, dateTime } from "../format.js";
import { usePolling } from "../hooks/usePolling.js";
import Badge, { sapTone } from "../components/Badge.jsx";
import ErrorBanner from "../components/ErrorBanner.jsx";
import Notice from "../components/Notice.jsx";

export default function Movements() {
  const { data, error, reload } = usePolling(api.movements, 10000);
  const [type, setType] = useState("");
  const [status, setStatus] = useState("");
  const [notice, setNotice] = useState(null);
  const [retrying, setRetrying] = useState(null);

  const rows = (data ?? []).filter((m) => (!type || m.type === type) && (!status || m.sapStatus === status));

  async function retry(movement) {
    setRetrying(movement.id);
    setNotice(null);
    try {
      const response = await api.retrySap(movement.id);
      const statuses = response.movements.map((m) => m.sapStatus).join(", ");
      setNotice({ tone: "ok", text: `Retry executed for ${response.reference}. SAP status: ${statuses}` });
      await reload();
    } catch (e) {
      setNotice({ tone: "bad", text: e.message });
    } finally {
      setRetrying(null);
    }
  }

  return (
    <>
      <ErrorBanner error={error} />
      <div className="card">
        <div className="card-header">
          <h3>Recent movements</h3>
          <div className="toolbar">
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">All types</option>
              <option value="ENTRADA">ENTRADA</option>
              <option value="SAIDA">SAIDA</option>
            </select>
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All SAP statuses</option>
              <option value="PENDENTE">PENDENTE</option>
              <option value="ENVIADO">ENVIADO</option>
              <option value="ERRO">ERRO</option>
            </select>
          </div>
        </div>
        {notice && <Notice tone={notice.tone}>{notice.text}</Notice>}
        <table>
          <thead>
            <tr><th>#</th><th>Type</th><th>SKU</th><th>Batch</th><th className="num">Qty</th><th>Reference</th><th>SAP</th><th className="num">Tries</th><th>SAP doc</th><th>Date</th><th></th></tr>
          </thead>
          <tbody>
            {rows.map((m) => (
              <tr key={m.id}>
                <td>{m.id}</td>
                <td><Badge tone={m.type === "ENTRADA" ? "ok" : "neutral"}>{m.type}</Badge></td>
                <td>{m.sku}</td>
                <td>{m.batchCode}</td>
                <td className="num">{number(m.quantity)}</td>
                <td className="mono" title={m.reference}>{m.reference.length > 28 ? m.reference.slice(0, 28) + "..." : m.reference}</td>
                <td title={m.sapError ?? ""}><Badge tone={sapTone[m.sapStatus]}>{m.sapStatus}</Badge></td>
                <td className="num">{m.sapAttempts}</td>
                <td>{m.sapDocEntry ?? "-"}</td>
                <td>{dateTime(m.createdAt)}</td>
                <td>
                  {(m.sapStatus === "ERRO" || m.sapStatus === "PENDENTE") && (
                    <button className="ghost small" disabled={retrying === m.id} onClick={() => retry(m)}>
                      {retrying === m.id ? "Sending..." : "Retry SAP"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <p className="muted">No movements match the filters.</p>}
      </div>
    </>
  );
}