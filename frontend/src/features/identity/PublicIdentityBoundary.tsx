import type { PropsWithChildren } from "react";
import { Alert, Button } from "antd";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { useAuth } from "../auth/useAuth";

export function PublicIdentityBoundary({ children }: PropsWithChildren) {
  const auth = useAuth();

  if (
    auth.state.kind === "authenticated" ||
    auth.state.kind === "password-required"
  ) {
    const logoutPending = auth.pendingAction === "logout";
    return (
      <IdentityLayout>
        <IdentityCard>
          <Alert
            showIcon
            title="Cierra la sesión para continuar"
            description="Este enlace solo se procesa sin una sesión activa. Al cerrar la sesión aquí, el enlace permanece disponible en esta página."
            type="warning"
          />
          <Button
            block
            className="cg-public-identity-logout"
            disabled={auth.pendingAction !== null}
            loading={logoutPending}
            onClick={() => void auth.logout()}
            type="primary"
          >
            Cerrar sesión y continuar
          </Button>
        </IdentityCard>
      </IdentityLayout>
    );
  }

  return children;
}
