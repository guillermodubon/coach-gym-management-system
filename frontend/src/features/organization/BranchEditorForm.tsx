import { Form, Input } from "antd";
import type {
  BranchEditorErrors,
  BranchEditorField,
  BranchEditorMode,
  BranchEditorValues,
} from "./branch-editor-validation";

interface BranchEditorFormProps {
  mode: BranchEditorMode;
  values: BranchEditorValues;
  errors: BranchEditorErrors;
  disabled: boolean;
  showErrors: boolean;
  onFieldChange(field: BranchEditorField, value: string): void;
  onSubmit(): void;
}

const fields: readonly {
  name: Exclude<BranchEditorField, "code">;
  label: string;
  limit: number;
  autocomplete: string;
  inputMode?: "email" | "tel" | "text";
  fullWidth?: boolean;
}[] = [
  {
    name: "name",
    label: "Nombre de la sucursal",
    limit: 160,
    autocomplete: "organization",
  },
  {
    name: "addressLine1",
    label: "Dirección, línea 1",
    limit: 200,
    autocomplete: "address-line1",
  },
  {
    name: "addressLine2",
    label: "Dirección, línea 2",
    limit: 200,
    autocomplete: "address-line2",
  },
  {
    name: "city",
    label: "Ciudad",
    limit: 120,
    autocomplete: "address-level2",
  },
  {
    name: "stateOrDepartment",
    label: "Departamento o estado",
    limit: 120,
    autocomplete: "address-level1",
  },
  {
    name: "postalCode",
    label: "Código postal",
    limit: 32,
    autocomplete: "postal-code",
  },
  {
    name: "countryCode",
    label: "Código de país",
    limit: 2,
    autocomplete: "country",
  },
  {
    name: "phone",
    label: "Teléfono",
    limit: 32,
    autocomplete: "tel",
    inputMode: "tel",
  },
  {
    name: "email",
    label: "Correo electrónico de contacto",
    limit: 254,
    autocomplete: "email",
    inputMode: "email",
  },
  {
    name: "timezone",
    label: "Zona horaria IANA",
    limit: 64,
    autocomplete: "off",
    fullWidth: true,
  },
];

export function BranchEditorForm({
  mode,
  values,
  errors,
  disabled,
  showErrors,
  onFieldChange,
  onSubmit,
}: BranchEditorFormProps) {
  return (
    <Form
      aria-label="Datos de la sucursal"
      autoComplete="off"
      className="cg-branch-editor-form"
      id="branch-editor-form"
      layout="vertical"
      noValidate
      onFinish={onSubmit}
    >
      {mode === "create" ? (
        <div className="cg-branch-editor-fields">
          <Form.Item
            htmlFor="code"
            label="Código de la sucursal"
            required
            validateStatus={
              errors.code && (showErrors || values.code.length > 0)
                ? "error"
                : ""
            }
            help={
              showErrors || values.code.length > 0 ? errors.code : undefined
            }
          >
            <Input
              aria-invalid={Boolean(errors.code && (showErrors || values.code))}
              aria-required="true"
              autoComplete="off"
              disabled={disabled}
              id="code"
              maxLength={32}
              onChange={(event) => onFieldChange("code", event.target.value)}
              placeholder="Ej. CENTRAL"
              value={values.code}
            />
          </Form.Item>
        </div>
      ) : null}

      <div className="cg-branch-editor-fields">
        {fields.map((field) => {
          const error =
            showErrors || values[field.name].length > 0
              ? errors[field.name]
              : undefined;
          return (
            <Form.Item
              className={field.fullWidth ? "cg-branch-editor-field--full" : ""}
              htmlFor={field.name}
              key={field.name}
              label={field.label}
              required={
                field.name === "name" ||
                (field.name === "timezone" && mode === "update")
              }
              validateStatus={error ? "error" : ""}
              help={error}
            >
              <Input
                aria-invalid={Boolean(error)}
                aria-required={
                  field.name === "name" ||
                  (field.name === "timezone" && mode === "update")
                }
                autoComplete={field.autocomplete}
                disabled={disabled}
                id={field.name}
                inputMode={field.inputMode}
                maxLength={field.limit}
                onChange={(event) =>
                  onFieldChange(field.name, event.target.value)
                }
                type="text"
                value={values[field.name]}
              />
            </Form.Item>
          );
        })}
      </div>

      <p className="cg-branch-editor-timezone-note">
        {mode === "create"
          ? "La zona horaria es opcional al crear. Si queda vacía, el servidor usará la predeterminada de la organización."
          : "La zona horaria es obligatoria al editar. El servidor valida que sea una zona IANA reconocida."}
      </p>
    </Form>
  );
}
