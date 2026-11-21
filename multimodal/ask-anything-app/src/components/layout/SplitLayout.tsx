/**
 * Two-column split layout component.
 */
import type { ReactNode } from "react";

interface SplitLayoutProps {
  left: ReactNode;
  right: ReactNode;
}

export function SplitLayout({ left, right }: SplitLayoutProps) {
  return (
    <div className="flex h-screen w-screen">
      <div className="w-1/2 h-full border-r border-gray-200">{left}</div>
      <div className="w-1/2 h-full">{right}</div>
    </div>
  );
}
