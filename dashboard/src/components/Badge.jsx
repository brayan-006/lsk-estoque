export default function Badge({ tone = "neutral", children }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

export const sapTone = {
  ENVIADO: "ok",
  PENDENTE: "warn",
  ENVIANDO: "info",
  ERRO: "bad",
};