export type QueryParamPrimitive =
    | string
    | number
    | boolean
    | Date;

export type QueryParamValue =
    | QueryParamPrimitive
    | QueryParamPrimitive[]
    | null
    | undefined;

export type QueryParams = Record<string, QueryParamValue>;

function stringifyQueryValue(value: QueryParamPrimitive): string {
    if (value instanceof Date) {
        return value.toISOString();
    }

    return String(value);
}

export function buildQueryString(
    query?: QueryParams
): string {
    if (!query) {
        return "";
    }

    const searchParams = new URLSearchParams();

    for (const [key, value] of Object.entries(query)) {
        if (value === null || value === undefined) {
            continue;
        }

        if (Array.isArray(value)) {
            for (const item of value) {
                searchParams.append(key, stringifyQueryValue(item));
            }

            continue;
        }

        searchParams.append(key, stringifyQueryValue(value));
    }

    const queryString = searchParams.toString();

    return queryString ? `?${queryString}` : "";
}

export function appendQueryParams(
    path: string,
    query?: QueryParams
): string {
    const queryString = buildQueryString(query);

    if (!queryString) {
        return path;
    }

    return path.includes("?")
        ? `${path}&${queryString.slice(1)}`
        : `${path}${queryString}`;
}