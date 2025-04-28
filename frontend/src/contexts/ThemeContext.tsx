import React, { createContext, useContext, useState, useEffect } from 'react';

type Theme = 'default' | 'dark';

interface ThemeContextType {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => {
    // Initialize from localStorage when component mounts
    const savedTheme = localStorage.getItem('app-theme');
    return (savedTheme === 'default' || savedTheme === 'dark') ? savedTheme : 'default';
  });

  // Update localStorage and apply theme when theme changes
  useEffect(() => {
    localStorage.setItem('app-theme', theme);
    
    // Apply theme class to body
    document.body.classList.remove('theme-default', 'theme-dark');
    document.body.classList.add(`theme-${theme}`);
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
}
