import { useEffect, useState } from "react";
import { Alert, Button, Card, Modal, Typography } from "antd";
import { useBlocker } from "react-router";
import { StatePanel } from "../../shared/ui/StatePanel";
import { useAuth } from "../auth/useAuth";
import { useAccountProfile } from "./AccountProfileProvider";
import { ProfileForm } from "./ProfileForm";
import { useProfilePhoto } from "./ProfilePhotoProvider";
import "./profile.css";

export function ProfilePage() {
  const auth = useAuth();
  const account = useAccountProfile();
  const photo = useProfilePhoto();
  const [confirmReload, setConfirmReload] = useState(false);
  const shouldBlock =
    (account.dirty || photo.dirty) &&
    auth.state.kind === "authenticated" &&
    !auth.privacyCovered;
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      shouldBlock &&
      currentLocation.pathname === "/mi-perfil" &&
      nextLocation.pathname !== currentLocation.pathname,
  );

  useEffect(() => {
    if (
      blocker.state === "blocked" &&
      (auth.state.kind !== "authenticated" || auth.privacyCovered)
    ) {
      blocker.proceed();
    }
  }, [auth.privacyCovered, auth.state.kind, blocker]);

  useEffect(() => {
    if (!shouldBlock) return;
    const preventUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", preventUnload);
    return () => window.removeEventListener("beforeunload", preventUnload);
  }, [shouldBlock]);

  if (account.loading) {
    return (
      <div className="cg-profile-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Mi perfil
        </Typography.Title>
        <StatePanel
          variant="loading"
          title="Cargando perfil"
          description="Estamos consultando la información de tu cuenta."
        />
      </div>
    );
  }

  if (!account.profile) {
    return (
      <div className="cg-profile-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Mi perfil
        </Typography.Title>
        <StatePanel
          variant="error"
          title="No se pudo cargar tu perfil"
          description={
            account.readError?.message ??
            "Comprueba tu sesión e inténtalo de nuevo."
          }
          action={
            <Button onClick={() => void account.reload()}>Reintentar</Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="cg-profile-page">
      <div className="cg-profile-heading">
        <div>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Mi perfil
          </Typography.Title>
          <Typography.Paragraph>
            Consulta los datos de tu cuenta y actualiza tu nombre.
          </Typography.Paragraph>
        </div>
        {!account.dirty && !photo.dirty ? (
          <Button
            disabled={account.saving}
            onClick={() => void account.reload()}
          >
            Actualizar datos
          </Button>
        ) : null}
      </div>

      {account.readError ? (
        <Alert
          showIcon
          title="No se pudieron actualizar los datos del perfil."
          description={account.readError.message}
          type="warning"
        />
      ) : null}

      <Card
        className="cg-profile-card"
        classNames={{ body: "cg-profile-card-body" }}
        variant="outlined"
      >
        <ProfileForm onRequestReload={() => setConfirmReload(true)} />
      </Card>

      <Modal
        cancelText="Seguir editando"
        okButtonProps={{ danger: true }}
        okText="Descartar y salir"
        onCancel={() => {
          if (blocker.state === "blocked") blocker.reset();
        }}
        onOk={() => {
          account.discard();
          photo.discardSelection();
          if (blocker.state === "blocked") blocker.proceed();
        }}
        open={blocker.state === "blocked"}
        title="Tienes cambios sin guardar"
      >
        <Typography.Paragraph>
          Si sales ahora, se descartarán los cambios que todavía no has
          guardado.
        </Typography.Paragraph>
      </Modal>

      <Modal
        cancelText="Conservar mis cambios"
        okButtonProps={{ danger: true }}
        okText="Descartar y recargar"
        onCancel={() => setConfirmReload(false)}
        onOk={async () => {
          await account.reload();
          setConfirmReload(false);
        }}
        open={confirmReload}
        title="Recargar el perfil"
      >
        <Typography.Paragraph>
          Se descartarán los nombres editados y se consultará la versión actual
          del servidor.
        </Typography.Paragraph>
      </Modal>
    </div>
  );
}
