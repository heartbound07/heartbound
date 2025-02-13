import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CreateLFGRequest, LFGService } from '@/lib/api/lfg';

export function CreatePostForm() {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);

    const formData = new FormData(e.currentTarget);
    const data: CreateLFGRequest = {
      game: formData.get('game') as string,
      title: formData.get('title') as string,
      description: formData.get('description') as string,
      requirements: {
        rank: formData.get('rank') as string,
        region: formData.get('region') as string,
        language: (formData.get('language') as string).split(','),
        voiceChat: formData.get('voiceChat') === 'true',
      },
      expiresIn: parseInt(formData.get('expiresIn') as string),
    };

    try {
      await LFGService.createPost(data);
      navigate('/lfg');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create post');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="lfg-form">
      {error && <div className="error-message">{error}</div>}
      
      <div className="form-group">
        <label htmlFor="game">Game</label>
        <input
          type="text"
          id="game"
          name="game"
          required
          className="lfg-input"
        />
      </div>

      <div className="form-group">
        <label htmlFor="title">Title</label>
        <input
          type="text"
          id="title"
          name="title"
          required
          className="lfg-input"
        />
      </div>

      <div className="form-group">
        <label htmlFor="description">Description</label>
        <textarea
          id="description"
          name="description"
          required
          className="lfg-input"
        />
      </div>

      {/* Add more form fields for requirements */}

      <button
        type="submit"
        disabled={isLoading}
        className="lfg-button"
      >
        {isLoading ? 'Creating...' : 'Create Post'}
      </button>
    </form>
  );
} 