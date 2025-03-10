import { createContext, useContext, useReducer, ReactNode } from 'react';
import { LoadingSpinner } from '@/components/ui/LoadingSpinner';

interface LoadingState {
  isLoading: boolean;
  loadingMessage?: string;
  loadingDescription?: string;
}

type LoadingAction = 
  | { type: 'START_LOADING'; message?: string; description?: string }
  | { type: 'STOP_LOADING' };

interface LoadingContextType extends LoadingState {
  startLoading: (message?: string, description?: string) => void;
  stopLoading: () => void;
}

const LoadingContext = createContext<LoadingContextType | undefined>(undefined);

function loadingReducer(state: LoadingState, action: LoadingAction): LoadingState {
  switch (action.type) {
    case 'START_LOADING':
      return {
        isLoading: true,
        loadingMessage: action.message,
        loadingDescription: action.description,
      };
    case 'STOP_LOADING':
      return {
        isLoading: false,
      };
    default:
      return state;
  }
}

export function LoadingProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(loadingReducer, {
    isLoading: false,
  });

  const startLoading = (message?: string, description?: string) => {
    dispatch({ type: 'START_LOADING', message, description });
  };

  const stopLoading = () => {
    dispatch({ type: 'STOP_LOADING' });
  };

  return (
    <LoadingContext.Provider value={{ ...state, startLoading, stopLoading }}>
      {state.isLoading ? (
        <LoadingSpinner
          title={state.loadingMessage || "Loading..."}
          description={state.loadingDescription}
          fullScreen={true}
          theme="valorant"
        />
      ) : (
        children
      )}
    </LoadingContext.Provider>
  );
}

export function useLoading() {
  const context = useContext(LoadingContext);
  if (context === undefined) {
    throw new Error('useLoading must be used within a LoadingProvider');
  }
  return context;
} 