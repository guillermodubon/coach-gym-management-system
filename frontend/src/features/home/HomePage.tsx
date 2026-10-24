import { Card, Typography } from "antd";
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
    <div className="cg-home-page">
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
    </div>
  );
}
