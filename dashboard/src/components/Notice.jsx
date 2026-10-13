export default function Notice({ tone = "bad", children }) {
  if (!children) return null;
  return <div className={`notice ${tone}`}>{children}</div>;
}