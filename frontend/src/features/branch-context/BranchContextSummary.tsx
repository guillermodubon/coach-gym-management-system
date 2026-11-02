import { Button, Card, Typography } from "antd";
import type { ReactNode } from "react";
import type { CurrentUser } from "../../shared/api/contracts";
import { getBranchContextView } from "./branch-context-view";
import "./branch-context.css";

export interface BranchContextSummaryProps {
  user: CurrentUser;
  selector?: ReactNode;
  onRefresh?: () => void;
}

export function BranchContextSummary({
  user,
  selector,
  onRefresh,
}: BranchContextSummaryProps) {
  const view = getBranchContextView(user);

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
          <div className="cg-context-card-actions">
            {selector}
            {onRefresh ? (
              <Button onClick={onRefresh}>Actualizar contexto</Button>
            ) : null}
          </div>
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
        <Typography.Paragraph className="cg-context-available-branches">
          Sucursales disponibles: {view.availableNames.join(", ")}
        </Typography.Paragraph>
      ) : null}
    </Card>
  );
}
