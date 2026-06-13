import { AppRouter } from "./app/AppRouter";
import { AppProviders } from "./app/providers";
import "./style/index.css";

function App() {
  return (
    <AppProviders>
      <AppRouter />
    </AppProviders>
  );
}

export default App;
