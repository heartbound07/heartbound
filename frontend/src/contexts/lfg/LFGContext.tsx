import { createContext, useContext, useReducer, ReactNode } from 'react';
import { LFGPost, FilterParams } from '@/lib/api/lfg';

interface LFGState {
  posts: LFGPost[];
  filters: FilterParams;
  isLoading: boolean;
  error: string | null;
  totalPages: number;
  currentPage: number;
}

type LFGAction =
  | { type: 'SET_POSTS'; payload: LFGPost[] }
  | { type: 'SET_FILTERS'; payload: FilterParams }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_PAGINATION'; payload: { totalPages: number; currentPage: number } };

const initialState: LFGState = {
  posts: [],
  filters: {},
  isLoading: false,
  error: null,
  totalPages: 0,
  currentPage: 1,
};

const LFGContext = createContext<{
  state: LFGState;
  dispatch: React.Dispatch<LFGAction>;
} | undefined>(undefined);

function lfgReducer(state: LFGState, action: LFGAction): LFGState {
  switch (action.type) {
    case 'SET_POSTS':
      return { ...state, posts: action.payload };
    case 'SET_FILTERS':
      return { ...state, filters: action.payload };
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    case 'SET_ERROR':
      return { ...state, error: action.payload };
    case 'SET_PAGINATION':
      return {
        ...state,
        totalPages: action.payload.totalPages,
        currentPage: action.payload.currentPage,
      };
    default:
      return state;
  }
}

export function LFGProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(lfgReducer, initialState);

  return (
    <LFGContext.Provider value={{ state, dispatch }}>
      {children}
    </LFGContext.Provider>
  );
}

export function useLFG() {
  const context = useContext(LFGContext);
  if (context === undefined) {
    throw new Error('useLFG must be used within a LFGProvider');
  }
  return context;
} 