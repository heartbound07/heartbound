import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { LFGPost, LFGService } from '@/lib/api/lfg';

interface PostCardProps {
  post: LFGPost;
}

export function PostCard({ post }: PostCardProps) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleJoin = async () => {
    if (!user) {
      navigate('/login');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await LFGService.joinPost(post.id);
      navigate(`/lfg/${post.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to join group');
    } finally {
      setIsLoading(false);
    }
  };

  const isExpired = new Date(post.expiresAt) < new Date();
  const isOwner = user?.id === post.userId;

  return (
    <div className="lfg-card">
      <div className="lfg-card-header">
        <h3 className="lfg-card-title">{post.title}</h3>
        <span className={`lfg-status ${post.status}`}>
          {isExpired ? 'Expired' : post.status}
        </span>
      </div>

      <div className="lfg-card-game">
        <img 
          src={`/games/${post.game.toLowerCase()}.png`} 
          alt={post.game}
          className="game-icon"
        />
        <span>{post.game}</span>
      </div>

      <p className="lfg-card-description">{post.description}</p>

      <div className="lfg-card-requirements">
        <div className="requirement">
          <span className="label">Region:</span>
          <span>{post.requirements.region}</span>
        </div>
        {post.requirements.rank && (
          <div className="requirement">
            <span className="label">Rank:</span>
            <span>{post.requirements.rank}</span>
          </div>
        )}
        <div className="requirement">
          <span className="label">Languages:</span>
          <span>{post.requirements.language.join(', ')}</span>
        </div>
        {post.requirements.voiceChat && (
          <div className="requirement voice-chat">
            <span>Voice Chat Required</span>
          </div>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="lfg-card-actions">
        {!isOwner && !isExpired && post.status === 'open' && (
          <button
            onClick={handleJoin}
            disabled={isLoading}
            className="join-button"
          >
            {isLoading ? 'Joining...' : 'Join Group'}
          </button>
        )}
        {isOwner && (
          <button
            onClick={() => navigate(`/lfg/${post.id}/edit`)}
            className="edit-button"
          >
            Edit Post
          </button>
        )}
      </div>
    </div>
  );
} 