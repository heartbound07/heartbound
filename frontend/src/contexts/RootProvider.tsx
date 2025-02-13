import { ReactNode } from 'react';
import { AuthProvider } from './auth/AuthProvider';
import { LoadingProvider } from './loading/LoadingContext';

interface RootProviderProps {
  children: ReactNode;
}

export function RootProvider({ children }: RootProviderProps) {
  return (
    <LoadingProvider>
      <AuthProvider>
        {children}
      </AuthProvider>
    </LoadingProvider>
  );
} 