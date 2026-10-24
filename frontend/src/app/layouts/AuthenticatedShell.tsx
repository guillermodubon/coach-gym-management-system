import { Outlet, useLocation } from "react-router";
import { AppLayout } from "./AppLayout";
import { AccountMenu } from "../../features/account/AccountMenu";
import { BranchContextSummary } from "../../features/home/BranchContextSummary";
import { useProfilePhoto } from "../../features/profile/ProfilePhotoProvider";
import { useAuth } from "../../features/auth/useAuth";

function titleForPath(pathname: string): string {
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
      contextSlot={<BranchContextSummary mode="header" user={user} />}
      title={titleForPath(location.pathname)}
    >
      <Outlet />
    </AppLayout>
  );
}
