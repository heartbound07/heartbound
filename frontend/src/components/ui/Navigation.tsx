import { Link } from 'react-router-dom';

interface NavigationProps {
  className?: string;
  onSectionClick?: (section: string) => void;
  isLandingPage?: boolean;
}

export function Navigation({ className = '', onSectionClick, isLandingPage = false }: NavigationProps) {
  const handleClick = (section: string) => {
    if (isLandingPage && onSectionClick) {
      onSectionClick(section);
      return;
    }
  };

  return (
    <nav className={`relative z-10 p-2 sm:p-3 md:p-4 ${className}`}>
      <ul className="flex justify-center gap-2 sm:gap-4 md:gap-6 lg:gap-8 text-white font-medium overflow-x-auto">
        <li>
          {isLandingPage ? (
            <button
              onClick={() => handleClick('competitive')}
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              competitive
            </button>
          ) : (
            <Link
              to="/competitive"
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              competitive
            </Link>
          )}
        </li>
        <li>
          {isLandingPage ? (
            <button
              onClick={() => handleClick('find-a-duo')}
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              find a duo
            </button>
          ) : (
            <Link
              to="/find-a-duo"
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              find a duo
            </Link>
          )}
        </li>
        <li>
          {isLandingPage ? (
            <button
              onClick={() => handleClick('discord')}
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              discord
            </button>
          ) : (
            <Link
              to="/discord"
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              discord
            </Link>
          )}
        </li>
        <li>
          {isLandingPage ? (
            <button
              onClick={() => handleClick('how-it-works')}
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              how it works
            </button>
          ) : (
            <Link
              to="/how-it-works"
              className="group relative inline-block text-sm sm:text-base md:text-md text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-1.5 sm:px-2 py-1 rounded whitespace-nowrap"
            >
              how it works
            </Link>
          )}
        </li>
      </ul>
    </nav>
  );
} 