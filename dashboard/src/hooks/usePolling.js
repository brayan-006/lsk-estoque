import { useCallback, useEffect, useState } from "react";

export function usePolling(fetcher, intervalMs = 15000) {
  const [state, setState] = useState({ data: null, error: null, loading: true });

  const load = useCallback(async () => {
    try {
      const data = await fetcher();
      setState({ data, error: null, loading: false });
    } catch (error) {
      setState((previous) => ({ ...previous, error: error.message, loading: false }));
    }
  }, [fetcher]);

  useEffect(() => {
    load();
    const id = setInterval(load, intervalMs);
    return () => clearInterval(id);
  }, [load, intervalMs]);

  return { ...state, reload: load };
}