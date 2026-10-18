import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { Typography } from "antd";
import { AppLayout } from "../../src/app/layouts/AppLayout";
import { AccountMenu } from "../../src/features/account/AccountMenu";
import { BranchContextSummary } from "../../src/features/home/BranchContextSummary";
import { ThemeProvider } from "../../src/shared/theme/ThemeProvider";
import { responsiveIdentity } from "./responsive-identity";
import "../../src/styles.css";

function VisualExample() {
  return (
    <AppLayout
      accountSlot={
        <AccountMenu onLogout={() => undefined} user={responsiveIdentity} />
      }
      contextSlot={
        <BranchContextSummary mode="header" user={responsiveIdentity} />
      }
    >
      <Typography.Title id="main-heading" level={1}>
        Inicio
      </Typography.Title>
      <Typography.Paragraph>
        Vista sintética para revisar el layout y sus temas.
      </Typography.Paragraph>
      <BranchContextSummary
        onRefresh={() => undefined}
        user={responsiveIdentity}
      />
    </AppLayout>
  );
}

const host = document.getElementById("visual-root");
if (!host) throw new Error("Falta el contenedor de la prueba visual.");

createRoot(host).render(
  <StrictMode>
    <MemoryRouter initialEntries={["/"]}>
      <ThemeProvider>
        <VisualExample />
      </ThemeProvider>
    </MemoryRouter>
  </StrictMode>,
);
