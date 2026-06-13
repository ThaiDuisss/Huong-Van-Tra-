import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { keycloak } from "./auth/keycloak";
import App from "./App";

async function bootstrap(): Promise<void> {
  try {
    const authenticated =
      await keycloak.init({
        onLoad: "login-required",
        flow: "standard",
        pkceMethod: "S256",
        checkLoginIframe: true
      });

    if (!authenticated) {
      await keycloak.login();

      return;
    }

    createRoot(
      document.getElementById("root")!
    ).render(
      <StrictMode>
        <App />
      </StrictMode>
    );
  } catch (error) {
    console.error(
      "Không thể khởi tạo Keycloak",
      error
    );

    document.body.innerHTML = `
      <main>
        <h1>Không thể kết nối hệ thống đăng nhập</h1>
        <p>Vui lòng thử tải lại trang.</p>
      </main>
    `;
  }
}

void bootstrap();