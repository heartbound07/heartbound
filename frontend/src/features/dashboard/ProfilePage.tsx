import { useState } from 'react';
import { useAuth } from '@/contexts/auth';
import { UserInfo } from '@/contexts/auth/types';

interface ProfileFormData extends Omit<UserInfo, 'id'> {
  newPassword?: string;
  confirmPassword?: string;
}

export function ProfilePage() {
  const { user, error: authError } = useAuth();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [formData, setFormData] = useState<ProfileFormData>({
    username: user?.username || '',
    email: user?.email || '',
    avatar: user?.avatar || '',
  });

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);
    setSuccess(null);

    try {
      // Validate passwords if provided
      if (formData.newPassword) {
        if (formData.newPassword !== formData.confirmPassword) {
          throw new Error('Passwords do not match');
        }
        if (formData.newPassword.length < 8) {
          throw new Error('Password must be at least 8 characters');
        }
      }

      // Replace with actual API call
      const response = await fetch('/api/profile/update', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      });

      if (!response.ok) {
        throw new Error('Failed to update profile');
      }

      setSuccess('Profile updated successfully');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update profile');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="profile-container">
      <h1>Profile Settings</h1>
      
      {(error || authError) && (
        <div className="error-message">
          {error || authError}
        </div>
      )}
      
      {success && (
        <div className="success-message">
          {success}
        </div>
      )}

      <form onSubmit={handleSubmit} className="profile-form">
        <div className="form-group">
          <label htmlFor="username">Username</label>
          <input
            type="text"
            id="username"
            name="username"
            value={formData.username}
            onChange={handleInputChange}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input
            type="email"
            id="email"
            name="email"
            value={formData.email}
            onChange={handleInputChange}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="avatar">Avatar URL</label>
          <input
            type="url"
            id="avatar"
            name="avatar"
            value={formData.avatar}
            onChange={handleInputChange}
          />
        </div>

        <div className="form-group">
          <label htmlFor="newPassword">New Password (optional)</label>
          <input
            type="password"
            id="newPassword"
            name="newPassword"
            value={formData.newPassword || ''}
            onChange={handleInputChange}
            minLength={8}
          />
        </div>

        <div className="form-group">
          <label htmlFor="confirmPassword">Confirm Password</label>
          <input
            type="password"
            id="confirmPassword"
            name="confirmPassword"
            value={formData.confirmPassword || ''}
            onChange={handleInputChange}
            minLength={8}
          />
        </div>

        <button 
          type="submit" 
          className="submit-button"
          disabled={isLoading}
        >
          {isLoading ? 'Updating...' : 'Update Profile'}
        </button>
      </form>

      <div className="connected-accounts">
        <h2>Connected Accounts</h2>
        <div className="account-item">
          <span>Discord</span>
          {user?.discordId ? (
            <span className="connected">Connected</span>
          ) : (
            <button className="connect-button">Connect Discord</button>
          )}
        </div>
      </div>
    </div>
  );
} 