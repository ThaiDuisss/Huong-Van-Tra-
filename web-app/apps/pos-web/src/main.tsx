import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { keycloak } from "./auth/keycloak";
import App from "./App";

async function bootstrap(): Promise<void> {
  try {
    await keycloak.init({
      onLoad: "login-required",
      flow: "standard",
      pkceMethod: "S256",

      // Quan trọng: tắt iframe check để tránh lỗi:
      // Timeout when waiting for 3rd party check iframe message
      checkLoginIframe: false,
    });

    createRoot(document.getElementById("root")!).render(
      <StrictMode>
        <App />
      </StrictMode>
    );
  } catch (error) {
    console.error("Không thể khởi tạo Keycloak", error);

    document.body.innerHTML = `
      <main>
        <h1>Không thể kết nối hệ thống đăng nhập</h1>
        <p>Vui lòng thử tải lại trang.</p>
      </main>
    `;
  }
}

void bootstrap();