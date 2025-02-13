import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { LoginRequest } from '@/contexts/auth/types';

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, startDiscordOAuth, error } = useAuth();
  const [isLoading, setIsLoading] = useState(false);

  const handleDiscordLogin = async () => {
    try {
      await startDiscordOAuth();
    } catch (error) {
      console.error('Discord login failed:', error);
    }
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsLoading(true);
    
    const formData = new FormData(e.currentTarget);
    const credentials: LoginRequest = {
      username: formData.get('username') as string,
      password: formData.get('password') as string,
    };

    try {
      await login(credentials);
      const returnUrl = location.state?.returnUrl || '/dashboard';
      navigate(returnUrl);
    } catch (error) {
      console.error('Login failed:', error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h1>Login</h1>
        {error && <div className="auth-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <input type="text" name="username" placeholder="Username" required />
          <input type="password" name="password" placeholder="Password" required />
          <button type="submit" disabled={isLoading}>
            {isLoading ? 'Logging in...' : 'Login'}
          </button>
        </form>
        <button onClick={handleDiscordLogin}>
          Login with Discord
        </button>
      </div>
    </div>
  );
} 