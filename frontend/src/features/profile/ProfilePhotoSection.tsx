import { useState } from "react";
import {
  DeleteOutlined,
  ReloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { Alert, Button, Modal, Typography, Upload } from "antd";
import type { UploadProps } from "antd";
import { useAccountProfile } from "./AccountProfileProvider";
import { useProfilePhoto } from "./ProfilePhotoProvider";

const acceptedPhotoTypes =
  ".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp";

export function ProfilePhotoSection() {
  const account = useAccountProfile();
  const photo = useProfilePhoto();
  const [confirmRemoval, setConfirmRemoval] = useState(false);

  const beforeUpload: UploadProps["beforeUpload"] = (file) => {
    photo.selectFile(file);
    return Upload.LIST_IGNORE;
  };

  return (
    <section
      aria-labelledby="profile-photo-heading"
      className="cg-profile-photo"
    >
      <div className="cg-profile-photo-copy">
        <Typography.Title id="profile-photo-heading" level={3}>
          Foto de perfil
        </Typography.Title>
        <Typography.Paragraph>
          Elige una imagen JPG, PNG o WebP de hasta 5 MiB. Se mostrará en tu
          perfil y en el menú de cuenta.
        </Typography.Paragraph>
      </div>

      {photo.loading ? (
        <Typography.Text aria-live="polite" role="status" type="secondary">
          Cargando foto privada…
        </Typography.Text>
      ) : null}

      {photo.readError ? (
        <Alert
          action={
            <Button
              icon={<ReloadOutlined aria-hidden="true" />}
              onClick={photo.retryDownload}
              type="link"
            >
              Reintentar carga
            </Button>
          }
          className="cg-profile-photo-alert"
          showIcon
          title="No se pudo mostrar la foto actual."
          description={
            photo.readError.status === 404
              ? "La foto privada ya no está disponible. Puedes elegir otra para reemplazarla."
              : photo.readError.message
          }
          type="warning"
        />
      ) : null}

      {photo.validationError ? (
        <Alert
          className="cg-profile-photo-alert"
          role="alert"
          showIcon
          title={photo.validationError}
          type="error"
        />
      ) : null}

      {photo.writeError ? (
        <Alert
          action={
            photo.reconciliationRequired ? (
              <Button
                disabled={photo.saving}
                onClick={() => void photo.reconcile()}
                type="link"
              >
                Actualizar perfil y foto
              </Button>
            ) : null
          }
          className="cg-profile-photo-alert"
          showIcon
          title={
            photo.reconciliationRequired
              ? "Actualiza el perfil antes de volver a guardar."
              : "No se pudo confirmar el cambio de foto."
          }
          description={photo.writeError.message}
          type="error"
        />
      ) : null}

      {photo.notice ? (
        <Alert
          className="cg-profile-photo-alert"
          role="status"
          showIcon
          title={photo.notice}
          type="success"
        />
      ) : null}

      {photo.dirty ? (
        <div className="cg-profile-photo-selection">
          <Typography.Text
            className="cg-profile-photo-file"
            {...(photo.selectedFileName
              ? { title: photo.selectedFileName }
              : {})}
          >
            Imagen seleccionada: {photo.selectedFileName}
            {photo.selectedFileSize !== null
              ? ` (${(photo.selectedFileSize / (1024 * 1024)).toFixed(1)} MiB)`
              : ""}
          </Typography.Text>
          <div className="cg-profile-photo-actions">
            <Upload
              accept={acceptedPhotoTypes}
              beforeUpload={beforeUpload}
              disabled={!photo.canSelect}
              maxCount={1}
              multiple={false}
              showUploadList={false}
            >
              <Button
                disabled={!photo.canSelect}
                icon={<UploadOutlined aria-hidden="true" />}
              >
                Elegir otra imagen
              </Button>
            </Upload>
            <Button disabled={photo.saving} onClick={photo.discardSelection}>
              Cancelar selección
            </Button>
            <Button
              disabled={!photo.canUpload}
              loading={photo.saving}
              onClick={() => void photo.upload()}
              type="primary"
            >
              Guardar foto
            </Button>
          </div>
        </div>
      ) : (
        <div className="cg-profile-photo-actions">
          <Upload
            accept={acceptedPhotoTypes}
            beforeUpload={beforeUpload}
            disabled={!photo.canSelect}
            maxCount={1}
            multiple={false}
            showUploadList={false}
          >
            <Button
              disabled={!photo.canSelect}
              icon={<UploadOutlined aria-hidden="true" />}
            >
              Seleccionar foto
            </Button>
          </Upload>
          {photo.photoPresent ? (
            <Button
              danger
              disabled={!photo.canRemove}
              icon={<DeleteOutlined aria-hidden="true" />}
              onClick={() => setConfirmRemoval(true)}
            >
              Eliminar foto
            </Button>
          ) : null}
        </div>
      )}

      {photo.reconciliationRequired ? (
        <Typography.Text type="secondary">
          No se enviará otra escritura hasta confirmar la versión actual del
          perfil.
        </Typography.Text>
      ) : null}

      {account.dirty ? (
        <Typography.Text type="secondary">
          Guarda o descarta los cambios de nombre antes de cambiar la foto.
        </Typography.Text>
      ) : null}

      <Modal
        cancelText="Conservar foto"
        confirmLoading={photo.saving}
        okButtonProps={{ danger: true }}
        okText="Eliminar foto"
        onCancel={() => setConfirmRemoval(false)}
        onOk={async () => {
          if (await photo.remove()) setConfirmRemoval(false);
        }}
        open={confirmRemoval}
        title="Eliminar foto de perfil"
      >
        <Typography.Paragraph>
          La foto dejará de mostrarse en tu perfil y en el menú de cuenta.
        </Typography.Paragraph>
      </Modal>
    </section>
  );
}
