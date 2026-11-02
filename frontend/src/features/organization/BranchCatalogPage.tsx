import { useEffect } from "react";
import {
  Button,
  Card,
  Form,
  Input,
  Pagination,
  Select,
  Table,
  Tag,
  Typography,
} from "antd";
import type { TableColumnsType } from "antd";
import { Link, useLocation, useSearchParams } from "react-router";
import { StatePanel } from "../../shared/ui/StatePanel";
import { useAuth } from "../auth/useAuth";
import type {
  BranchRecord,
  BranchSortDirection,
  BranchSortField,
} from "../../shared/api/organization-contracts";
import {
  branchCatalogSearch,
  normalizeBranchFilter,
  parseBranchCatalogSearch,
  type BranchCatalogValues,
} from "./branch-catalog-query";
import {
  organizationReadError,
  useBranchCatalogQuery,
} from "./organization-read";
import { canManageOrganizationBranches } from "./branch-editor-policy";
import "./organization-pages.css";

interface CatalogFormValues {
  status?: "ACTIVE" | "INACTIVE";
  search?: string;
  city?: string;
  countryCode?: string;
  size?: number;
  sort?: BranchSortField;
  direction?: BranchSortDirection;
}

function locationLabel(branch: BranchRecord): string {
  return (
    [branch.city, branch.stateOrDepartment, branch.countryCode]
      .filter((part) => part?.trim())
      .join(", ") || "Sin ubicación registrada"
  );
}

export function BranchCatalogPage() {
  const auth = useAuth();
  const canManage = canManageOrganizationBranches(auth);
  const location = useLocation();
  const [, setSearchParams] = useSearchParams();
  const [form] = Form.useForm<CatalogFormValues>();
  const parsed = parseBranchCatalogSearch(location.search);
  const query = useBranchCatalogQuery(
    parsed.valid ? parsed.query : {},
    parsed.valid,
  );

  useEffect(() => {
    if (!parsed.valid) {
      form.resetFields();
      form.setFieldsValue({
        size: 25,
        sort: "CODE",
        direction: "ASC",
      });
      return;
    }
    form.setFieldsValue(parsed.query);
  }, [form, location.search]);

  function updateUrl(values: BranchCatalogValues, replace = false) {
    const serialized = branchCatalogSearch(values);
    setSearchParams(new URLSearchParams(serialized.slice(1)), { replace });
  }

  function applyFilters(values: CatalogFormValues) {
    const search = normalizeBranchFilter(values.search);
    const city = normalizeBranchFilter(values.city);
    const countryCode = normalizeBranchFilter(
      values.countryCode,
    )?.toUpperCase();
    updateUrl({
      page: 0,
      size: values.size ?? 25,
      sort: values.sort ?? "CODE",
      direction: values.direction ?? "ASC",
      ...(values.status ? { status: values.status } : {}),
      ...(search ? { search } : {}),
      ...(city ? { city } : {}),
      ...(countryCode ? { countryCode } : {}),
    });
  }

  function changePage(page: number, pageSize: number) {
    if (!parsed.valid) return;
    updateUrl({ ...parsed.query, page: page - 1, size: pageSize });
  }

  const columns: TableColumnsType<BranchRecord> = [
    {
      title: "Código",
      dataIndex: "code",
      key: "code",
      width: 130,
      render: (code: string) => <strong>{code}</strong>,
    },
    {
      title: "Sucursal",
      dataIndex: "name",
      key: "name",
      render: (_name: string, branch) => (
        <Link
          state={{ returnTo: `${location.pathname}${location.search}` }}
          to={`/sucursales/${branch.id}`}
        >
          {branch.name}
        </Link>
      ),
    },
    {
      title: "Ubicación",
      key: "location",
      render: (_value: unknown, branch) => locationLabel(branch),
    },
    {
      title: "Estado",
      dataIndex: "status",
      key: "status",
      width: 130,
      render: (status: BranchRecord["status"]) => (
        <Tag color={status === "ACTIVE" ? "success" : "default"}>
          {status === "ACTIVE" ? "Activa" : "Inactiva"}
        </Tag>
      ),
    },
    {
      title: "Acción",
      key: "action",
      width: 140,
      render: (_value: unknown, branch) => (
        <Link
          aria-label={`Ver detalle de ${branch.name}`}
          state={{ returnTo: `${location.pathname}${location.search}` }}
          to={`/sucursales/${branch.id}`}
        >
          Ver detalle
        </Link>
      ),
    },
  ];

  return (
    <section aria-labelledby="main-heading" className="cg-organization-page">
      <div className="cg-organization-heading">
        <div>
          <Typography.Title id="main-heading" level={1} tabIndex={-1}>
            Sucursales
          </Typography.Title>
          <Typography.Paragraph>
            Consulta el catálogo de sucursales. Ver una ficha no concede
            permisos operativos sobre ella.
          </Typography.Paragraph>
        </div>
        <div className="cg-organization-heading-actions">
          {canManage ? (
            <Link
              className="cg-organization-link cg-organization-link--primary"
              to="/sucursales/nueva"
            >
              Nueva sucursal
            </Link>
          ) : null}
          <Link className="cg-organization-link" to="/organizacion">
            Ver organización
          </Link>
        </div>
      </div>

      <Card
        className="cg-branch-filter-card"
        title="Filtros"
        variant="outlined"
      >
        <Form<CatalogFormValues>
          form={form}
          initialValues={{ size: 25, sort: "CODE", direction: "ASC" }}
          layout="vertical"
          onFinish={applyFilters}
        >
          <div className="cg-branch-filter-grid">
            <Form.Item label="Buscar por código, nombre o ciudad" name="search">
              <Input autoComplete="off" maxLength={120} />
            </Form.Item>
            <Form.Item label="Ciudad" name="city">
              <Input autoComplete="off" maxLength={120} />
            </Form.Item>
            <Form.Item
              label="País"
              name="countryCode"
              rules={[
                {
                  len: 2,
                  message: "Usa el código de país de dos letras.",
                  validateTrigger: "onSubmit",
                },
                {
                  pattern: /^[a-zA-Z]{2}$/,
                  message: "Usa el código de país de dos letras.",
                  validateTrigger: "onSubmit",
                },
              ]}
            >
              <Input autoComplete="off" maxLength={2} />
            </Form.Item>
            <Form.Item label="Estado" name="status">
              <Select
                allowClear
                options={[
                  { label: "Activas", value: "ACTIVE" },
                  { label: "Inactivas", value: "INACTIVE" },
                ]}
                placeholder="Todos los estados"
              />
            </Form.Item>
            <Form.Item label="Ordenar por" name="sort">
              <Select
                options={[
                  { label: "Código", value: "CODE" },
                  { label: "Nombre", value: "NAME" },
                  { label: "Ciudad", value: "CITY" },
                  { label: "Fecha de creación", value: "CREATED_AT" },
                  { label: "Última actualización", value: "UPDATED_AT" },
                ]}
              />
            </Form.Item>
            <Form.Item label="Dirección" name="direction">
              <Select
                options={[
                  { label: "Ascendente", value: "ASC" },
                  { label: "Descendente", value: "DESC" },
                ]}
              />
            </Form.Item>
            <Form.Item label="Resultados por página" name="size">
              <Select
                options={[10, 25, 50, 100].map((size) => ({
                  label: String(size),
                  value: size,
                }))}
              />
            </Form.Item>
          </div>
          <div className="cg-branch-filter-actions">
            <Button htmlType="submit" type="primary">
              Aplicar filtros
            </Button>
            <Button
              onClick={() => {
                form.resetFields();
                form.setFieldsValue({
                  size: 25,
                  sort: "CODE",
                  direction: "ASC",
                });
                setSearchParams(new URLSearchParams());
              }}
            >
              Limpiar filtros
            </Button>
          </div>
        </Form>
      </Card>

      {!parsed.valid ? (
        <StatePanel
          action={
            <Button
              onClick={() => setSearchParams(new URLSearchParams())}
              type="primary"
            >
              Limpiar URL y filtros
            </Button>
          }
          description={parsed.message}
          title="La URL de filtros no es válida"
          variant="error"
        />
      ) : query.isPending ? (
        <StatePanel
          description="Consultando el catálogo con los filtros seleccionados."
          title="Cargando sucursales"
          variant="loading"
        />
      ) : query.isError || !query.data ? (
        <StatePanel
          action={
            <Button onClick={() => void query.refetch()}>Reintentar</Button>
          }
          description={organizationReadError(query.error)}
          title="No se pudo consultar el catálogo"
          variant="error"
        />
      ) : (
        <Card className="cg-branch-catalog-card" variant="outlined">
          {query.data.items.length === 0 ? (
            <StatePanel
              description="Prueba otros términos o limpia los filtros para consultar todo el catálogo."
              title="No hay sucursales que coincidan con estos filtros"
              variant="empty"
            />
          ) : (
            <Table<BranchRecord>
              aria-label="Catálogo de sucursales"
              columns={columns}
              dataSource={query.data.items}
              pagination={false}
              rowKey="id"
              scroll={{ x: 680 }}
              size="middle"
            />
          )}
          <div className="cg-branch-pagination">
            <span aria-live="polite" role="status">
              {query.data.totalElements} resultado
              {query.data.totalElements === 1 ? "" : "s"}
              {query.isFetching ? " · Actualizando…" : ""}
            </span>
            <Pagination
              current={query.data.page + 1}
              pageSize={query.data.size}
              pageSizeOptions={[10, 25, 50, 100]}
              showSizeChanger
              showTotal={(total, range) =>
                `${range[0]}–${range[1]} de ${total}`
              }
              total={query.data.totalElements}
              onChange={changePage}
            />
          </div>
        </Card>
      )}
    </section>
  );
}
