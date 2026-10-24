import { createContext, useContext, useState } from "react";
import type { PropsWithChildren } from "react";
import { createApiClient } from "./client";
import { createIdentityApi } from "./identity";
import { createProfileApi } from "./profile";
import { createPublicIdentityApi } from "./public-identity";

export interface ApiServices {
  apiClient: ReturnType<typeof createApiClient>;
  identityApi: ReturnType<typeof createIdentityApi>;
  profileApi: ReturnType<typeof createProfileApi>;
  publicIdentityApi: ReturnType<typeof createPublicIdentityApi>;
}

const ApiServicesContext = createContext<ApiServices | null>(null);

export function ApiServicesProvider({ children }: PropsWithChildren) {
  const [services] = useState<ApiServices>(() => {
    const apiClient = createApiClient();
    return {
      apiClient,
      identityApi: createIdentityApi(apiClient),
      profileApi: createProfileApi(apiClient),
      publicIdentityApi: createPublicIdentityApi(apiClient),
    };
  });

  return (
    <ApiServicesContext.Provider value={services}>
      {children}
    </ApiServicesContext.Provider>
  );
}

export function useApiServices(): ApiServices {
  const services = useContext(ApiServicesContext);
  if (!services) {
    throw new Error("API services require ApiServicesProvider.");
  }
  return services;
}
