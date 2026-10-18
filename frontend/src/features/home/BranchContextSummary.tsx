import { Button, Card, Typography } from "antd";
import type { CurrentUser } from "../../shared/api/contracts";
import { getBranchContextView } from "./branch-context-view";
import "./home.css";

export interface BranchContextSummaryProps {
  user: CurrentUser;
  mode?: "header" | "home";
  onRefresh?: () => void;
}

export function BranchContextSummary({
  user,
  mode = "home",
  onRefresh,
}: BranchContextSummaryProps) {
  const view = getBranchContextView(user);

  if (mode === "header") {
    const label =
      view.kind === "active-branch"
        ? `${view.headline} · Código: ${view.code}`
        : view.headline;
    return (
      <span
        aria-label={label}
        className="cg-context-header"
        role="group"
        title={label}
      >
        {label}
      </span>
    );
  }

  return (
    <Card
      className="cg-context-card"
      classNames={{
        header: "cg-context-card-header",
        body: "cg-home-card-body",
      }}
      title={
        <div className="cg-context-card-heading">
          <Typography.Title level={2}>Contexto operativo</Typography.Title>
          {onRefresh ? (
            <Button onClick={onRefresh}>Actualizar contexto</Button>
          ) : null}
        </div>
      }
    >
      <Typography.Paragraph className="cg-context-headline" strong>
        {view.headline}
      </Typography.Paragraph>
      {view.kind === "active-branch" ? (
        <Typography.Paragraph>
          Código: <strong>{view.code}</strong> · Zona horaria de negocio:{" "}
          {view.timezone}
        </Typography.Paragraph>
      ) : (
        <Typography.Paragraph>{view.description}</Typography.Paragraph>
      )}
      {view.availableNames.length > 0 && view.kind !== "active-branch" ? (
        <Typography.Paragraph>
          Sucursales disponibles: {view.availableNames.join(", ")}
        </Typography.Paragraph>
      ) : null}
    </Card>
  );
}
