import { useEffect, useState } from "react";
import { Alert, Button, Card, Descriptions, Tag, Typography } from "antd";
import { Link, useLocation, useParams } from "react-router";
import type {
  BranchRecord,
  BranchStatus,
} from "../../shared/api/organization-contracts";
import { StatePanel } from "../../shared/ui/StatePanel";
import { useAuth } from "../auth/useAuth";
import { authErrorMessage } from "../auth/auth-messages";
import { canManageOrganizationBranches } from "./branch-editor-policy";
import { BranchStatusDialog } from "./BranchStatusDialog";
import { useBranchStatus } from "./BranchStatusProvider";
import {
  isBranchId,
  organizationReadError,
  useBranchDetailQuery,
} from "./organization-read";
import "./organization-pages.css";

function safeReturnTo(value: unknown): string {
  if (typeof value !== "string") return "/sucursales";
  try {
    const target = new URL(value, window.location.origin);
    if (
      target.origin === window.location.origin &&
      target.pathname === "/sucursales" &&
      !target.hash
    ) {
      return `${target.pathname}${target.search}`;
    }
  } catch {
    // Invalid navigation state falls back to the catalog.
  }
  return "/sucursales";
}

function optional(value: string | null | undefined): string | undefined {
  return value?.trim() || undefined;
}

export function BranchDetailPage() {
  const auth = useAuth();
  const canManage = canManageOrganizationBranches(auth);
  const branchStatus = useBranchStatus();
  const { branchId } = useParams();
  const location = useLocation();
  const validId = isBranchId(branchId);
  const query = useBranchDetailQuery(validId ? branchId : "");
  const actorId = auth.state.kind === "authenticated" ? auth.state.user.id : "";
  const activeBranchId =
    auth.state.kind === "authenticated"
      ? (auth.state.user.activeBranch?.id ?? null)
      : null;
  const [statusDialogOpen, setStatusDialogOpen] = useState(false);
  const [statusTarget, setStatusTarget] = useState<BranchStatus | null>(null);
  const [statusBranch, setStatusBranch] = useState<BranchRecord | null>(null);
  const [loadingFreshVersion, setLoadingFreshVersion] = useState(false);
  const returnTo = safeReturnTo(
    (location.state as { returnTo?: unknown } | null)?.returnTo,
  );

  useEffect(() => {
    setStatusDialogOpen(false);
    setStatusTarget(null);
    setStatusBranch(null);
  }, [actorId, branchId]);

  if (!validId) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Sucursal
        </Typography.Title>
        <StatePanel
          action={
            <Link className="cg-organization-link" to="/sucursales">
              Volver al catálogo
            </Link>
          }
          description="El identificador de la sucursal no tiene un formato válido."
          title="Enlace de sucursal no válido"
          variant="error"
        />
      </section>
    );
  }

  if (query.isPending) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Sucursal
        </Typography.Title>
        <StatePanel
          description="Consultando la ficha de sucursal."
          title="Cargando sucursal"
          variant="loading"
        />
      </section>
    );
  }

  if (query.isError || !query.data) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Sucursal
        </Typography.Title>
        <StatePanel
          action={
            <div className="cg-branch-detail-actions">
              <Button onClick={() => void query.refetch()}>Reintentar</Button>
              <Link className="cg-organization-link" to={returnTo}>
                Volver al catálogo
              </Link>
            </div>
          }
          description={organizationReadError(query.error)}
          title="No se pudo consultar la sucursal"
          variant="error"
        />
      </section>
    );
  }

  const branch = query.data;
  const statusFeedback = branchStatus.feedbackFor(branch.id);
  const statusBusy =
    statusFeedback?.phase === "submitting" ||
    statusFeedback?.phase === "reconciling";
  const statusLocked =
    statusBusy ||
    statusFeedback?.phase === "uncertain" ||
    statusFeedback?.phase === "conflict";

  async function openStatusDialog(targetStatus: BranchStatus) {
    if (!canManage || statusLocked || loadingFreshVersion) return;
    if (statusFeedback) branchStatus.clearFeedback(branch.id);
    setLoadingFreshVersion(true);
    try {
      const latest = await query.refetch();
      if (
        latest.isSuccess &&
        latest.data?.id === branch.id &&
        ((targetStatus === "ACTIVE" && latest.data.status === "INACTIVE") ||
          (targetStatus === "INACTIVE" && latest.data.status === "ACTIVE"))
      ) {
        setStatusBranch(latest.data);
        setStatusTarget(targetStatus);
        setStatusDialogOpen(true);
      }
    } finally {
      setLoadingFreshVersion(false);
    }
  }

  async function inspectConflict() {
    const latest = await query.refetch();
    if (latest.isSuccess && latest.data?.id === branch.id) {
      branchStatus.clearFeedback(branch.id);
    }
  }

  function closeStatusDialog() {
    setStatusDialogOpen(false);
    setStatusTarget(null);
    setStatusBranch(null);
  }
  const address = [
    branch.addressLine1,
    branch.addressLine2,
    branch.city,
    branch.stateOrDepartment,
    branch.postalCode,
    branch.countryCode,
  ]
    .map(optional)
    .filter(Boolean)
    .join(", ");
  const items = [
    { key: "code", label: "Código", children: branch.code },
    {
      key: "status",
      label: "Estado",
      children: (
        <Tag className="cg-organization-status" color="default">
          {branch.status === "ACTIVE" ? "Activa" : "Inactiva"}
        </Tag>
      ),
    },
    ...(address
      ? [{ key: "address", label: "Dirección", children: address }]
      : []),
    ...(optional(branch.phone)
      ? [{ key: "phone", label: "Teléfono", children: branch.phone }]
      : []),
    ...(optional(branch.email)
      ? [{ key: "email", label: "Correo", children: branch.email }]
      : []),
    { key: "timezone", label: "Zona horaria", children: branch.timezone },
    {
      key: "initial",
      label: "Sucursal inicial",
      children: branch.initialBranch ? "Sí" : "No",
    },
    {
      key: "updatedAt",
      label: "Última actualización",
      children: new Intl.DateTimeFormat("es-SV", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(branch.updatedAt)),
    },
  ];

  return (
    <section aria-labelledby="main-heading" className="cg-organization-page">
      <div className="cg-organization-heading">
        <div>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            {branch.name}
          </Typography.Title>
          <Typography.Paragraph>
            Ficha de consulta · Código {branch.code}
          </Typography.Paragraph>
        </div>
        <div className="cg-organization-heading-actions">
          {canManage ? (
            <Button
              disabled={statusLocked || loadingFreshVersion}
              loading={loadingFreshVersion || statusBusy}
              onClick={() =>
                void openStatusDialog(
                  branch.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
                )
              }
            >
              {branch.status === "ACTIVE"
                ? "Desactivar sucursal"
                : "Activar sucursal"}
            </Button>
          ) : null}
          {canManage ? (
            <Link
              className="cg-organization-link cg-organization-link--primary"
              state={{ returnTo }}
              to={`/sucursales/${branch.id}/editar`}
            >
              Editar sucursal
            </Link>
          ) : null}
          <Link className="cg-organization-link" to={returnTo}>
            Volver al catálogo
          </Link>
        </div>
      </div>
      {branch.status === "INACTIVE" ? (
        <div className="cg-branch-detail-notice" role="status">
          Esta sucursal está inactiva. Su ficha puede consultarse, pero no está
          disponible para seleccionarse como contexto operativo.
        </div>
      ) : null}
      {statusFeedback && statusFeedback.phase !== "submitting" ? (
        <Alert
          action={
            statusFeedback.phase === "uncertain" ? (
              <Button
                disabled={statusBusy}
                onClick={() => void branchStatus.inspectUncertain(branch.id)}
              >
                Comprobar estado
              </Button>
            ) : statusFeedback.phase === "conflict" ? (
              <Button
                disabled={query.isFetching}
                onClick={() => void inspectConflict()}
              >
                Actualizar ficha
              </Button>
            ) : (
              <Button onClick={() => branchStatus.clearFeedback(branch.id)}>
                Cerrar aviso
              </Button>
            )
          }
          className="cg-branch-status-feedback"
          description={
            statusFeedback.phase === "uncertain" ||
            statusFeedback.phase === "conflict" ||
            statusFeedback.phase === "blocked"
              ? statusFeedback.message
              : statusFeedback.error
                ? authErrorMessage(statusFeedback.error)
                : statusFeedback.message
          }
          showIcon
          title={
            statusFeedback.phase === "uncertain"
              ? "Resultado pendiente de comprobar"
              : statusFeedback.phase === "conflict"
                ? "La sucursal cambió"
                : statusFeedback.phase === "success"
                  ? "Estado actualizado"
                  : statusFeedback.phase === "blocked"
                    ? "Operación bloqueada"
                    : "No se pudo cambiar el estado"
          }
          type={
            statusFeedback.phase === "success"
              ? "success"
              : statusFeedback.phase === "uncertain" ||
                  statusFeedback.phase === "conflict" ||
                  statusFeedback.phase === "blocked"
                ? "warning"
                : "error"
          }
        />
      ) : null}
      <Card className="cg-organization-card" variant="outlined">
        <Descriptions
          column={{ xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 3 }}
          items={items}
          title="Información de sucursal"
        />
      </Card>
      <p className="cg-branch-detail-footnote">
        Consultar esta ficha no concede permisos para operar en la sucursal. Las
        operaciones dependen del alcance autorizado en la sesión.
      </p>
      <BranchStatusDialog
        actorId={actorId}
        activeBranchId={activeBranchId}
        branch={statusBranch}
        feedback={branchStatus.feedbackFor(branch.id)}
        onCancel={closeStatusDialog}
        onConfirm={(reason) =>
          statusBranch && statusTarget
            ? branchStatus.changeStatus(statusBranch, statusTarget, reason)
            : Promise.resolve(false)
        }
        open={statusDialogOpen}
        targetStatus={statusTarget}
      />
    </section>
  );
}
