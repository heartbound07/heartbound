import { Link } from 'react-router-dom';

interface NavigationProps {
  className?: string;
}

export function Navigation({ className = '' }: NavigationProps) {
  return (
    <nav className={`relative z-10 p-4 ${className}`}>
      <ul className="flex justify-center gap-8 text-white font-medium">
        <li>
          <Link
            to="/competitive"
            className="group relative inline-block text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            competitive
          </Link>
        </li>
        <li>
          <Link
            to="/find-a-duo"
            className="group relative inline-block text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            find a duo
          </Link>
        </li>
        <li>
          <Link
            to="/discord"
            className="group relative inline-block text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            discord
          </Link>
        </li>
        <li>
          <Link
            to="/how-it-works"
            className="group relative inline-block text-white visited:text-white transition-all duration-300 transform hover:scale-105 hover:bg-white/20 px-2 py-1 rounded"
          >
            how it works
          </Link>
        </li>
      </ul>
    </nav>
  );
} 