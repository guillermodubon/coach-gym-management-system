import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { Alert, Button, Form, Input, Typography } from "antd";
import { Link } from "react-router";
import type { StaffInvitationAcceptanceRequest } from "../../shared/api/public-identity-contracts";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { useAuth } from "../auth/useAuth";
import { useRetryDelay } from "../auth/useRetryDelay";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import type { ApiError as ApiErrorType } from "../../shared/api/errors";
import { usePublicIdentityRuntime } from "./PublicIdentityProvider";
import { safePublicIdentityError } from "./public-identity-errors";
import {
  normalizeInvitationName,
  validateInvitationAcceptance,
} from "./invitation-validation";
import type { InvitationAcceptanceValues } from "./invitation-validation";
import {
  InvitationProposalSummary,
  invitationPreviewView,
} from "./InvitationProposalSummary";
import type { InvitationPreviewView } from "./InvitationProposalSummary";
import "./invitation-acceptance.css";

type PageState = "editing" | "accepted" | "unavailable" | "uncertain";
type Feedback = { operation: "inspect" | "accept"; error: ApiErrorType };

const unavailableMessage =
  "El enlace de invitación no está disponible. Solicita a administración que envíe uno nuevo.";

function isUncertainAcceptance(error: ApiErrorType): boolean {
  return (
    error.kind === "network" ||
    error.kind === "timeout" ||
    error.kind === "invalid-response" ||
    (error.status !== undefined && error.status >= 500)
  );
}

function inspectErrorMessage(error: ApiErrorType): string {
  if (error.code === "CSRF_TOKEN_INVALID") {
    return "No se pudo validar la solicitud. Vuelve a intentar la revisión.";
  }
  if (error.status === 429) {
    return "Hay demasiados intentos de revisión. Espera antes de volver a intentarlo.";
  }
  return "No se pudo revisar la invitación. Puedes intentar la revisión de nuevo.";
}

function acceptErrorMessage(error: ApiErrorType): string {
  if (error.status === 400) {
    return "Revisa tus nombres y los requisitos de la contraseña. Vuelve a escribir tus datos.";
  }
  if (error.code === "CSRF_TOKEN_INVALID") {
    return "No se pudo validar la solicitud. Vuelve a escribir tus datos e inténtalo de nuevo.";
  }
  if (error.status === 429) {
    return "Hay demasiados intentos de aceptación. Espera antes de volver a intentarlo.";
  }
  return "No se pudo completar la aceptación. Vuelve a escribir tus datos e inténtalo de nuevo.";
}

function isFieldValue(value: unknown): value is string {
  return typeof value === "string";
}

export function InvitationAcceptancePage() {
  const [form] = Form.useForm<InvitationAcceptanceValues>();
  const [preview, setPreview] = useState<InvitationPreviewView | null>(null);
  const [pageState, setPageState] = useState<PageState>("editing");
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const inspecting = useRef(false);
  const accepting = useRef(false);
  const auth = useAuth();
  const { publicIdentityApi } = useApiServices();
  const { runtime, snapshot } = usePublicIdentityRuntime();
  const token =
    snapshot.kind === "available" && snapshot.purpose === "invitation"
      ? snapshot.token
      : null;
  const retryDelay = useRetryDelay(feedback?.error ?? null);
  const pending = auth.pendingAction === "public-identity";

  function clearFields(): void {
    flushSync(() => form.resetFields());
  }

  async function inspectInvitation(): Promise<void> {
    if (!token || preview || inspecting.current || pageState !== "editing") {
      return;
    }
    inspecting.current = true;
    setFeedback(null);

    try {
      const ticket = await auth.beginPublicIdentityAction("invitation-inspect");
      if (!ticket) return;
      try {
        const result = await publicIdentityApi.inspectStaffInvitation(token, {
          signal: ticket.signal,
        });
        setPreview(invitationPreviewView(result));
      } catch (caught) {
        const error = safePublicIdentityError(caught);
        if (
          error.status === 400 ||
          error.status === 409 ||
          error.kind === "invalid-response"
        ) {
          runtime.clear();
          setPageState("unavailable");
          return;
        }
        setFeedback({ operation: "inspect", error });
      } finally {
        auth.finishPublicIdentityAction(ticket);
      }
    } catch (caught) {
      setFeedback({
        operation: "inspect",
        error: safePublicIdentityError(caught),
      });
    } finally {
      inspecting.current = false;
    }
  }

  async function acceptInvitation(values: InvitationAcceptanceValues) {
    if (!token || !preview || accepting.current || pageState !== "editing") {
      return;
    }
    accepting.current = true;
    setFeedback(null);
    const validation = validateInvitationAcceptance(values);
    if (!validation.valid) {
      accepting.current = false;
      return;
    }
    const request: StaffInvitationAcceptanceRequest = {
      token,
      firstName: normalizeInvitationName(values.firstName),
      lastName: normalizeInvitationName(values.lastName),
      password: values.password,
      passwordConfirmation: values.passwordConfirmation,
    };
    clearFields();

    try {
      const ticket = await auth.beginPublicIdentityAction("invitation-accept");
      if (!ticket) return;
      try {
        await publicIdentityApi.acceptStaffInvitation(request, {
          signal: ticket.signal,
        });
        runtime.clear();
        setPreview(null);
        setPageState("accepted");
      } catch (caught) {
        const error = safePublicIdentityError(caught);
        if (error.status === 409) {
          runtime.clear();
          setPreview(null);
          setPageState("unavailable");
        } else if (isUncertainAcceptance(error)) {
          runtime.clear();
          setPreview(null);
          setPageState("uncertain");
        } else {
          setFeedback({ operation: "accept", error });
        }
      } finally {
        auth.finishPublicIdentityAction(ticket);
      }
    } catch (caught) {
      const error = safePublicIdentityError(caught);
      if (isUncertainAcceptance(error)) {
        runtime.clear();
        setPreview(null);
        setPageState("uncertain");
      } else {
        setFeedback({ operation: "accept", error });
      }
    } finally {
      clearFields();
      accepting.current = false;
    }
  }

  if (pageState === "accepted") {
    return (
      <IdentityLayout>
        <IdentityCard>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Invitación aceptada
          </Typography.Title>
          <Alert
            showIcon
            title="Tu cuenta está lista. Inicia sesión con el correo que recibió esta invitación."
            type="success"
          />
          <Link className="cg-recovery-link" to="/login">
            Ir a iniciar sesión
          </Link>
        </IdentityCard>
      </IdentityLayout>
    );
  }

  if (pageState === "unavailable" || pageState === "uncertain") {
    return (
      <IdentityLayout>
        <IdentityCard>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Aceptar invitación
          </Typography.Title>
          <Alert
            showIcon
            title={
              pageState === "unavailable"
                ? unavailableMessage
                : "No pudimos confirmar si la aceptación se completó. Inicia sesión con el correo de la invitación; si no puedes, solicita una nueva. No vuelvas a enviar este formulario."
            }
            type="warning"
          />
          <Link className="cg-recovery-link" to="/login">
            Volver a iniciar sesión
          </Link>
        </IdentityCard>
      </IdentityLayout>
    );
  }

  if (!token) {
    return (
      <IdentityLayout>
        <IdentityCard>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Aceptar invitación
          </Typography.Title>
          <Alert
            showIcon
            title="El enlace de invitación no está disponible. Abre el enlace completo que recibiste o solicita a administración una invitación nueva."
            type="warning"
          />
          <Link className="cg-recovery-link" to="/login">
            Volver a iniciar sesión
          </Link>
        </IdentityCard>
      </IdentityLayout>
    );
  }

  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Aceptar invitación
        </Typography.Title>
        {!preview ? (
          <>
            <Typography.Paragraph>
              Revisa la invitación antes de crear tu acceso a Coach Gym.
            </Typography.Paragraph>
            {feedback?.operation === "inspect" ? (
              <Alert
                className="cg-invitation-feedback"
                description={`${inspectErrorMessage(feedback.error)}${retryDelay > 0 ? ` Espera ${retryDelay} segundos.` : ""}`}
                showIcon
                title="No se pudo revisar la invitación"
                type="error"
              />
            ) : null}
            <Button
              block
              disabled={pending || retryDelay > 0}
              loading={pending}
              onClick={() => void inspectInvitation()}
              type="primary"
            >
              {feedback?.operation === "inspect"
                ? "Intentar revisión de nuevo"
                : "Revisar invitación"}
            </Button>
          </>
        ) : (
          <>
            <Typography.Paragraph>
              Confirma que esta propuesta corresponde a la invitación que
              esperabas. El rol y las sucursales los determina quien envió la
              invitación.
            </Typography.Paragraph>
            <InvitationProposalSummary preview={preview} />
            <Typography.Paragraph>
              Completa tu nombre y define una contraseña de 12 a 256 caracteres.
              La contraseña se envía exactamente como la escribes.
            </Typography.Paragraph>
            {feedback?.operation === "accept" ? (
              <Alert
                className="cg-invitation-feedback"
                description={`${acceptErrorMessage(feedback.error)}${retryDelay > 0 ? ` Espera ${retryDelay} segundos.` : ""}`}
                showIcon
                title="No se pudo aceptar la invitación"
                type="error"
              />
            ) : null}
            <Form<InvitationAcceptanceValues>
              form={form}
              layout="vertical"
              onFinish={(values) => void acceptInvitation(values)}
              scrollToFirstError
            >
              <Form.Item
                label="Nombre"
                name="firstName"
                rules={[
                  {
                    validator: async (_, value: unknown) => {
                      const values = form.getFieldsValue();
                      const result = validateInvitationAcceptance({
                        firstName: isFieldValue(value) ? value : "",
                        lastName: isFieldValue(values.lastName)
                          ? values.lastName
                          : "",
                        password: isFieldValue(values.password)
                          ? values.password
                          : "",
                        passwordConfirmation: isFieldValue(
                          values.passwordConfirmation,
                        )
                          ? values.passwordConfirmation
                          : "",
                      });
                      if (result.errors.firstName)
                        throw new Error(result.errors.firstName);
                    },
                  },
                ]}
              >
                <Input
                  autoComplete="given-name"
                  disabled={pending || retryDelay > 0}
                  maxLength={100}
                />
              </Form.Item>
              <Form.Item
                label="Apellido"
                name="lastName"
                rules={[
                  {
                    validator: async (_, value: unknown) => {
                      const values = form.getFieldsValue();
                      const result = validateInvitationAcceptance({
                        firstName: isFieldValue(values.firstName)
                          ? values.firstName
                          : "",
                        lastName: isFieldValue(value) ? value : "",
                        password: isFieldValue(values.password)
                          ? values.password
                          : "",
                        passwordConfirmation: isFieldValue(
                          values.passwordConfirmation,
                        )
                          ? values.passwordConfirmation
                          : "",
                      });
                      if (result.errors.lastName)
                        throw new Error(result.errors.lastName);
                    },
                  },
                ]}
              >
                <Input
                  autoComplete="family-name"
                  disabled={pending || retryDelay > 0}
                  maxLength={100}
                />
              </Form.Item>
              <Form.Item
                label="Contraseña"
                name="password"
                rules={[
                  {
                    validator: async (_, value: unknown) => {
                      const values = form.getFieldsValue();
                      const result = validateInvitationAcceptance({
                        firstName: isFieldValue(values.firstName)
                          ? values.firstName
                          : "",
                        lastName: isFieldValue(values.lastName)
                          ? values.lastName
                          : "",
                        password: isFieldValue(value) ? value : "",
                        passwordConfirmation: isFieldValue(
                          values.passwordConfirmation,
                        )
                          ? values.passwordConfirmation
                          : "",
                      });
                      if (result.errors.password)
                        throw new Error(result.errors.password);
                    },
                  },
                ]}
              >
                <Input.Password
                  autoComplete="new-password"
                  disabled={pending || retryDelay > 0}
                  maxLength={256}
                />
              </Form.Item>
              <Form.Item
                dependencies={["password"]}
                label="Confirma la contraseña"
                name="passwordConfirmation"
                rules={[
                  {
                    validator: async (_, value: unknown) => {
                      const values = form.getFieldsValue();
                      const result = validateInvitationAcceptance({
                        firstName: isFieldValue(values.firstName)
                          ? values.firstName
                          : "",
                        lastName: isFieldValue(values.lastName)
                          ? values.lastName
                          : "",
                        password: isFieldValue(values.password)
                          ? values.password
                          : "",
                        passwordConfirmation: isFieldValue(value) ? value : "",
                      });
                      if (result.errors.passwordConfirmation)
                        throw new Error(result.errors.passwordConfirmation);
                    },
                  },
                ]}
              >
                <Input.Password
                  autoComplete="new-password"
                  disabled={pending || retryDelay > 0}
                  maxLength={256}
                />
              </Form.Item>
              <Button
                block
                disabled={pending || retryDelay > 0}
                htmlType="submit"
                loading={pending}
                type="primary"
              >
                Aceptar invitación
              </Button>
            </Form>
          </>
        )}
        <Link className="cg-recovery-link" to="/login">
          Cancelar y volver a iniciar sesión
        </Link>
      </IdentityCard>
    </IdentityLayout>
  );
}
