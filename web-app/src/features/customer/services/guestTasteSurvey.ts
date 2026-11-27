import { useQuery, useQueryClient } from "@tanstack/react-query";

import type { TasteCriteria } from "./tasteSurvey.api";

export interface GuestTasteSurvey {
    taxonomyVersion: string;
    taxonomySha256: string;
    preparationCode?: string;
    criteria: TasteCriteria;
    savedAt: string;
}

export function guestTasteSurveyKey(scope: string) {
    return ["guest-taste-survey", scope] as const;
}

export function useGuestTasteSurvey(scope: string | undefined) {
    return useQuery<GuestTasteSurvey | null>({
        queryKey: guestTasteSurveyKey(scope ?? ""),
        queryFn: () => null,
        enabled: false,
        gcTime: Infinity,
        staleTime: Infinity
    }).data ?? null;
}

export function useSaveGuestTasteSurvey() {
    const queryClient = useQueryClient();
    return (scope: string, survey: GuestTasteSurvey) => {
        queryClient.setQueryDefaults(guestTasteSurveyKey(scope), { gcTime: Infinity, staleTime: Infinity });
        queryClient.setQueryData(guestTasteSurveyKey(scope), survey);
    };
}
