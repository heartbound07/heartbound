import { useEffect } from 'react';
import { useLFG } from '@/contexts/lfg/LFGContext';
import { LFGService } from '@/lib/api/lfg';
import { PostCard } from './PostCard';

export function PostGrid() {
  const { state, dispatch } = useLFG();
  const { posts, filters, isLoading, error, currentPage } = state;

  useEffect(() => {
    const fetchPosts = async () => {
      dispatch({ type: 'SET_LOADING', payload: true });
      try {
        const response = await LFGService.getPosts({
          ...filters,
          page: currentPage,
          limit: 12,
        });
        dispatch({ type: 'SET_POSTS', payload: response.data });
        dispatch({
          type: 'SET_PAGINATION',
          payload: {
            totalPages: response.totalPages,
            currentPage: response.currentPage,
          },
        });
      } catch (err) {
        dispatch({
          type: 'SET_ERROR',
          payload: err instanceof Error ? err.message : 'Failed to fetch posts',
        });
      } finally {
        dispatch({ type: 'SET_LOADING', payload: false });
      }
    };

    fetchPosts();
  }, [filters, currentPage, dispatch]);

  if (isLoading) {
    return <div className="lfg-loading">Loading posts...</div>;
  }

  if (error) {
    return <div className="lfg-error">{error}</div>;
  }

  if (posts.length === 0) {
    return <div className="lfg-empty">No posts found</div>;
  }

  return (
    <div className="lfg-grid">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
} 