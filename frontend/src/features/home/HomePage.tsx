import { Card, Typography } from "antd";
import { AppLayout } from "../../app/layouts/AppLayout";
import { AccountMenu } from "../account/AccountMenu";
import { useAuth } from "../auth/useAuth";
import { getAccountDisplayName } from "../account/account-labels";
import { BranchContextSummary } from "./BranchContextSummary";
import "./home.css";

export function HomePage() {
  const auth = useAuth();
  if (auth.state.kind !== "authenticated") return null;
  const { user } = auth.state;
  const name = getAccountDisplayName(user);

  return (
    <AppLayout
      accountSlot={
        <AccountMenu onLogout={() => void auth.logout()} user={user} />
      }
      contextSlot={<BranchContextSummary mode="header" user={user} />}
    >
      <Typography.Title id="main-heading" level={1} tabIndex={-1}>
        Inicio
      </Typography.Title>
      <Card
        className="cg-home-welcome"
        classNames={{ body: "cg-home-card-body" }}
      >
        <Typography.Paragraph>
          Bienvenido, <strong>{name}</strong>. Has iniciado sesión en Coach Gym.
        </Typography.Paragraph>
      </Card>
      <BranchContextSummary
        onRefresh={() => void auth.revalidate()}
        user={user}
      />
    </AppLayout>
  );
}
