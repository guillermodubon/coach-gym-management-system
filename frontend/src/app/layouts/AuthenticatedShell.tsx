import { Outlet, useLocation } from "react-router";
import { AppLayout } from "./AppLayout";
import { AccountMenu } from "../../features/account/AccountMenu";
import { BranchContextControl } from "../../features/branch-context/BranchContextControl";
import { useProfilePhoto } from "../../features/profile/ProfilePhotoProvider";
import { useAuth } from "../../features/auth/useAuth";

function titleForPath(pathname: string): string {
  if (pathname === "/organizacion") return "Organización";
  if (pathname === "/sucursales/nueva") return "Nueva sucursal";
  if (pathname.endsWith("/editar")) return "Editar sucursal";
  if (pathname === "/sucursales") return "Sucursales";
  if (pathname.startsWith("/sucursales/")) return "Detalle de sucursal";
  if (pathname === "/mi-perfil") return "Mi perfil";
  if (pathname === "/mi-cuenta") return "Ajustes de mi cuenta";
  return "Inicio";
}

export function AuthenticatedShell() {
  const auth = useAuth();
  const photo = useProfilePhoto();
  const location = useLocation();
  if (auth.state.kind !== "authenticated") return null;

  const { user } = auth.state;
  return (
    <AppLayout
      accountSlot={
        <AccountMenu
          onLogout={() => void auth.logout()}
          photoUrl={photo.photoUrl}
          user={user}
        />
      }
      contextSlot={<BranchContextControl mode="header" />}
      title={titleForPath(location.pathname)}
    >
      <Outlet />
    </AppLayout>
  );
}
