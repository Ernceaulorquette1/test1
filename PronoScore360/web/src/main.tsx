import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "bootstrap/dist/css/bootstrap.min.css";
import "./styles/brand.css";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { BasketProvider } from "./components/Basket";
import { ThemeProvider } from "./theme/ThemeContext";

const qc = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false } } });

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={qc}>
      <ThemeProvider><BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><AuthProvider><BasketProvider><App /></BasketProvider></AuthProvider></BrowserRouter></ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
