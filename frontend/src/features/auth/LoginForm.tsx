import { useRef } from "react";
import { flushSync } from "react-dom";
import { Alert, Button, Form, Input } from "antd";
import { Link } from "react-router";
import { ApiError } from "../../shared/api/errors";
import { StatePanel } from "../../shared/ui/StatePanel";
import { authErrorMessage, authNoticeMessage } from "./auth-messages";
import { useAuth } from "./useAuth";
import { useRetryDelay } from "./useRetryDelay";
import "./auth-forms.css";

interface LoginValues {
  identifier: string;
  password: string;
}

export function LoginForm() {
  const [form] = Form.useForm<LoginValues>();
  const submittingRef = useRef(false);
  const auth = useAuth();
  const retryDelay = useRetryDelay(auth.error);

  function clearPassword(): void {
    flushSync(() => form.setFieldValue("password", ""));
  }

  async function submit(values: LoginValues): Promise<void> {
    if (submittingRef.current) return;
    submittingRef.current = true;
    let passwordCleared = false;

    try {
      const ticket = await auth.beginLogin();
      if (!ticket) return;

      let requestError: ApiError | null = null;
      try {
        await auth.sendLogin(ticket, {
          identifier: values.identifier,
          password: values.password,
        });
      } catch (error) {
        requestError =
          error instanceof ApiError ? error : new ApiError("network");
      } finally {
        clearPassword();
        passwordCleared = true;
      }

      if (requestError) {
        await auth.failLogin(ticket, requestError);
      } else {
        await auth.completeLogin(ticket);
      }
    } finally {
      if (!passwordCleared) clearPassword();
      submittingRef.current = false;
    }
  }

  const error = auth.error;
  const notice = authNoticeMessage(auth.notice);
  const loginPending = auth.pendingAction === "login";

  return (
    <div className="cg-auth-form-stack">
      {notice ? <Alert showIcon title={notice} type="info" /> : null}
      {error ? (
        <StatePanel
          variant="error"
          title={authErrorMessage(error)}
          {...(retryDelay > 0
            ? {
                description: `Espera ${retryDelay} segundos antes de volver a intentarlo.`,
              }
            : {})}
        />
      ) : null}
      <Form<LoginValues>
        form={form}
        layout="vertical"
        onFinish={(values) => void submit(values)}
        scrollToFirstError
      >
        <Form.Item
          label="Usuario o correo"
          name="identifier"
          rules={[
            {
              validator: async (_, value: unknown) => {
                if (typeof value !== "string" || value.length === 0) {
                  throw new Error("Escribe tu usuario o correo.");
                }
                if (value.length > 254) {
                  throw new Error("El usuario o correo es demasiado largo.");
                }
              },
            },
          ]}
          {...(error?.fieldErrors.identifier
            ? { validateStatus: "error" as const, help: "Revisa este campo." }
            : {})}
        >
          <Input
            autoComplete="username"
            autoFocus
            disabled={loginPending}
            maxLength={254}
          />
        </Form.Item>
        <Form.Item
          label="Contraseña"
          name="password"
          rules={[
            {
              validator: async (_, value: unknown) => {
                if (typeof value !== "string" || value.length === 0) {
                  throw new Error("Escribe tu contraseña.");
                }
                if (value.length > 256) {
                  throw new Error(
                    "La contraseña no puede superar 256 caracteres.",
                  );
                }
              },
            },
          ]}
          {...(error?.fieldErrors.password
            ? { validateStatus: "error" as const, help: "Revisa este campo." }
            : {})}
        >
          <Input.Password
            autoComplete="current-password"
            disabled={loginPending}
            maxLength={256}
          />
        </Form.Item>
        <Button
          block
          disabled={retryDelay > 0 || loginPending}
          htmlType="submit"
          loading={loginPending}
          type="primary"
        >
          Iniciar sesión
        </Button>
      </Form>
      <Link className="cg-recovery-link" to="/solicitar-recuperacion">
        ¿Olvidaste tu contraseña?
      </Link>
    </div>
  );
}
