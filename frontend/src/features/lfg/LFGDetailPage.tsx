import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/auth';
import { LFGPost, LFGService } from '@/lib/api/lfg';

export function LFGDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [post, setPost] = useState<LFGPost | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchPost = async () => {
      if (!id) return;
      
      try {
        const data = await LFGService.getPost(id);
        setPost(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load post');
      } finally {
        setIsLoading(false);
      }
    };

    fetchPost();
  }, [id]);

  const handleDelete = async () => {
    if (!post || !window.confirm('Are you sure you want to delete this post?')) {
      return;
    }

    try {
      await LFGService.deletePost(post.id);
      navigate('/lfg');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete post');
    }
  };

  if (isLoading) {
    return <div className="lfg-loading">Loading post details...</div>;
  }

  if (error) {
    return <div className="lfg-error">{error}</div>;
  }

  if (!post) {
    return <div className="lfg-error">Post not found</div>;
  }

  const isOwner = user?.id === post.userId;
  const isExpired = new Date(post.expiresAt) < new Date();

  return (
    <div className="lfg-detail">
      <div className="lfg-detail-header">
        <h1>{post.title}</h1>
        <span className={`lfg-status ${post.status}`}>
          {isExpired ? 'Expired' : post.status}
        </span>
      </div>

      <div className="lfg-detail-content">
        <div className="lfg-detail-game">
          <img 
            src={`/games/${post.game.toLowerCase()}.png`} 
            alt={post.game}
            className="game-icon-large"
          />
          <h2>{post.game}</h2>
        </div>

        <div className="lfg-detail-description">
          <h3>Description</h3>
          <p>{post.description}</p>
        </div>

        <div className="lfg-detail-requirements">
          <h3>Requirements</h3>
          <div className="requirements-grid">
            <div className="requirement">
              <span className="label">Region</span>
              <span>{post.requirements.region}</span>
            </div>
            {post.requirements.rank && (
              <div className="requirement">
                <span className="label">Rank</span>
                <span>{post.requirements.rank}</span>
              </div>
            )}
            <div className="requirement">
              <span className="label">Languages</span>
              <span>{post.requirements.language.join(', ')}</span>
            </div>
            {post.requirements.voiceChat && (
              <div className="requirement voice-chat">
                <span>Voice Chat Required</span>
              </div>
            )}
          </div>
        </div>

        <div className="lfg-detail-actions">
          {isOwner ? (
            <>
              <button
                onClick={() => navigate(`/lfg/${post.id}/edit`)}
                className="edit-button"
              >
                Edit Post
              </button>
              <button
                onClick={handleDelete}
                className="delete-button"
              >
                Delete Post
              </button>
            </>
          ) : (
            !isExpired && post.status === 'open' && (
              <button
                onClick={() => navigate(`/lfg/${post.id}/join`)}
                className="join-button"
              >
                Join Group
              </button>
            )
          )}
        </div>
      </div>
    </div>
  );
} 