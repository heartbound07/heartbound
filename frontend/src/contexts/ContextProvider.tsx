import React, { createContext, useContext, useState, useEffect } from 'react';

interface StateContextType {
  activeMenu: boolean;
  setActiveMenu: React.Dispatch<React.SetStateAction<boolean>>;
  screenSize: number;
  currentColor: string;
  setCurrentColor: React.Dispatch<React.SetStateAction<string>>;
}

const StateContext = createContext<StateContextType | undefined>(undefined);

export const ContextProvider = ({ children }: { children: React.ReactNode }) => {
  const [activeMenu, setActiveMenu] = useState(true);
  const [screenSize, setScreenSize] = useState(window.innerWidth);
  const [currentColor, setCurrentColor] = useState('#03C9D7');

  useEffect(() => {
    const handleResize = () => setScreenSize(window.innerWidth);
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return (
    <StateContext.Provider value={{ activeMenu, setActiveMenu, screenSize, currentColor, setCurrentColor }}>
      {children}
    </StateContext.Provider>
  );
};

export const useStateContext = () => {
  const context = useContext(StateContext);
  if (context === undefined) {
    throw new Error('useStateContext must be used within a ContextProvider');
  }
  return context;
}; 