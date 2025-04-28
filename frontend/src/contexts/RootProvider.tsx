import { ReactNode } from 'react';
import { AuthProvider } from './auth/AuthProvider';
import { LoadingProvider } from './loading/LoadingContext';
import { ThemeProvider } from './ThemeContext';

interface RootProviderProps {
  children: ReactNode;
}

export function RootProvider({ children }: RootProviderProps) {
  return (
    <LoadingProvider>
      <AuthProvider>
        <ThemeProvider>
          {children}
        </ThemeProvider>
      </AuthProvider>
    </LoadingProvider>
  );
} 