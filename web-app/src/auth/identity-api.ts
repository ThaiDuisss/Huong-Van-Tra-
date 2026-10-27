import { apiClient } from "../api/api-client";

export interface CurrentUser {
    id?: string;
    keycloakSubject?: string;
    issuer?: string;
    username?: string;
    usernameSnapshot?: string;
    email?: string;
    emailSnapshot?: string;
    emailVerified?: boolean;
    fullName?: string;
    fullNameSnapshot?: string;
    name?: string;
    displayName?: string;
    firstName?: string;
    lastName?: string;
    avatarUrl?: string;
    status?: string;
    roles?: string[];
    shiftName?: string;
    currentShiftName?: string;
    currentShift?: {
        name?: string;
        code?: string;
    };
}

export const identityApi = {
    me() {
        return apiClient.get<CurrentUser>("/identity/me");
    }
};
