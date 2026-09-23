import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, clearToken, setToken, token } from "../../api";

/** Mirrors com.chengjing.identity.AccountView. */
export type Account = {
  id: string;
  email: string;
  displayName: string;
  createdAt: string;
};

type SessionValue = {
  /** The signed-in account, or null. */
  account: Account | null;
  /** False until the stored token has been checked, so pages can avoid flashing a signed-out view. */
  ready: boolean;
  signIn: (nextToken: string, nextAccount: Account) => void;
  signOut: () => Promise<void>;
};

const SessionContext = createContext<SessionValue | null>(null);

/**
 * Holds the signed-in account for the whole app.
 *
 * <p>On load, a stored token is verified against the server rather than trusted: a token that was
 * signed out or invalidated elsewhere must not leave the UI looking signed in. A rejected token is
 * discarded so the next visit starts clean.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!token()) {
      setReady(true);
      return;
    }
    api<Account>("/auth/me")
      .then(setAccount)
      .catch(() => clearToken())
      .finally(() => setReady(true));
  }, []);

  const signIn = useCallback((nextToken: string, nextAccount: Account) => {
    setToken(nextToken);
    setAccount(nextAccount);
  }, []);

  const signOut = useCallback(async () => {
    try {
      await api("/auth/logout", { method: "POST" });
    } catch {
      // Already expired or signed out elsewhere — the local token is dropped either way.
    }
    clearToken();
    setAccount(null);
  }, []);

  const value = useMemo(
    () => ({ account, ready, signIn, signOut }),
    [account, ready, signIn, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error("useSession 必须在 SessionProvider 内使用");
  }
  return value;
}
