import { useEffect, useRef } from "react";
import { flushSync } from "react-dom";
import { Button, Form, Input } from "antd";
import type { FormInstance } from "antd";
import { ApiError } from "../../shared/api/errors";
import { StatePanel } from "../../shared/ui/StatePanel";
import { passwordChangeErrorMessage } from "./auth-messages";
import { useAuth } from "./useAuth";
import { validatePasswordChangeField } from "./password-validation";
import type { PasswordChangeValues } from "./password-validation";
import { useRetryDelay } from "./useRetryDelay";
import "./auth-forms.css";

const emptyValues: PasswordChangeValues = {
  currentPassword: "",
  newPassword: "",
  newPasswordConfirmation: "",
};

function valuesFromForm(
  form: FormInstance<PasswordChangeValues>,
): PasswordChangeValues {
  const values = form.getFieldsValue();
  return {
    currentPassword:
      typeof values.currentPassword === "string" ? values.currentPassword : "",
    newPassword:
      typeof values.newPassword === "string" ? values.newPassword : "",
    newPasswordConfirmation:
      typeof values.newPasswordConfirmation === "string"
        ? values.newPasswordConfirmation
        : "",
  };
}

function fieldValidator(
  form: FormInstance<PasswordChangeValues>,
  field: keyof PasswordChangeValues,
) {
  return async () => {
    const error = validatePasswordChangeField(field, valuesFromForm(form));
    if (error) throw new Error(error);
  };
}

function passwordFieldError(error: ApiError | null): string | undefined {
  return error?.code === "CURRENT_PASSWORD_INVALID"
    ? "No se pudo verificar la contraseña actual."
    : undefined;
}

export function PasswordChangeForm({
  submitLabel = "Actualizar contraseña",
}: {
  submitLabel?: string;
}) {
  const [form] = Form.useForm<PasswordChangeValues>();
  const submittingRef = useRef(false);
  const auth = useAuth();
  const retryDelay = useRetryDelay(auth.error);
  const passwordPending = auth.pendingAction === "password";
  const serverError = auth.error;

  useEffect(
    () => () => {
      form.resetFields();
    },
    [form],
  );

  function clearPasswords(): void {
    flushSync(() => form.resetFields());
  }

  async function submit(values: PasswordChangeValues): Promise<void> {
    if (submittingRef.current || retryDelay > 0) return;
    submittingRef.current = true;
    let requestAttempted = false;
    let passwordsCleared = false;

    try {
      const ticket = await auth.beginPasswordChange();
      if (!ticket) return;

      let result = null;
      let requestError: ApiError | null = null;
      requestAttempted = true;
      try {
        const request = auth.sendPasswordChange(ticket, {
          currentPassword: values.currentPassword,
          newPassword: values.newPassword,
          newPasswordConfirmation: values.newPasswordConfirmation,
        });
        clearPasswords();
        passwordsCleared = true;
        result = await request;
      } catch (error) {
        requestError =
          error instanceof ApiError ? error : new ApiError("network");
      } finally {
        if (!passwordsCleared) {
          clearPasswords();
          passwordsCleared = true;
        }
      }

      if (requestError) {
        await auth.failPasswordChange(ticket, requestError);
      } else if (result) {
        await auth.completePasswordChange(ticket, result);
      }
    } finally {
      if (requestAttempted && !passwordsCleared) clearPasswords();
      submittingRef.current = false;
    }
  }

  const conflict = serverError?.code === "STAFF_PROFILE_VERSION_CONFLICT";
  const currentPasswordError = passwordFieldError(serverError);

  return (
    <div className="cg-auth-form-stack">
      {serverError ? (
        <StatePanel
          action={
            conflict ? (
              <Button
                disabled={passwordPending}
                onClick={() => void auth.revalidate()}
              >
                Comprobar sesión
              </Button>
            ) : undefined
          }
          title={passwordChangeErrorMessage(serverError)}
          variant="error"
        />
      ) : null}
      <Form<PasswordChangeValues>
        form={form}
        initialValues={emptyValues}
        layout="vertical"
        onFinish={(formValues) => void submit(formValues)}
        preserve={false}
        scrollToFirstError={{ focus: true }}
      >
        <Form.Item
          label="Contraseña actual"
          name="currentPassword"
          rules={[{ validator: fieldValidator(form, "currentPassword") }]}
          {...(currentPasswordError
            ? { validateStatus: "error" as const, help: currentPasswordError }
            : serverError?.fieldErrors.currentPassword
              ? { validateStatus: "error" as const, help: "Revisa este campo." }
              : {})}
        >
          <Input.Password
            autoComplete="current-password"
            disabled={passwordPending}
          />
        </Form.Item>
        <Form.Item
          dependencies={["currentPassword"]}
          label="Nueva contraseña"
          name="newPassword"
          rules={[{ validator: fieldValidator(form, "newPassword") }]}
          {...(serverError?.fieldErrors.newPassword
            ? { validateStatus: "error" as const, help: "Revisa este campo." }
            : {})}
        >
          <Input.Password
            autoComplete="new-password"
            disabled={passwordPending}
          />
        </Form.Item>
        <Form.Item
          dependencies={["newPassword"]}
          label="Confirmar nueva contraseña"
          name="newPasswordConfirmation"
          rules={[
            { validator: fieldValidator(form, "newPasswordConfirmation") },
          ]}
          {...(serverError?.fieldErrors.newPasswordConfirmation
            ? { validateStatus: "error" as const, help: "Revisa este campo." }
            : {})}
        >
          <Input.Password
            autoComplete="new-password"
            disabled={passwordPending}
          />
        </Form.Item>
        <Button
          block
          disabled={passwordPending || retryDelay > 0}
          htmlType="submit"
          loading={passwordPending}
          type="primary"
        >
          {retryDelay > 0
            ? `Espera ${retryDelay} s para volver a intentarlo`
            : submitLabel}
        </Button>
      </Form>
    </div>
  );
}
