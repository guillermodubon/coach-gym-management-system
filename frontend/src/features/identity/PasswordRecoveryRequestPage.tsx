import { useRef, useState } from "react";
import { Alert, Button, Form, Input, Typography } from "antd";
import { Link } from "react-router";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { useAuth } from "../auth/useAuth";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import { ApiError } from "../../shared/api/errors";
import { useRetryDelay } from "../auth/useRetryDelay";
import {
  recoveryRequestErrorMessage,
  safePublicIdentityError,
} from "./public-identity-errors";
import "./identity-recovery.css";

interface RecoveryRequestValues {
  email: string;
}

export function PasswordRecoveryRequestPage() {
  const [form] = Form.useForm<RecoveryRequestValues>();
  const [error, setError] = useState<ApiError | null>(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const submitting = useRef(false);
  const auth = useAuth();
  const { publicIdentityApi } = useApiServices();
  const retryDelay = useRetryDelay(error);
  const pending = auth.pendingAction === "public-identity";

  async function submit(values: RecoveryRequestValues): Promise<void> {
    if (submitting.current) return;
    submitting.current = true;
    setError(null);
    setAcknowledged(false);

    try {
      const ticket = await auth.beginPublicIdentityAction("recovery-request");
      if (!ticket) {
        setError(new ApiError("network"));
        return;
      }
      try {
        await publicIdentityApi.requestPasswordRecovery(
          { email: values.email.trim() },
          { signal: ticket.signal },
        );
        form.resetFields();
        setAcknowledged(true);
      } catch (caught) {
        setError(safePublicIdentityError(caught));
      } finally {
        auth.finishPublicIdentityAction(ticket);
      }
    } finally {
      submitting.current = false;
    }
  }

  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Recuperar contraseña
        </Typography.Title>
        <Typography.Paragraph>
          Escribe el correo asociado a tu cuenta de personal. Te mostraremos el
          mismo mensaje para proteger la privacidad de las cuentas.
        </Typography.Paragraph>
        {acknowledged ? (
          <Alert
            className="cg-recovery-feedback"
            showIcon
            title="Si existe una cuenta de personal activa con ese correo, recibirás instrucciones para recuperar tu contraseña."
            type="success"
          />
        ) : null}
        {error ? (
          <Alert
            className="cg-recovery-feedback"
            description={`${recoveryRequestErrorMessage(error)}${retryDelay > 0 ? ` Espera ${retryDelay} segundos.` : ""}`}
            showIcon
            title="No se pudo completar la solicitud"
            type="error"
          />
        ) : null}
        <Form<RecoveryRequestValues>
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
          scrollToFirstError
        >
          <Form.Item
            label="Correo electrónico"
            name="email"
            rules={[
              { required: true, message: "Escribe tu correo electrónico." },
              { type: "email", message: "Escribe un correo válido." },
              { max: 254, message: "El correo es demasiado largo." },
            ]}
          >
            <Input
              autoComplete="email"
              disabled={pending}
              maxLength={254}
              type="email"
            />
          </Form.Item>
          <Button
            block
            disabled={retryDelay > 0 || pending}
            htmlType="submit"
            loading={pending}
            type="primary"
          >
            Enviar instrucciones
          </Button>
        </Form>
        <Link className="cg-recovery-link" to="/login">
          Volver a iniciar sesión
        </Link>
      </IdentityCard>
    </IdentityLayout>
  );
}
