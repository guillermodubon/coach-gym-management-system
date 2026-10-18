import { Button, Typography } from "antd";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { StatePanel } from "../../shared/ui/StatePanel";
import { authErrorMessage, authNoticeMessage } from "./auth-messages";
import { useAuth } from "./useAuth";
import type { AuthState } from "./auth-model";
import { useRetryDelay } from "./useRetryDelay";

export function CheckingPage() {
  return (
    <IdentityLayout>
      <IdentityCard>
        <StatePanel
          variant="loading"
          title="Comprobando la sesión"
          description="Espera mientras verificamos el acceso con el servicio."
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
  const title =
    state.kind === "restricted" ? "Acceso restringido" : "Sesión no disponible";
  const description = auth.notice
    ? authNoticeMessage(auth.notice)
    : state.kind === "restricted"
      ? restrictionMessage(state)
      : state.kind === "unavailable"
        ? unavailableMessage(state)
        : undefined;

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
                loading={auth.privacyCovered}
                onClick={() => void auth.revalidate()}
              >
                Comprobar sesión
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
