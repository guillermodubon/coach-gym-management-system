import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Modal, Space, Typography } from "antd";
import {
  Link,
  useBlocker,
  useLocation,
  useNavigate,
  useParams,
} from "react-router";
import { StatePanel } from "../../shared/ui/StatePanel";
import { useAuth } from "../auth/useAuth";
import { useBranchDetailQuery } from "./organization-read";
import { branchCatalogSearch } from "./branch-catalog-query";
import {
  emptyBranchEditorValues,
  validateBranchEditorValues,
} from "./branch-editor-validation";
import type { BranchEditorMode } from "./branch-editor-validation";
import { useBranchEditor } from "./BranchEditorProvider";
import { isBranchId } from "./organization-read";
import { BranchEditorForm } from "./BranchEditorForm";
import "./branch-editor.css";

function safeBranchReturnTo(value: unknown): string {
  if (typeof value !== "string") return "/sucursales";
  try {
    const target = new URL(value, window.location.origin);
    if (
      target.origin !== window.location.origin ||
      target.hash ||
      target.username ||
      target.password
    ) {
      return "/sucursales";
    }
    if (target.pathname === "/sucursales") {
      return `${target.pathname}${target.search}`;
    }
    const detail = target.pathname.match(/^\/sucursales\/([^/]+)$/);
    if (detail && isBranchId(detail[1]) && !target.search) {
      return target.pathname;
    }
  } catch {
    // Invalid navigation state returns to the known local catalog route.
  }
  return "/sucursales";
}

function isBranchEditorRoute(pathname: string): boolean {
  return (
    pathname === "/sucursales/nueva" ||
    /^\/sucursales\/[\da-f-]+\/editar$/i.test(pathname)
  );
}

function loadingPage(title: string, description: string) {
  return (
    <section aria-labelledby="main-heading" className="cg-organization-page">
      <Typography.Title id="main-heading" level={1} tabIndex={-1}>
        {title}
      </Typography.Title>
      <StatePanel
        description={description}
        title="Preparando el formulario"
        variant="loading"
      />
    </section>
  );
}

export function BranchEditorPage({ mode }: { mode: BranchEditorMode }) {
  const auth = useAuth();
  const editor = useBranchEditor();
  const navigate = useNavigate();
  const location = useLocation();
  const params = useParams();
  const branchId = mode === "update" ? params.branchId : undefined;
  const validBranchId = mode === "create" || isBranchId(branchId);
  const branchQuery = useBranchDetailQuery(
    validBranchId && mode === "update" ? branchId! : "",
    editor.authorized && mode === "update" && validBranchId,
  );
  const [showErrors, setShowErrors] = useState(false);
  const [confirmReplace, setConfirmReplace] = useState(false);
  const allowNavigationAfterSave = useRef(false);

  const title = mode === "create" ? "Nueva sucursal" : "Editar sucursal";
  const state = location.state as { returnTo?: unknown } | null;
  const returnTo = safeBranchReturnTo(state?.returnTo);
  const submitLocationKey = useRef<string | null>(null);

  useEffect(() => {
    if (!editor.authorized) return;
    if (mode === "create") {
      editor.beginCreate();
      return;
    }
    if (
      branchQuery.data &&
      branchQuery.data.id === branchId &&
      !branchQuery.isFetching
    ) {
      editor.beginUpdate(branchQuery.data);
    }
  }, [
    branchId,
    branchQuery.data,
    branchQuery.isFetching,
    editor.authorized,
    editor.beginCreate,
    editor.beginUpdate,
    mode,
  ]);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !allowNavigationAfterSave.current &&
      editor.authorized &&
      (editor.dirty || editor.saving) &&
      isBranchEditorRoute(currentLocation.pathname) &&
      (currentLocation.pathname !== nextLocation.pathname ||
        currentLocation.search !== nextLocation.search ||
        currentLocation.hash !== nextLocation.hash),
  );

  useEffect(() => {
    if (
      allowNavigationAfterSave.current &&
      blocker.state === "blocked" &&
      submitLocationKey.current === location.key
    ) {
      allowNavigationAfterSave.current = false;
      submitLocationKey.current = null;
      blocker.proceed();
      return;
    }
    if (
      allowNavigationAfterSave.current &&
      submitLocationKey.current !== null &&
      submitLocationKey.current !== location.key
    ) {
      allowNavigationAfterSave.current = false;
      submitLocationKey.current = null;
    }
  }, [blocker, location.key]);

  useEffect(() => {
    if (blocker.state === "blocked" && !editor.authorized) {
      blocker.proceed();
    }
  }, [blocker, editor.authorized]);

  const shouldWarnBeforeUnload =
    editor.authorized && (editor.dirty || editor.saving);
  useEffect(() => {
    if (!shouldWarnBeforeUnload) return;
    const preventUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", preventUnload);
    return () => window.removeEventListener("beforeunload", preventUnload);
  }, [shouldWarnBeforeUnload]);

  const draft = editor.draft;
  const matchingDraft = editor.matchesTarget(mode, branchId);
  const values = matchingDraft ? (draft?.values ?? null) : null;
  const validation = useMemo(
    () => validateBranchEditorValues(mode, values ?? emptyBranchEditorValues),
    [mode, values],
  );
  const canSave = Boolean(
    editor.authorized &&
    matchingDraft &&
    editor.dirty &&
    validation.valid &&
    !editor.saving &&
    !editor.outcomeUncertain &&
    !editor.versionConflict,
  );

  if (
    auth.privacyCovered ||
    auth.state.kind === "checking" ||
    auth.pendingAction !== null
  ) {
    return loadingPage(title, "Comprobando que la sesión pueda continuar.");
  }

  if (!editor.authorized) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          {title}
        </Typography.Title>
        <StatePanel
          action={
            <Link className="cg-organization-link" to="/sucursales">
              Volver al catálogo
            </Link>
          }
          description="La administración de sucursales requiere una cuenta ADMIN con alcance de organización. El servidor vuelve a comprobar este permiso al guardar."
          title="Acceso restringido"
          variant="error"
        />
      </section>
    );
  }

  if (!validBranchId) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          {title}
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

  if (mode === "update") {
    if (branchQuery.isPending || (branchQuery.isFetching && !matchingDraft)) {
      return loadingPage(title, "Consultando la ficha actual de la sucursal.");
    }
    if (branchQuery.isError && !matchingDraft) {
      return (
        <section
          aria-labelledby="main-heading"
          className="cg-organization-page"
        >
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            {title}
          </Typography.Title>
          <StatePanel
            action={
              <Space wrap>
                <Button onClick={() => void branchQuery.refetch()}>
                  Reintentar consulta
                </Button>
                <Link className="cg-organization-link" to="/sucursales">
                  Volver al catálogo
                </Link>
              </Space>
            }
            description="No se pudo cargar la versión actual de la sucursal. No se enviará ningún cambio."
            title="No se pudo preparar la edición"
            variant="error"
          />
        </section>
      );
    }
    if (branchQuery.data && branchQuery.data.id !== branchId) {
      return (
        <section
          aria-labelledby="main-heading"
          className="cg-organization-page"
        >
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            {title}
          </Typography.Title>
          <StatePanel
            description="La respuesta no corresponde a la sucursal solicitada. No se enviará ningún cambio."
            title="No se pudo confirmar la sucursal"
            variant="error"
          />
        </section>
      );
    }
    if (!matchingDraft)
      return loadingPage(title, "Inicializando el borrador local.");
  } else if (!matchingDraft) {
    return loadingPage(title, "Inicializando el borrador local.");
  }

  if (!draft || !values)
    return loadingPage(title, "Preparando los datos locales.");

  const handleSubmit = async () => {
    setShowErrors(true);
    if (!validation.valid || !canSave) return;
    const saved = await editor.save();
    if (!saved) return;
    allowNavigationAfterSave.current = true;
    submitLocationKey.current = location.key;
    navigate(`/sucursales/${saved.id}`, {
      replace: true,
      state: { returnTo },
    });
  };

  const uncertainLookup =
    mode === "create"
      ? `/sucursales${branchCatalogSearch({
          page: 0,
          size: 25,
          sort: "CODE",
          direction: "ASC",
          ...(values.code.trim() ? { search: values.code.trim() } : {}),
        })}`
      : `/sucursales/${draft.branchId}`;

  return (
    <section aria-labelledby="main-heading" className="cg-organization-page">
      <div className="cg-organization-heading">
        <div>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            {title}
          </Typography.Title>
          <Typography.Paragraph>
            {mode === "create"
              ? "Registra una sucursal para la organización. El servidor asignará su estado inicial."
              : "Actualiza los datos editables. El código, estado y sucursal inicial los controla el servidor."}
          </Typography.Paragraph>
        </div>
        {mode === "update" && branchQuery.data ? (
          <Typography.Text className="cg-branch-editor-code">
            Código: <strong>{branchQuery.data.code}</strong>
          </Typography.Text>
        ) : null}
      </div>

      {branchQuery.isError && matchingDraft ? (
        <Alert
          className="cg-branch-editor-alert"
          showIcon
          title="No se pudo actualizar la lectura de la sucursal."
          description="Tu borrador local se conserva. El servidor comprobará la versión al guardar."
          type="warning"
        />
      ) : null}

      {editor.writeError && !editor.outcomeUncertain ? (
        <Alert
          className="cg-branch-editor-alert"
          showIcon
          title={
            editor.writeError.code === "GYM_BRANCH_CODE_CONFLICT"
              ? "Ese código ya está en uso."
              : editor.writeError.code === "GYM_BRANCH_VERSION_CONFLICT"
                ? "La ficha cambió en el servidor."
                : editor.writeError.status === 403
                  ? "El servidor denegó la operación."
                  : "No se pudo guardar la sucursal."
          }
          description={
            editor.writeError.status === 403
              ? `${editor.writeError.message} Se comprobarán de nuevo la sesión y el permiso antes de continuar.`
              : editor.writeError.message
          }
          type="error"
        />
      ) : null}

      {editor.outcomeUncertain ? (
        <Alert
          className="cg-branch-editor-alert"
          showIcon
          title="No se pudo confirmar el resultado del guardado."
          description={
            <span>
              No se repetirá la solicitud automáticamente. Comprueba el estado
              en otra pestaña y vuelve a iniciar el formulario solo después de
              confirmar que no se aplicó.
            </span>
          }
          type="warning"
        />
      ) : null}

      {editor.versionConflict ? (
        <Alert
          action={
            editor.latestBranch ? (
              <Button onClick={() => setConfirmReplace(true)} type="link">
                Usar versión actual
              </Button>
            ) : (
              <Button
                loading={editor.loadingLatest}
                onClick={() => void editor.loadLatest()}
                type="link"
              >
                Consultar versión actual
              </Button>
            )
          }
          className="cg-branch-editor-alert"
          showIcon
          title="Tus cambios se conservaron; no se reemplazó la versión base."
          description={
            editor.latestBranch
              ? `Versión actual del servidor: ${editor.latestBranch.version}. Puedes revisar y confirmar su uso para reemplazar este borrador.`
              : (editor.latestReadError?.message ??
                "Consulta la ficha actual antes de decidir si reemplazas tu borrador.")
          }
          type="warning"
        />
      ) : null}

      {editor.blockedWriteReason ? (
        <Alert
          className="cg-branch-editor-alert"
          showIcon
          title="Espera antes de guardar"
          description={editor.blockedWriteReason}
          type="info"
        />
      ) : null}

      {editor.outcomeUncertain ? (
        <div className="cg-branch-editor-uncertain-link">
          <Link rel="noreferrer" target="_blank" to={uncertainLookup}>
            {mode === "create"
              ? "Comprobar en el catálogo en otra pestaña"
              : "Consultar la ficha en otra pestaña"}
          </Link>
        </div>
      ) : null}

      <Card className="cg-branch-editor-card" variant="outlined">
        {mode === "update" ? (
          <Typography.Paragraph className="cg-branch-editor-version">
            Versión base recibida del servidor: {draft.baseVersion}
          </Typography.Paragraph>
        ) : null}
        <BranchEditorForm
          disabled={
            !editor.authorized || editor.saving || editor.outcomeUncertain
          }
          errors={validation.errors}
          mode={mode}
          onFieldChange={(field, value) => {
            setShowErrors(false);
            editor.setField(field, value);
          }}
          onSubmit={() => void handleSubmit()}
          showErrors={showErrors}
          values={values}
        />
        <div className="cg-branch-editor-footer">
          <Typography.Text aria-live="polite" role="status">
            {editor.dirty
              ? "Hay cambios sin guardar."
              : "No hay cambios para guardar."}
          </Typography.Text>
          <Space wrap>
            <Button disabled={editor.saving} onClick={() => navigate(returnTo)}>
              Cancelar
            </Button>
            <Button
              disabled={!canSave}
              htmlType="submit"
              loading={editor.saving}
              form="branch-editor-form"
              type="primary"
            >
              {mode === "create" ? "Crear sucursal" : "Guardar cambios"}
            </Button>
          </Space>
        </div>
      </Card>

      <Modal
        cancelText="Seguir editando"
        okButtonProps={{ danger: true }}
        okText="Descartar y salir"
        onCancel={() => {
          if (blocker.state === "blocked") blocker.reset();
        }}
        onOk={() => {
          editor.discard();
          if (blocker.state === "blocked") blocker.proceed();
        }}
        open={blocker.state === "blocked"}
        title="Tienes cambios sin guardar"
      >
        <Typography.Paragraph>
          Si sales ahora, se descartará el borrador local de la sucursal.
        </Typography.Paragraph>
      </Modal>

      <Modal
        cancelText="Conservar mi borrador"
        okButtonProps={{ danger: true }}
        okText="Reemplazar con la versión actual"
        onCancel={() => setConfirmReplace(false)}
        onOk={() => {
          editor.replaceWithLatest();
          setConfirmReplace(false);
          setShowErrors(false);
        }}
        open={confirmReplace}
        title="Reemplazar el borrador"
      >
        <Typography.Paragraph>
          Se descartarán los valores editados y se usará la ficha más reciente
          del servidor con su versión actual.
        </Typography.Paragraph>
      </Modal>
    </section>
  );
}
