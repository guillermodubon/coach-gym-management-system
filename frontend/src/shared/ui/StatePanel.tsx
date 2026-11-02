import type { ReactNode } from "react";
import { Alert, Card, Typography } from "antd";
import "./state-panel.css";

export type StatePanelVariant = "loading" | "error" | "restricted" | "empty";

export interface StatePanelProps {
  variant: StatePanelVariant;
  title: string;
  description?: string;
  action?: ReactNode;
  headingId?: string;
  headingLevel?: 1 | 2 | 3 | 4 | 5;
  headingTabIndex?: number;
}

export function StatePanel({
  variant,
  title,
  description,
  action,
  headingId,
  headingLevel = 2,
  headingTabIndex,
}: StatePanelProps) {
  if (variant === "error") {
    return (
      <Alert
        action={action}
        className="cg-state-panel cg-feedback"
        classNames={{
          section: "cg-feedback-content",
          actions: "cg-feedback-actions",
        }}
        description={description}
        role="alert"
        showIcon
        title={title}
        type="error"
      />
    );
  }

  return (
    <Card
      className="cg-state-panel"
      classNames={{ body: "cg-state-panel-body" }}
      variant="outlined"
    >
      <div className="cg-state-panel-stack">
        <Typography.Title
          {...(headingId === undefined ? {} : { id: headingId })}
          level={headingLevel}
          {...(headingTabIndex === undefined
            ? {}
            : { tabIndex: headingTabIndex })}
        >
          {title}
        </Typography.Title>
        {description ? (
          <Typography.Paragraph>{description}</Typography.Paragraph>
        ) : null}
        {variant === "loading" ? <span role="status">Cargando…</span> : null}
        {action}
      </div>
    </Card>
  );
}
