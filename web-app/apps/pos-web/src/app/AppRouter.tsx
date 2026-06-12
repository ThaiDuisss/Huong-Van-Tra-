import { appRoutes } from "../routes";

export function AppRouter() {
  const SalePage = appRoutes.sales.component;

  return <SalePage />;
}
