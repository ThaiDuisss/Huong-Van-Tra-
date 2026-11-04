import Keycloak from "keycloak-js";

export const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL,
    realm: import.meta.env.VITE_KEYCLOAK_REALM,
    clientId:
        import.meta.env.VITE_KEYCLOAK_CLIENT_ID
});

let initPromise: Promise<boolean> | null = null;

export function initKeycloak(): Promise<boolean> {
    if (!initPromise) {
        initPromise = keycloak.init({
            onLoad: "check-sso",
            pkceMethod: "S256",

            checkLoginIframe: false,

            // KHONG dung silentCheckSsoRedirectUri: no lam check-sso chay trong IFRAME AN
            // tro sang Keycloak, va Keycloak chan framing xuyen origin bang CA HAI header
            //   Content-Security-Policy: frame-ancestors 'self'
            //   X-Frame-Options: SAMEORIGIN
            // -> "Framing 'https://auth...' violates ... frame-ancestors 'self'".
            //
            // Khong chi la chuyen cau hinh: X-Frame-Options khong the cho phep MOT origin
            // cu the (ALLOW-FROM da bi cac browser hien dai bo), nen muon giu silent SSO
            // thi phai XOA han X-Frame-Options tren realm - noi long cho moi thu. Va silent
            // check-sso qua iframe von da khong con chay duoc tren Safari va tren Chrome khi
            // chan third-party cookie, vi request iframe la cross-site nen khong mang cookie
            // session cua Keycloak.
            //
            // Bo tham so nay thi check-sso chuyen sang redirect ca trang: khong iframe,
            // khong phu thuoc CSP, khong phu thuoc third-party cookie. Doi lai nguoi dung
            // thay mot nhip chuyen trang khi mo app lan dau.
        });
    }

    return initPromise;
}
