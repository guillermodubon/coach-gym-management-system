import { Typography } from "antd";
import type { StaffInvitationPreview } from "../../shared/api/public-identity-contracts";

export type InvitationPreviewView = Omit<
  StaffInvitationPreview,
  "invitationId" | "proposedBranches"
> & {
  proposedBranches: Array<
    Pick<StaffInvitationPreview["proposedBranches"][number], "code" | "name">
  >;
};

function roleLabel(role: InvitationPreviewView["proposedRole"]): string {
  return role === "ADMIN" ? "Administrador" : "Recepcionista";
}

function scopeLabel(scope: InvitationPreviewView["proposedScope"]): string {
  return scope === "ORGANIZATION"
    ? "Toda la organización"
    : "Sucursales específicas";
}

function expiryLabel(value: string): string {
  return new Intl.DateTimeFormat("es-SV", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function invitationPreviewView(
  preview: StaffInvitationPreview,
): InvitationPreviewView {
  return {
    maskedEmail: preview.maskedEmail,
    proposedRole: preview.proposedRole,
    proposedScope: preview.proposedScope,
    proposedBranches: preview.proposedBranches.map(({ code, name }) => ({
      code,
      name,
    })),
    expiresAt: preview.expiresAt,
  };
}

export function InvitationProposalSummary({
  preview,
}: {
  preview: InvitationPreviewView;
}) {
  return (
    <section
      aria-label="Datos de la invitación"
      className="cg-invitation-summary"
    >
      <dl className="cg-invitation-summary-list">
        <div>
          <dt>Correo de la invitación</dt>
          <dd>{preview.maskedEmail}</dd>
        </div>
        <div>
          <dt>Rol propuesto</dt>
          <dd>{roleLabel(preview.proposedRole)}</dd>
        </div>
        <div>
          <dt>Alcance</dt>
          <dd>{scopeLabel(preview.proposedScope)}</dd>
        </div>
        <div>
          <dt>Vence</dt>
          <dd>
            <time dateTime={preview.expiresAt}>
              {expiryLabel(preview.expiresAt)}
            </time>
          </dd>
        </div>
      </dl>
      {preview.proposedBranches.length > 0 ? (
        <div className="cg-invitation-branches">
          <Typography.Text strong>Sucursales incluidas</Typography.Text>
          <ul>
            {preview.proposedBranches.map(({ code, name }) => (
              <li key={`${code}:${name}`}>
                <span>{name}</span>
                <Typography.Text type="secondary">{code}</Typography.Text>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <Typography.Paragraph className="cg-invitation-organization-note">
          El acceso propuesto corresponde a toda la organización.
        </Typography.Paragraph>
      )}
    </section>
  );
}
