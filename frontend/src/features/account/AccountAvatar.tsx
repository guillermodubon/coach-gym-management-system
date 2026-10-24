import { useEffect, useState } from "react";
import { Avatar } from "antd";

export interface AccountAvatarProps {
  className: string;
  initials: string;
  photoUrl: string | null;
  size: number;
}

export function AccountAvatar({
  className,
  initials,
  photoUrl,
  size,
}: AccountAvatarProps) {
  const [failedPhotoUrl, setFailedPhotoUrl] = useState<string | null>(null);
  const visiblePhotoUrl =
    photoUrl && photoUrl !== failedPhotoUrl ? photoUrl : undefined;

  useEffect(() => {
    setFailedPhotoUrl(null);
  }, [photoUrl]);

  return (
    <Avatar
      aria-hidden="true"
      className={className}
      {...(visiblePhotoUrl
        ? {
            onError: () => {
              setFailedPhotoUrl(visiblePhotoUrl);
              return true;
            },
            src: visiblePhotoUrl,
          }
        : { "data-initials": initials })}
      size={size}
    />
  );
}
