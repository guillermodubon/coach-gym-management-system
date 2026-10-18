import type { PropsWithChildren } from "react";
import { Card, Layout, Typography } from "antd";
import { ThemeToggle } from "../../shared/theme/ThemeToggle";
import "./identity-layout.css";

export function IdentityLayout({ children }: PropsWithChildren) {
  return (
    <Layout className="cg-identity-layout">
      <header className="cg-identity-header">
        <Typography.Text className="cg-brand" strong>
          Coach Gym
        </Typography.Text>
        <ThemeToggle />
      </header>
      <main className="cg-identity-main" id="main-content" tabIndex={-1}>
        {children}
      </main>
      <footer className="cg-identity-footer">
        <Typography.Text type="secondary">Gestión de Coach Gym</Typography.Text>
      </footer>
    </Layout>
  );
}

export function IdentityCard({ children }: PropsWithChildren) {
  return (
    <Card
      className="cg-identity-card"
      classNames={{ body: "cg-identity-card-body" }}
      variant="outlined"
    >
      {children}
    </Card>
  );
}
