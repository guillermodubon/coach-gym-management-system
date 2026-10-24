import { Alert, Button, Form, Input, Typography } from "antd";
import type { StaffSelfProfile } from "../../shared/api/profile-contracts";
import { AccountAvatar } from "../account/AccountAvatar";
import { getInitialsForName } from "../account/initials";
import { useAccountProfile } from "./AccountProfileProvider";
import { ProfilePhotoSection } from "./ProfilePhotoSection";
import { useProfilePhoto } from "./ProfilePhotoProvider";
import { validateProfileNames } from "./profile-validation";

const roleLabels: Record<StaffSelfProfile["roles"][number], string> = {
  ADMIN: "Administrador",
  RECEPTIONIST: "Recepción",
};

const statusLabels: Record<StaffSelfProfile["status"], string> = {
  ACTIVE: "Activa",
  INACTIVE: "Inactiva",
  SUSPENDED: "Suspendida",
  DEACTIVATED: "Desactivada",
};

export function ProfileForm({ onRequestReload }: { onRequestReload(): void }) {
  const account = useAccountProfile();
  const photo = useProfilePhoto();
  const { profile, draft } = account;
  if (!profile || !draft) return null;

  const validation = validateProfileNames(draft);
  const firstNameError = validation.errors.firstName;
  const lastNameError = validation.errors.lastName;
  const locked = account.saving || photo.dirty || photo.reconciliationRequired;
  const canSave =
    account.dirty && validation.valid && !account.conflict && !locked;

  return (
    <div className="cg-profile-grid">
      <section aria-labelledby="profile-details-heading">
        <div className="cg-profile-identity">
          <AccountAvatar
            className="cg-profile-avatar"
            initials={getInitialsForName(profile.displayName)}
            photoUrl={photo.previewUrl ?? photo.photoUrl}
            size={56}
          />
          <div className="cg-profile-identity-copy">
            <Typography.Title id="profile-details-heading" level={2}>
              Información personal
            </Typography.Title>
            <Typography.Text type="secondary">
              Actualiza el nombre que aparece en tu cuenta.
            </Typography.Text>
          </div>
        </div>

        <ProfilePhotoSection />

        {account.conflict ? (
          <Alert
            action={
              <Button onClick={onRequestReload} type="link">
                Recargar perfil
              </Button>
            }
            className="cg-profile-alert"
            showIcon
            title="El perfil cambió en otra sesión. Tus cambios siguen aquí."
            type="warning"
          />
        ) : account.writeError ? (
          <Alert
            className="cg-profile-alert"
            showIcon
            title="No se pudo confirmar el cambio. Comprueba el perfil antes de volver a intentarlo."
            description={account.writeError.message}
            type="error"
          />
        ) : null}

        <Form
          autoComplete="off"
          className="cg-profile-form"
          layout="vertical"
          onFinish={() => {
            if (!photo.dirty && !photo.reconciliationRequired)
              void account.save();
          }}
        >
          <Form.Item
            htmlFor="profile-first-name"
            label="Nombre"
            required
            {...(firstNameError
              ? { validateStatus: "error" as const, help: firstNameError }
              : {})}
          >
            <Input
              autoComplete="given-name"
              disabled={locked}
              id="profile-first-name"
              maxLength={100}
              required
              onChange={(event) =>
                account.setName("firstName", event.target.value)
              }
              value={draft.firstName}
              aria-invalid={Boolean(firstNameError)}
            />
          </Form.Item>

          <Form.Item
            htmlFor="profile-last-name"
            label="Apellido"
            required
            {...(lastNameError
              ? { validateStatus: "error" as const, help: lastNameError }
              : {})}
          >
            <Input
              autoComplete="family-name"
              disabled={locked}
              id="profile-last-name"
              maxLength={100}
              required
              onChange={(event) =>
                account.setName("lastName", event.target.value)
              }
              value={draft.lastName}
              aria-invalid={Boolean(lastNameError)}
            />
          </Form.Item>

          {account.dirty ? (
            <div className="cg-profile-form-footer">
              <Typography.Text aria-live="polite" role="status">
                Hay cambios sin guardar.
              </Typography.Text>
              <div className="cg-profile-form-actions">
                <Button disabled={locked} onClick={account.discard}>
                  Descartar cambios
                </Button>
                <Button
                  disabled={!canSave}
                  htmlType="submit"
                  loading={locked}
                  type="primary"
                >
                  Guardar cambios
                </Button>
              </div>
            </div>
          ) : (
            <Typography.Text type="secondary">
              No hay cambios para guardar.
            </Typography.Text>
          )}
        </Form>
      </section>

      <section aria-labelledby="account-readonly-heading">
        <Typography.Title id="account-readonly-heading" level={2}>
          Datos de la cuenta
        </Typography.Title>
        <dl className="cg-profile-readonly">
          <div>
            <dt>Usuario</dt>
            <dd>{profile.username}</dd>
          </div>
          <div>
            <dt>Correo electrónico</dt>
            <dd>{profile.email}</dd>
          </div>
          <div>
            <dt>Rol</dt>
            <dd>{profile.roles.map((role) => roleLabels[role]).join(", ")}</dd>
          </div>
          <div>
            <dt>Estado de la cuenta</dt>
            <dd>{statusLabels[profile.status]}</dd>
          </div>
        </dl>
        <Typography.Paragraph
          className="cg-profile-readonly-note"
          type="secondary"
        >
          El usuario, el correo, el rol y el estado se muestran solo como
          información.
        </Typography.Paragraph>
      </section>
    </div>
  );
}
