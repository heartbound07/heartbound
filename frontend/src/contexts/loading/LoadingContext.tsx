import { createContext, useContext, useReducer, ReactNode } from 'react';

interface LoadingState {
  isLoading: boolean;
  loadingMessage?: string;
}

type LoadingAction = 
  | { type: 'START_LOADING'; message?: string }
  | { type: 'STOP_LOADING' };

interface LoadingContextType extends LoadingState {
  startLoading: (message?: string) => void;
  stopLoading: () => void;
}

const LoadingContext = createContext<LoadingContextType | undefined>(undefined);

function loadingReducer(state: LoadingState, action: LoadingAction): LoadingState {
  switch (action.type) {
    case 'START_LOADING':
      return {
        isLoading: true,
        loadingMessage: action.message,
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

  const startLoading = (message?: string) => {
    dispatch({ type: 'START_LOADING', message });
  };

  const stopLoading = () => {
    dispatch({ type: 'STOP_LOADING' });
  };

  return (
    <LoadingContext.Provider value={{ ...state, startLoading, stopLoading }}>
      {children}
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