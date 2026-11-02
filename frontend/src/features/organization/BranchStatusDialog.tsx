import { useEffect, useState } from "react";
import { Alert, Input, Modal, Typography } from "antd";
import type {
  BranchRecord,
  BranchStatus,
} from "../../shared/api/organization-contracts";
import { authErrorMessage } from "../auth/auth-messages";
import type { BranchStatusFeedback } from "./BranchStatusProvider";

interface BranchStatusDialogProps {
  actorId: string;
  activeBranchId: string | null;
  branch: BranchRecord | null;
  feedback: BranchStatusFeedback | null;
  open: boolean;
  targetStatus: BranchStatus | null;
  onCancel(): void;
  onConfirm(reason: string): Promise<boolean>;
}

export function BranchStatusDialog({
  actorId,
  activeBranchId,
  branch,
  feedback,
  open,
  targetStatus,
  onCancel,
  onConfirm,
}: BranchStatusDialogProps) {
  const [reason, setReason] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const isActivation = targetStatus === "ACTIVE";
  const title = isActivation ? "Activar sucursal" : "Desactivar sucursal";
  const action = isActivation ? "activación" : "desactivación";

  useEffect(() => {
    setReason("");
    setValidationError(null);
  }, [actorId, branch?.id, targetStatus]);

  useEffect(() => {
    if (open) return;
    setReason("");
    setValidationError(null);
    setSubmitting(false);
  }, [open]);

  async function confirm() {
    const normalized = reason.trim();
    if (!normalized) {
      setValidationError("Escribe un motivo para continuar.");
      return;
    }
    if (normalized.length > 1000) {
      setValidationError("El motivo admite hasta 1000 caracteres.");
      return;
    }
    if (!branch || !targetStatus || submitting) return;

    setSubmitting(true);
    setValidationError(null);
    try {
      const completed = await onConfirm(normalized);
      if (completed) onCancel();
    } finally {
      setSubmitting(false);
    }
  }

  if (!branch || !targetStatus) return null;

  const currentBranchIsActive = activeBranchId === branch.id;
  const blockedMessage =
    feedback?.phase === "blocked" && feedback.operation?.branchId === branch.id
      ? feedback.message
      : null;

  return (
    <Modal
      cancelText="Cancelar"
      closable={!submitting}
      confirmLoading={submitting}
      destroyOnHidden
      okButtonProps={{ disabled: submitting }}
      okText={isActivation ? "Confirmar activación" : "Confirmar desactivación"}
      onCancel={onCancel}
      onOk={() => void confirm()}
      open={open}
      title={`${title}: ${branch.name}`}
    >
      <div className="cg-branch-status-dialog">
        <Typography.Paragraph>
          Confirma la {action} de <strong>{branch.name}</strong> · código{" "}
          <strong>{branch.code}</strong>. El cambio se enviará una sola vez con
          la versión actual de la ficha.
        </Typography.Paragraph>
        {isActivation ? (
          <Alert
            description="Al activarla, podrá aparecer entre las sucursales disponibles conforme a las asignaciones autorizadas por el servidor."
            showIcon
            type="info"
          />
        ) : (
          <>
            {currentBranchIsActive ? (
              <Alert
                className="cg-branch-status-dialog__notice"
                description="Esta es tu sucursal operativa. Al confirmar, se volverá a consultar la sesión; el servidor indicará si queda sin selección, usa una única sucursal restante o restringe el contexto."
                showIcon
                type="warning"
              />
            ) : null}
            {branch.initialBranch ? (
              <Alert
                className="cg-branch-status-dialog__notice"
                description="La sucursal inicial no se protege mediante una regla del navegador. El servidor rechazará la desactivación si sus invariantes actuales no permiten el cambio."
                showIcon
                type="info"
              />
            ) : null}
          </>
        )}
        <label
          className="cg-branch-status-dialog__reason"
          htmlFor="cg-branch-status-reason"
        >
          Motivo requerido
        </label>
        <Input.TextArea
          aria-describedby={
            validationError ? "cg-branch-status-reason-error" : undefined
          }
          aria-invalid={validationError ? "true" : undefined}
          autoSize={{ minRows: 3, maxRows: 6 }}
          disabled={submitting}
          id="cg-branch-status-reason"
          maxLength={1000}
          onChange={(event) => {
            setReason(event.target.value);
            setValidationError(null);
          }}
          placeholder="Describe brevemente por qué se cambia el estado."
          showCount
          value={reason}
        />
        {validationError ? (
          <Typography.Text
            className="cg-branch-status-dialog__error"
            id="cg-branch-status-reason-error"
            role="alert"
            type="danger"
          >
            {validationError}
          </Typography.Text>
        ) : null}
        {blockedMessage ? (
          <Alert
            className="cg-branch-status-dialog__notice"
            description={blockedMessage}
            showIcon
            type="warning"
          />
        ) : null}
        {feedback?.phase === "error" && feedback.error ? (
          <Alert
            className="cg-branch-status-dialog__notice"
            description={authErrorMessage(feedback.error)}
            showIcon
            type="error"
          />
        ) : null}
      </div>
    </Modal>
  );
}
