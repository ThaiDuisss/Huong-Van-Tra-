import { NetworkStatusBanner } from "./app/network/NetworkStatusBanner";
import { AppRouter } from "./app/AppRouter";
import { AppProviders } from "./app/providers";
import "./style/index.css";

function App() {
  return (
    <AppProviders>
      <NetworkStatusBanner />
      <AppRouter />
    </AppProviders>
  );
}

export default App;
