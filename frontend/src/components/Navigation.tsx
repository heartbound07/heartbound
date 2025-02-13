import { Link } from 'react-router-dom';

export function Navigation() {
  return (
    <nav className="relative z-10 p-4">
      <ul className="flex justify-center gap-8 text-white/90 font-medium">
        <li>
          <Link to="/competitive" className="hover:text-white transition-colors">
            competitive
          </Link>
        </li>
        <li>
          <Link to="/find-a-duo" className="hover:text-white transition-colors">
            find a duo
          </Link>
        </li>
        <li>
          <Link to="/discord" className="hover:text-white transition-colors">
            discord
          </Link>
        </li>
        <li>
          <Link to="/how-it-works" className="hover:text-white transition-colors">
            how it works
          </Link>
        </li>
      </ul>
    </nav>
  );
} 