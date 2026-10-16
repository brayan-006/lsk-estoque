import { useState } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { api } from "../api.js";
import { number, dateTime } from "../format.js";
import { usePolling } from "../hooks/usePolling.js";
import ErrorBanner from "../components/ErrorBanner.jsx";

const COLORS = ["#4f8cff", "#f5b942", "#8892a6"];

export default function Overview() {
  const products = usePolling(api.products);
  const alerts = usePolling(api.alerts);
  const movements = usePolling(api.movements);
  const system = usePolling(api.systemStatus, 30000);
  const [busy, setBusy] = useState(false);

  const list = products.data ?? [];
  const sapIssues = (movements.data ?? []).filter((m) => m.sapStatus === "PENDING" || m.sapStatus === "ERROR").length;
  const abc = ["A", "B", "C"].map((c) => ({ name: `Class ${c}`, value: list.filter((p) => p.abcClass === c).length }));
  const hasData = abc.some((slice) => slice.value > 0);

  async function recalculate() {
    setBusy(true);
    try {
      await api.recalculateAbc();
      await products.reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <ErrorBanner error={products.error ?? alerts.error ?? movements.error} />

      <section className="kpis">
        <div className="card kpi"><span className="muted">Products</span><strong>{list.length}</strong></div>
        <div className="card kpi"><span className="muted">Below minimum</span><strong>{list.filter((p) => p.belowMinimum).length}</strong></div>
        <div className="card kpi"><span className="muted">Open alerts</span><strong>{alerts.data?.length ?? 0}</strong></div>
        <div className="card kpi"><span className="muted">SAP pending or failed</span><strong>{sapIssues}</strong>
          <span className="muted">Integration: {system.data ? (system.data.sapEnabled ? "enabled" : "disabled") : "..."}</span>
        </div>
      </section>

      <section className="grid2">
        <div className="card">
          <div className="card-header">
            <h3>ABC analysis</h3>
            <button onClick={recalculate} disabled={busy}>{busy ? "Recalculating..." : "Recalculate"}</button>
          </div>
          {hasData ? (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={abc} dataKey="value" nameKey="name" innerRadius={60} outerRadius={90} paddingAngle={3}>
                  {abc.map((slice, i) => <Cell key={slice.name} fill={COLORS[i]} />)}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <p className="muted">No products yet.</p>
          )}
        </div>

        <div className="card">
          <h3>Open stock alerts</h3>
          {alerts.data?.length ? (
            <table>
              <thead><tr><th>SKU</th><th className="num">Balance</th><th className="num">Minimum</th><th>Since</th></tr></thead>
              <tbody>
                {alerts.data.map((alert) => (
                  <tr key={alert.id}>
                    <td>{alert.sku}</td>
                    <td className="num">{number(alert.balance)}</td>
                    <td className="num">{number(alert.minimumStock)}</td>
                    <td>{dateTime(alert.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="muted">No open alerts. Every product is above its minimum.</p>
          )}
        </div>
      </section>
    </>
  );
}
