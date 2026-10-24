import { useRef, useState } from "react";
import { flushSync } from "react-dom";
import { Alert, Button, Form, Input, Typography } from "antd";
import { Link } from "react-router";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { useAuth } from "../auth/useAuth";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import type { ApiError } from "../../shared/api/errors";
import { useRetryDelay } from "../auth/useRetryDelay";
import { usePublicIdentityRuntime } from "./PublicIdentityProvider";
import {
  recoveryCompletionErrorMessage,
  safePublicIdentityError,
} from "./public-identity-errors";
import { validatePasswordRecovery } from "./password-recovery-validation";
import type { PasswordRecoveryValues } from "./password-recovery-validation";
import "./identity-recovery.css";

type CompletionState = "editing" | "success" | "unavailable" | "uncertain";

export function PasswordRecoveryCompletionPage() {
  const [form] = Form.useForm<PasswordRecoveryValues>();
  const [error, setError] = useState<ApiError | null>(null);
  const [completion, setCompletion] = useState<CompletionState>("editing");
  const submitting = useRef(false);
  const auth = useAuth();
  const { publicIdentityApi } = useApiServices();
  const { runtime, snapshot } = usePublicIdentityRuntime();
  const retryDelay = useRetryDelay(error);
  const pending = auth.pendingAction === "public-identity";
  const token =
    snapshot.kind === "available" && snapshot.purpose === "password-recovery"
      ? snapshot.token
      : null;

  function clearPasswordFields(): void {
    flushSync(() => form.resetFields());
  }

  async function submit(values: PasswordRecoveryValues): Promise<void> {
    if (!token || submitting.current) return;
    submitting.current = true;
    setError(null);
    setCompletion("editing");
    const request = {
      token,
      newPassword: values.newPassword,
      passwordConfirmation: values.passwordConfirmation,
    };
    clearPasswordFields();

    try {
      const ticket = await auth.beginPublicIdentityAction("recovery-complete");
      if (!ticket) return;
      try {
        await publicIdentityApi.completePasswordRecovery(request, {
          signal: ticket.signal,
        });
        runtime.clear();
        setCompletion("success");
      } catch (caught) {
        const safeError = safePublicIdentityError(caught);
        setError(safeError);
        if (safeError.status === 409) {
          runtime.clear();
          setCompletion("unavailable");
        } else if (
          safeError.kind === "network" ||
          safeError.kind === "timeout" ||
          safeError.kind === "invalid-response" ||
          (safeError.status !== undefined && safeError.status >= 500)
        ) {
          runtime.clear();
          setCompletion("uncertain");
        }
      } finally {
        auth.finishPublicIdentityAction(ticket);
      }
    } finally {
      clearPasswordFields();
      submitting.current = false;
    }
  }

  if (completion === "success") {
    return (
      <IdentityLayout>
        <IdentityCard>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Contraseña actualizada
          </Typography.Title>
          <Alert
            showIcon
            title="La contraseña se actualizó. Inicia sesión con tu nueva contraseña."
            type="success"
          />
          <Link className="cg-recovery-link" to="/login">
            Ir a iniciar sesión
          </Link>
        </IdentityCard>
      </IdentityLayout>
    );
  }

  if (completion === "unavailable" || completion === "uncertain") {
    return (
      <IdentityLayout>
        <IdentityCard>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Recuperar contraseña
          </Typography.Title>
          <Alert
            showIcon
            title={
              completion === "unavailable"
                ? "El enlace de recuperación no está disponible. Solicita uno nuevo."
                : "No pudimos confirmar el cambio. Inicia sesión con la contraseña nueva si la recuerdas; si no, solicita otro enlace."
            }
            type="warning"
          />
          <Link className="cg-recovery-link" to="/solicitar-recuperacion">
            Solicitar otro enlace
          </Link>
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
            Recuperar contraseña
          </Typography.Title>
          <Alert
            showIcon
            title="El enlace no está disponible. Abre el enlace completo del correo o solicita uno nuevo."
            type="warning"
          />
          <Link className="cg-recovery-link" to="/solicitar-recuperacion">
            Solicitar otro enlace
          </Link>
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
          Define una contraseña nueva
        </Typography.Title>
        <Typography.Paragraph>
          Usa entre 12 y 256 caracteres. Las contraseñas se envían sin
          normalización ni recortes.
        </Typography.Paragraph>
        {error ? (
          <Alert
            className="cg-recovery-feedback"
            description={`${recoveryCompletionErrorMessage(error)}${retryDelay > 0 ? ` Espera ${retryDelay} segundos.` : ""}`}
            showIcon
            title="No se pudo completar el cambio"
            type="error"
          />
        ) : null}
        <Form<PasswordRecoveryValues>
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
          scrollToFirstError
        >
          <Form.Item
            label="Contraseña nueva"
            name="newPassword"
            validateTrigger="onBlur"
            rules={[
              {
                validator: async (_, value: unknown) => {
                  const error =
                    typeof value === "string"
                      ? validatePasswordRecovery({
                          newPassword: value,
                          passwordConfirmation:
                            form.getFieldValue("passwordConfirmation") ?? "",
                        }).newPassword
                      : "Escribe una contraseña nueva.";
                  if (error) throw new Error(error);
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
            label="Confirma la contraseña nueva"
            name="passwordConfirmation"
            validateTrigger="onBlur"
            rules={[
              {
                validator: async (_, value: unknown) => {
                  const error =
                    typeof value === "string"
                      ? validatePasswordRecovery({
                          newPassword: form.getFieldValue("newPassword") ?? "",
                          passwordConfirmation: value,
                        }).passwordConfirmation
                      : "Confirma la contraseña nueva.";
                  if (error) throw new Error(error);
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
            Actualizar contraseña
          </Button>
        </Form>
        <Link className="cg-recovery-link" to="/login">
          Cancelar y volver a iniciar sesión
        </Link>
      </IdentityCard>
    </IdentityLayout>
  );
}
