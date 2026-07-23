import { createContext, useContext } from "react";

import type { CurrentUser } from "./identity-api";

export type AuthContextValue = {
    initialized: boolean;
    authenticated: boolean;
    user?: CurrentUser;
    userId?: string;
    username?: string;
    email?: string;
    fullName?: string;
    displayName?: string;
    avatarUrl?: string;
    shiftName?: string;
    roles?: string[];
    roleLabel?: string;

    login: () => void;
    register: () => void;
    logout: () => Promise<void>;

    hasRealmRole: (role: string) => boolean;
    hasResourceRole: (role: string, resource?: string) => boolean;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
    const ctx = useContext(AuthContext);

    if (!ctx) {
        throw new Error("useAuth must be used inside AuthProvider");
    }

    return ctx;
}
