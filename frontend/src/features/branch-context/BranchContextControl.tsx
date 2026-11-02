import { useCallback, useMemo } from "react";
import { Button, Select } from "antd";
import { CloseCircleOutlined } from "@ant-design/icons";
import { authErrorMessage } from "../auth/auth-messages";
import { useAuth } from "../auth/useAuth";
import { useBranchContext } from "./useBranchContext";
import { getBranchContextView } from "./branch-context-view";
import "./branch-context.css";

export type BranchContextControlMode = "header" | "home";

interface BranchContextControlProps {
  mode: BranchContextControlMode;
}

function transitionMessage(
  auth: ReturnType<typeof useAuth>,
): string | undefined {
  const transition = auth.branchContextTransition;
  if (transition.status === "blocked") {
    switch (transition.reason) {
      case "unsaved-changes":
        return "Guarda o descarta los cambios del perfil, la foto o la sucursal antes de cambiar de sucursal.";
      case "write-in-progress":
        return "Espera a que termine la actualización del perfil, la foto o la sucursal.";
      case "context-change-in-progress":
        return "Ya hay un cambio de sucursal en curso.";
    }
  }
  if (transition.status === "failed") {
    return transition.error
      ? authErrorMessage(transition.error)
      : "No se pudo actualizar la sucursal operativa. Comprueba el contexto.";
  }
  if (transition.status !== "idle") return undefined;

  switch (transition.notice) {
    case "context-confirmed":
      return "La sucursal operativa se actualizó.";
    case "context-unchanged":
      return "Esta sucursal ya está activa.";
    case "effective-context-differs":
      return "El servidor confirmó otra sucursal operativa y se actualizó la selección.";
    case "context-reconciled":
      return "Se muestra la sucursal operativa confirmada por el servidor.";
    default:
      return undefined;
  }
}

function focusHeaderSelector(): void {
  const focus = () => {
    document
      .querySelector<HTMLElement>(
        '.cg-branch-context-control--header [role="combobox"]',
      )
      ?.focus({ preventScroll: true });
  };

  if (typeof window.requestAnimationFrame === "function") {
    window.requestAnimationFrame(focus);
  } else {
    queueMicrotask(focus);
  }
}

export function BranchContextControl({ mode }: BranchContextControlProps) {
  const auth = useAuth();
  const branchContext = useBranchContext();
  const user = auth.state.kind === "authenticated" ? auth.state.user : null;
  const view = user ? getBranchContextView(user) : null;
  const branches = branchContext.availableBranches;
  const options = useMemo(
    () =>
      branches.map((branch) => ({
        value: branch.id,
        label: `${branch.name} · ${branch.code}`,
      })),
    [branches],
  );

  const feedback = transitionMessage(auth);
  const hasMultipleChoices = branches.length > 1;
  const canClear = Boolean(branchContext.activeBranch && hasMultipleChoices);
  const disabled =
    auth.pendingAction !== null ||
    auth.privacyCovered ||
    auth.branchContextTransition.status === "submitting" ||
    auth.branchContextTransition.status === "reconciling" ||
    (branches.length === 1 && branchContext.activeBranch !== null);

  const chooseBranch = useCallback(
    async (branchId: string) => {
      await branchContext.selectBranch(branchId);
      focusHeaderSelector();
    },
    [branchContext.selectBranch],
  );
  const clearBranch = useCallback(async () => {
    await branchContext.clearBranch();
    focusHeaderSelector();
  }, [branchContext.clearBranch]);

  if (!user || !view) return null;

  const headerSummary =
    !branchContext.activeBranch || branches.length === 0 ? (
      <span
        aria-label={view.headline}
        className="cg-context-header-summary"
        role="group"
        title={view.headline}
      >
        {view.headline}
      </span>
    ) : null;

  return (
    <div
      className={`cg-branch-context-control cg-branch-context-control--${mode}`}
      data-branch-context-control={mode}
    >
      {headerSummary}
      {branches.length > 0 ? (
        <div className="cg-branch-context-actions">
          <label className="cg-branch-context-field">
            {mode === "home" ? (
              <span className="cg-branch-context-label">
                Sucursal operativa
              </span>
            ) : null}
            <Select
              aria-label="Sucursal operativa"
              classNames={{
                popup: { root: "cg-branch-context-dropdown" },
                root: "cg-branch-context-select",
              }}
              disabled={disabled}
              getPopupContainer={() => document.body}
              id={`branch-context-${mode}-select`}
              onChange={(branchId: string) => void chooseBranch(branchId)}
              optionFilterProp="label"
              options={options}
              placeholder={
                view.kind === "organization-with-branches"
                  ? "Sin sucursal activa"
                  : "Selecciona una sucursal"
              }
              popupMatchSelectWidth={320}
              showSearch={branches.length > 4}
              value={branchContext.activeBranch?.id ?? null}
              virtual={false}
            />
          </label>
          {canClear ? (
            <Button
              aria-label="Quitar selección"
              className="cg-branch-context-clear"
              disabled={auth.pendingAction !== null || auth.privacyCovered}
              icon={<CloseCircleOutlined aria-hidden="true" />}
              onClick={() => void clearBranch()}
              title="Quitar la preferencia de sucursal. No cambia tus asignaciones."
            >
              Quitar selección
            </Button>
          ) : null}
        </div>
      ) : null}
      {feedback ? (
        <span
          aria-atomic="true"
          className="cg-branch-context-feedback"
          role="status"
        >
          {feedback}
        </span>
      ) : null}
    </div>
  );
}
