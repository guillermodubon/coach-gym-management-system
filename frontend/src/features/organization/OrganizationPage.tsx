import { Button, Card, Descriptions, Tag, Typography } from "antd";
import { Link } from "react-router";
import { StatePanel } from "../../shared/ui/StatePanel";
import {
  organizationReadError,
  useOrganizationQuery,
} from "./organization-read";
import "./organization-pages.css";

function displayValue(value: string | null | undefined): string | undefined {
  return value?.trim() || undefined;
}

export function OrganizationPage() {
  const query = useOrganizationQuery();
  if (query.isPending) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Organización
        </Typography.Title>
        <StatePanel
          variant="loading"
          title="Cargando organización"
          description="Consultando los datos disponibles para tu sesión."
        />
      </section>
    );
  }

  if (query.isError || !query.data) {
    return (
      <section aria-labelledby="main-heading" className="cg-organization-page">
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Organización
        </Typography.Title>
        <StatePanel
          action={
            <Button onClick={() => void query.refetch()}>Reintentar</Button>
          }
          description={organizationReadError(query.error)}
          title="No se pudo consultar la organización"
          variant="error"
        />
      </section>
    );
  }

  const organization = query.data;
  const items = [
    { key: "code", label: "Código", children: organization.code },
    {
      key: "brand",
      label: "Nombre comercial",
      children: organization.brandName,
    },
    ...(displayValue(organization.legalName)
      ? [
          {
            key: "legal",
            label: "Razón social",
            children: organization.legalName,
          },
        ]
      : []),
    ...(displayValue(organization.supportEmail)
      ? [
          {
            key: "email",
            label: "Correo de soporte",
            children: organization.supportEmail,
          },
        ]
      : []),
    ...(displayValue(organization.supportPhone)
      ? [
          {
            key: "phone",
            label: "Teléfono de soporte",
            children: organization.supportPhone,
          },
        ]
      : []),
    ...(displayValue(organization.defaultTimezone)
      ? [
          {
            key: "timezone",
            label: "Zona horaria predeterminada",
            children: organization.defaultTimezone,
          },
        ]
      : []),
    {
      key: "currency",
      label: "Moneda predeterminada",
      children: organization.defaultCurrency,
    },
    {
      key: "status",
      label: "Estado",
      children: (
        <Tag className="cg-organization-status" color="default">
          {organization.status === "ACTIVE" ? "Activa" : "Inactiva"}
        </Tag>
      ),
    },
  ];

  return (
    <section aria-labelledby="main-heading" className="cg-organization-page">
      <div className="cg-organization-heading">
        <div>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Organización
          </Typography.Title>
          <Typography.Paragraph>
            Consulta la información de la organización disponible para tu
            cuenta.
          </Typography.Paragraph>
        </div>
        <Link
          className="cg-organization-link cg-organization-link--primary"
          to="/sucursales"
        >
          Consultar sucursales
        </Link>
      </div>
      <Card className="cg-organization-card" variant="outlined">
        <Descriptions
          column={{ xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 3 }}
          items={items}
          title={organization.brandName}
        />
      </Card>
      {query.isFetching ? (
        <span
          aria-live="polite"
          className="cg-organization-refresh"
          role="status"
        >
          Actualizando información…
        </span>
      ) : null}
    </section>
  );
}
