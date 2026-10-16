import { useState } from "react";
import Overview from "./pages/Overview.jsx";
import Products from "./pages/Products.jsx";
import Sell from "./pages/Sell.jsx";
import Receive from "./pages/Receive.jsx";
import Movements from "./pages/Movements.jsx";

const TABS = [
  { id: "overview", label: "Overview", view: <Overview /> },
  { id: "products", label: "Products", view: <Products /> },
  { id: "sell", label: "New sale", view: <Sell /> },
  { id: "receive", label: "Receive goods", view: <Receive /> },
  { id: "movements", label: "Movements", view: <Movements /> },
];

export default function App() {
  const [active, setActive] = useState("overview");

  return (
    <div className="layout">
      <header className="topbar">
        <div className="brand">LSK <span>Inventory</span></div>
        <nav>
          {TABS.map((tab) => (
            <button key={tab.id} className={tab.id === active ? "tab active" : "tab"} onClick={() => setActive(tab.id)}>
              {tab.label}
            </button>
          ))}
        </nav>
      </header>
      <main>{TABS.find((tab) => tab.id === active).view}</main>
    </div>
  );
}
