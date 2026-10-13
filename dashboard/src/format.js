export const number = (value) =>
  Number(value).toLocaleString("en-US", { maximumFractionDigits: 3 });

export const money = (value) =>
  Number(value).toLocaleString("en-US", { style: "currency", currency: "BRL" });

export const dateTime = (iso) => (iso ? new Date(iso).toLocaleString() : "-");