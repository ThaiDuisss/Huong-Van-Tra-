import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import App from "./App";
import { registerServiceWorker } from "./app/pwa/register-service-worker";
import { initKeycloak } from "./auth/keycloak";
import { AuthProvider } from "./auth/AuthProvider";

async function bootstrap(): Promise<void> {
  const rootElement = document.getElementById("root");

  if (!rootElement) {
    throw new Error("Không tìm thấy element #root");
  }

  try {
    await initKeycloak();

    createRoot(rootElement).render(
      <StrictMode>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </StrictMode>
    );
  } catch (error) {
    console.error("Không thể khởi tạo Keycloak", error);

    rootElement.innerHTML = `
      <main style="padding: 24px">
        <h1>Không thể kết nối hệ thống đăng nhập</h1>
        <p>Vui lòng thử tải lại trang.</p>
      </main>
    `;
  }
}

registerServiceWorker();
void bootstrap();