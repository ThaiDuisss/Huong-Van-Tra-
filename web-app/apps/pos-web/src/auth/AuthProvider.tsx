import {
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react";
import { AuthContext, type AuthContextValue } from "./auth-context";
import { identityApi, type CurrentUser } from "./identity-api";
import { keycloak } from "./keycloak";
import { getQueuedAccessToken } from "./token-provider";

const USER_CACHE_KEY = "hvt.pos.currentUser";
const USER_CACHE_TTL_MS = 5 * 60 * 1000;
const TOKEN_REFRESH_MIN_VALIDITY_SECONDS = 120;
const TOKEN_REFRESH_INTERVAL_MS = 60 * 1000;

type UserCacheEntry = {
    cachedAt: number;
    user: CurrentUser;
};

let memoryUserCache: UserCacheEntry | undefined;
let pendingUserRequest: Promise<CurrentUser> | undefined;

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

function isFreshCache(entry: UserCacheEntry) {
    return Date.now() - entry.cachedAt < USER_CACHE_TTL_MS;
}

function sanitizeUserForCache(user: CurrentUser): CurrentUser {
    return {
        id: user.id,
        username: user.username,
        usernameSnapshot: user.usernameSnapshot,
        fullName: user.fullName,
        fullNameSnapshot: user.fullNameSnapshot,
        name: user.name,
        displayName: user.displayName,
        firstName: user.firstName,
        lastName: user.lastName,
        avatarUrl: user.avatarUrl,
        status: user.status,
        roles: user.roles,
        shiftName: user.shiftName,
        currentShiftName: user.currentShiftName,
        currentShift: user.currentShift
    };
}

function readStoredUserCache() {
    try {
        const rawValue = window.sessionStorage.getItem(USER_CACHE_KEY);

        if (!rawValue) {
            return undefined;
        }

        const parsedValue: unknown = JSON.parse(rawValue);

        if (!isRecord(parsedValue) || !isRecord(parsedValue.user)) {
            return undefined;
        }

        const cachedAt = parsedValue.cachedAt;

        if (typeof cachedAt !== "number") {
            return undefined;
        }

        const entry: UserCacheEntry = {
            cachedAt,
            user: parsedValue.user as CurrentUser
        };

        return isFreshCache(entry) ? entry : undefined;
    } catch {
        return undefined;
    }
}

function readUserCache() {
    if (memoryUserCache && isFreshCache(memoryUserCache)) {
        return memoryUserCache.user;
    }

    const storedEntry = readStoredUserCache();

    if (storedEntry) {
        memoryUserCache = storedEntry;
        return storedEntry.user;
    }

    return undefined;
}

function writeUserCache(user: CurrentUser) {
    const entry: UserCacheEntry = {
        cachedAt: Date.now(),
        user: sanitizeUserForCache(user)
    };

    memoryUserCache = entry;

    try {
        window.sessionStorage.setItem(USER_CACHE_KEY, JSON.stringify(entry));
    } catch {
        // Session storage can be unavailable in private or restricted contexts.
    }
}

function clearUserCache() {
    memoryUserCache = undefined;
    pendingUserRequest = undefined;

    try {
        window.sessionStorage.removeItem(USER_CACHE_KEY);
    } catch {
        // Session storage can be unavailable in private or restricted contexts.
    }
}

function loadCurrentUser() {
    const cachedUser = readUserCache();

    if (cachedUser) {
        return Promise.resolve(cachedUser);
    }

    if (!pendingUserRequest) {
        pendingUserRequest = identityApi
            .me()
            .then((currentUser) => {
                writeUserCache(currentUser);
                return currentUser;
            })
            .finally(() => {
                pendingUserRequest = undefined;
            });
    }

    return pendingUserRequest;
}

async function refreshTokenSilently() {
    if (!keycloak.authenticated) {
        return false;
    }

    const accessToken = await getQueuedAccessToken(
        TOKEN_REFRESH_MIN_VALIDITY_SECONDS
    );

    return Boolean(accessToken);
}

function getUserDisplayName(user: CurrentUser | undefined) {
    if (!user) {
        return undefined;
    }

    const fullName =
        user.fullNameSnapshot ??
        user.fullName ??
        user.name ??
        user.displayName;

    if (fullName) {
        return fullName;
    }

    const composedName = [user.firstName, user.lastName]
        .filter(Boolean)
        .join(" ");

    return (
        composedName ||
        user.usernameSnapshot ||
        user.username
    );
}

function getUserShiftName(user: CurrentUser | undefined) {
    return (
        user?.shiftName ??
        user?.currentShiftName ??
        user?.currentShift?.name ??
        user?.currentShift?.code
    );
}

function formatRoleLabel(role: string) {
    return role
        .replace(/^ROLE_/i, "")
        .replace(/[_-]+/g, " ")
        .trim()
        .toLowerCase()
        .replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
}

function getUserRoleLabel(user: CurrentUser | undefined) {
    const primaryRole = user?.roles?.find((role) => role.trim());

    if (!primaryRole) {
        return "Nhân viên POS";
    }

    return formatRoleLabel(primaryRole);
}

export function AuthProvider({ children }: { children: ReactNode }) {
    const initialized = true;
    const [authenticated, setAuthenticated] = useState(!!keycloak.authenticated);
    const [token, setToken] = useState<string | undefined>(keycloak.token);
    const [user, setUser] = useState<CurrentUser | undefined>(() =>
        keycloak.authenticated ? readUserCache() : undefined
    );

    const syncAuthState = () => {
        setAuthenticated(!!keycloak.authenticated);
        setToken(keycloak.token);
    };

    useEffect(() => {
        keycloak.onAuthSuccess = syncAuthState;
        keycloak.onAuthRefreshSuccess = syncAuthState;
        keycloak.onAuthRefreshError = () => {
            console.error("Keycloak refresh thất bại");
            syncAuthState();
        };

        keycloak.onAuthLogout = () => {
            setAuthenticated(false);
            setToken(undefined);
            setUser(undefined);
            clearUserCache();
        };

        keycloak.onTokenExpired = () => {
            void refreshTokenSilently().finally(syncAuthState);
        };

        const intervalId = window.setInterval(() => {
            void refreshTokenSilently().finally(syncAuthState);
        }, TOKEN_REFRESH_INTERVAL_MS);

        const handleVisibilityChange = () => {
            if (!document.hidden) {
                void refreshTokenSilently().finally(syncAuthState);
            }
        };

        document.addEventListener("visibilitychange", handleVisibilityChange);

        return () => {
            window.clearInterval(intervalId);
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            keycloak.onAuthSuccess = undefined;
            keycloak.onAuthRefreshSuccess = undefined;
            keycloak.onAuthRefreshError = undefined;
            keycloak.onAuthLogout = undefined;
            keycloak.onTokenExpired = undefined;
        };
    }, []);

    useEffect(() => {
        if (!authenticated) {
            clearUserCache();
            return undefined;
        }

        let active = true;

        loadCurrentUser()
            .then((currentUser) => {
                if (active) {
                    setUser(currentUser);
                }
            })
            .catch((error) => {
                console.error("Không tải được thông tin người dùng", error);

                if (active) {
                    setUser(undefined);
                }
            });

        return () => {
            active = false;
        };
    }, [authenticated, token]);

    const value = useMemo<AuthContextValue>(
        () => ({
            initialized,
            authenticated,
            user,
            userId: user?.id ?? user?.keycloakSubject,
            username:
                user?.usernameSnapshot ??
                user?.username,
            email: user?.emailSnapshot ?? user?.email,
            fullName: getUserDisplayName(user),
            displayName: getUserDisplayName(user),
            avatarUrl: user?.avatarUrl,
            shiftName: getUserShiftName(user),
            roles: user?.roles,
            roleLabel: getUserRoleLabel(user),

            login: () => {
                keycloak.login({
                    redirectUri: window.location.origin,
                });
            },

            register: () => {
                keycloak.register({
                    redirectUri: window.location.origin,
                });
            },

            async logout() {
                await keycloak.logout({
                    redirectUri: `${window.location.origin}/login`,
                });
            },

            hasRealmRole: (role: string) => {
                return keycloak.hasRealmRole(role);
            },

            hasResourceRole: (role: string, resource?: string) => {
                return keycloak.hasResourceRole(role, resource);
            },
        }),
        [initialized, authenticated, user]
    );

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
