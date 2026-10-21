import { NetworkStatusBanner } from "./app/network/NetworkStatusBanner";
import { AppRouter } from "./app/AppRouter";
import { AppProviders } from "./app/providers";
import { OfflineOrderSyncRunner } from "./features/sales/offline/OfflineOrderSyncRunner";
import "./style/index.css";

function App() {
  return (
    <AppProviders>
      <NetworkStatusBanner />
      <OfflineOrderSyncRunner />
      <AppRouter />
    </AppProviders>
  );
}

export default App;
