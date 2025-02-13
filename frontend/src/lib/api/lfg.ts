import { API_ENDPOINTS } from '@/config/endpoints';
import { PaginatedResponse } from '../../types/api';

export interface LFGPost {
  id: string;
  userId: string;
  game: string;
  title: string;
  description: string;
  requirements: {
    rank?: string;
    region: string;
    language: string[];
    voiceChat: boolean;
  };
  status: 'open' | 'closed';
  createdAt: string;
  expiresAt: string;
}

export interface CreateLFGRequest {
  game: string;
  title: string;
  description: string;
  requirements: {
    rank?: string;
    region: string;
    language: string[];
    voiceChat: boolean;
  };
  expiresIn: number; // minutes
}

export interface FilterParams {
  game?: string;
  region?: string;
  language?: string[];
  voiceChat?: boolean;
  status?: 'open' | 'closed';
  page?: number;
  limit?: number;
}

export class LFGService {
  static async createPost(data: CreateLFGRequest): Promise<LFGPost> {
    const response = await fetch(API_ENDPOINTS.LFG_CREATE, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error('Failed to create LFG post');
    }

    return response.json();
  }

  static async getPosts(filters: FilterParams): Promise<PaginatedResponse<LFGPost>> {
    const params = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== undefined) {
        if (Array.isArray(value)) {
          value.forEach(v => params.append(key, v));
        } else {
          params.append(key, value.toString());
        }
      }
    });

    const response = await fetch(`${API_ENDPOINTS.LFG_LIST}?${params}`);
    if (!response.ok) {
      throw new Error('Failed to fetch LFG posts');
    }

    return response.json();
  }

  static async getPost(id: string): Promise<LFGPost> {
    const response = await fetch(`${API_ENDPOINTS.LFG_DETAIL}/${id}`);
    if (!response.ok) {
      throw new Error('Failed to fetch LFG post');
    }

    return response.json();
  }

  static async updatePost(id: string, data: Partial<CreateLFGRequest>): Promise<LFGPost> {
    const response = await fetch(`${API_ENDPOINTS.LFG_UPDATE}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error('Failed to update LFG post');
    }

    return response.json();
  }

  static async deletePost(id: string): Promise<void> {
    const response = await fetch(`${API_ENDPOINTS.LFG_DELETE}/${id}`, {
      method: 'DELETE',
    });

    if (!response.ok) {
      throw new Error('Failed to delete LFG post');
    }
  }

  static async joinPost(postId: string): Promise<void> {
    const response = await fetch(`${API_ENDPOINTS.LFG_JOIN}/${postId}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error('Failed to join LFG post');
    }
  }
} 