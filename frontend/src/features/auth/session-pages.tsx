import { useEffect } from "react";
import { Button, Typography } from "antd";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { StatePanel } from "../../shared/ui/StatePanel";
import { authErrorMessage, authNoticeMessage } from "./auth-messages";
import { useAuth } from "./useAuth";
import type { AuthState } from "./auth-model";
import { useRetryDelay } from "./useRetryDelay";

export function CheckingPage() {
  const auth = useAuth();
  const transitionStatus = auth.branchContextTransition.status;
  const isContextTransition =
    transitionStatus === "submitting" || transitionStatus === "reconciling";
  const title =
    transitionStatus === "submitting"
      ? "Cambiando la sucursal operativa"
      : transitionStatus === "reconciling"
        ? "Confirmando la sucursal operativa"
        : "Comprobando la sesión";
  const description =
    transitionStatus === "submitting"
      ? "Guardamos la preferencia y comprobamos la sesión antes de mostrar información de la sucursal."
      : transitionStatus === "reconciling"
        ? "Consultamos el contexto efectivo del servidor antes de continuar."
        : "Espera mientras verificamos el acceso con el servicio.";

  useEffect(() => {
    if (!isContextTransition) return;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById("main-heading")?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [isContextTransition]);

  return (
    <IdentityLayout>
      <IdentityCard>
        <StatePanel
          headingId="main-heading"
          headingLevel={1}
          headingTabIndex={-1}
          variant="loading"
          title={title}
          description={description}
        />
      </IdentityCard>
    </IdentityLayout>
  );
}

function restrictionMessage(state: Extract<AuthState, { kind: "restricted" }>) {
  switch (state.reason) {
    case "unsupported-authority":
      return "La cuenta usa un tipo de acceso que esta aplicación no reconoce.";
    case "branch-context":
      return "No se pudo confirmar el contexto operativo de la cuenta.";
    case "forbidden":
      return "El servidor no autorizó esta sesión.";
    case "inconsistent-context":
      return "Los datos de acceso de la cuenta no son coherentes.";
  }
}

function unavailableMessage(
  state: Extract<AuthState, { kind: "unavailable" }>,
) {
  switch (state.reason) {
    case "connection":
      return "No se pudo comprobar la sesión. Comprueba la conexión e inténtalo de nuevo.";
    case "after-login":
      return "El inicio de sesión no pudo confirmarse. Comprueba la sesión antes de volver a intentarlo.";
    case "logout-unknown":
      return "No pudimos confirmar el cierre de sesión. Comprueba el estado o vuelve a intentar cerrarla.";
    case "password-unknown":
      return "No se pudo confirmar el cambio de contraseña.";
    case "branch-context-unknown":
      return "No se pudo confirmar el contexto de sucursal. Compruébalo antes de continuar.";
    case "invalid-response":
      return "El servicio devolvió información que no se pudo validar.";
  }
}

export function SessionIssuePage() {
  const auth = useAuth();
  const state = auth.state;
  const retryDelay = useRetryDelay(auth.error);
  const isLogoutUncertain =
    state.kind === "unavailable" && state.reason === "logout-unknown";
  const isBranchContextUncertain =
    state.kind === "unavailable" && state.reason === "branch-context-unknown";
  const title =
    state.kind === "restricted" ? "Acceso restringido" : "Sesión no disponible";
  const description = auth.notice
    ? authNoticeMessage(auth.notice)
    : state.kind === "restricted"
      ? restrictionMessage(state)
      : state.kind === "unavailable"
        ? unavailableMessage(state)
        : undefined;

  useEffect(() => {
    if (!isBranchContextUncertain) return;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById("main-heading")?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [isBranchContextUncertain]);

  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          {title}
        </Typography.Title>
        <StatePanel
          variant="restricted"
          title={description ?? title}
          {...(auth.error
            ? {
                description: `${authErrorMessage(auth.error)}${retryDelay > 0 ? ` Espera ${retryDelay} segundos para volver a intentarlo.` : ""}`,
              }
            : {})}
          action={
            <div className="cg-session-actions">
              <Button
                disabled={retryDelay > 0 || auth.privacyCovered}
                loading={auth.pendingAction === "branch-context"}
                onClick={() =>
                  void (isBranchContextUncertain
                    ? auth.reconcileBranchContext()
                    : auth.revalidate())
                }
              >
                {isBranchContextUncertain
                  ? "Comprobar contexto"
                  : "Comprobar sesión"}
              </Button>
              {state.kind !== "restricted" || state.sessionMayExist ? (
                <Button
                  disabled={retryDelay > 0 || auth.pendingAction !== null}
                  loading={auth.pendingAction === "logout"}
                  onClick={() => void auth.logout()}
                  type={isLogoutUncertain ? "primary" : "default"}
                >
                  {isLogoutUncertain
                    ? "Reintentar cierre de sesión"
                    : "Cerrar sesión"}
                </Button>
              ) : null}
            </div>
          }
        />
      </IdentityCard>
    </IdentityLayout>
  );
}

export function NotFoundPage() {
  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Página no encontrada
        </Typography.Title>
        <Typography.Paragraph>
          La dirección solicitada no corresponde a una página disponible.
        </Typography.Paragraph>
      </IdentityCard>
    </IdentityLayout>
  );
}
