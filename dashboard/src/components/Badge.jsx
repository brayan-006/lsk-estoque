export default function Badge({ tone = "neutral", children }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

export const sapTone = {
  SENT: "ok",
  PENDING: "warn",
  SENDING: "info",
  ERROR: "bad",
};
