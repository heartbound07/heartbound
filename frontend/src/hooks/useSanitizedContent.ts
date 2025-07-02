import { useMemo, useCallback } from 'react';
import { sanitizeText, isContentSafe } from '@/components/SafeHtmlRenderer';

interface SanitizedContentResult {
  sanitized: string;
  isSafe: boolean;
  isModified: boolean;
  length: number;
}

interface ValidationRules {
  maxLength?: number;
  minLength?: number;
  allowHtml?: boolean;
  stripHtml?: boolean;
}

/**
 * useSanitizedContent Hook
 * 
 * Provides utilities for safely handling user-generated content.
 * Sanitizes content, validates it, and provides safety information.
 * 
 * Usage:
 * const { sanitized, isSafe, isModified } = useSanitizedContent(userInput, { maxLength: 100 });
 */
export function useSanitizedContent(
  content: string | null | undefined,
  rules: ValidationRules = {}
): SanitizedContentResult {
  const {
    maxLength,
    minLength = 0,
    allowHtml = false,
    stripHtml = true
  } = rules;

  const result = useMemo((): SanitizedContentResult => {
    // Handle null/undefined content
    if (!content || content.trim() === '') {
      return {
        sanitized: '',
        isSafe: true,
        isModified: false,
        length: 0
      };
    }

    // Sanitize content based on rules
    const sanitized = stripHtml ? sanitizeText(content) : content;
    
    // Check if content was modified during sanitization
    const isModified = sanitized !== content;
    
    // Check if content is safe (no dangerous patterns)
    const isSafe = isContentSafe(content);
    
    // Truncate if necessary
    const finalContent = maxLength && sanitized.length > maxLength 
      ? sanitized.substring(0, maxLength).trim()
      : sanitized;

    return {
      sanitized: finalContent,
      isSafe,
      isModified: isModified || (finalContent !== sanitized),
      length: finalContent.length
    };
  }, [content, maxLength, minLength, allowHtml, stripHtml]);

  return result;
}

/**
 * useInputValidation Hook
 * 
 * Provides real-time validation for user inputs with security checks.
 * 
 * Usage:
 * const { isValid, errors, validate } = useInputValidation({ maxLength: 100, minLength: 3 });
 */
export function useInputValidation(rules: ValidationRules) {
  const validate = useCallback((value: string): { isValid: boolean; errors: string[] } => {
    const errors: string[] = [];
    
    if (!value || value.trim() === '') {
      if (rules.minLength && rules.minLength > 0) {
        errors.push('This field is required');
      }
      return { isValid: errors.length === 0, errors };
    }

    // Sanitize for validation
    const sanitized = rules.stripHtml !== false ? sanitizeText(value) : value;
    
    // Length validation
    if (rules.minLength && sanitized.length < rules.minLength) {
      errors.push(`Minimum length is ${rules.minLength} characters`);
    }
    
    if (rules.maxLength && sanitized.length > rules.maxLength) {
      errors.push(`Maximum length is ${rules.maxLength} characters`);
    }
    
    // Safety validation
    if (!isContentSafe(value)) {
      errors.push('Content contains potentially dangerous characters');
    }
    
    // HTML validation
    if (!rules.allowHtml && value !== sanitized) {
      errors.push('HTML tags are not allowed in this field');
    }

    return { isValid: errors.length === 0, errors };
  }, [rules]);

  return { validate };
}

/**
 * Utility function to get character count for display
 */
export function useCharacterCount(content: string, maxLength?: number) {
  return useMemo(() => {
    const sanitized = sanitizeText(content || '');
    const length = sanitized.length;
    const remaining = maxLength ? maxLength - length : null;
    const isOverLimit = maxLength ? length > maxLength : false;
    
    return {
      current: length,
      max: maxLength,
      remaining,
      isOverLimit,
      percentage: maxLength ? Math.min((length / maxLength) * 100, 100) : 0
    };
  }, [content, maxLength]);
}

/**
 * Utility function to format content safely for display
 */
export function formatSafeContent(content: string, options: {
  maxLength?: number;
  showTooltip?: boolean;
  preserveNewlines?: boolean;
} = {}): { display: string; isTruncated: boolean; tooltip?: string } {
  if (!content || content.trim() === '') {
    return { display: '', isTruncated: false };
  }

  // Sanitize content
  let sanitized = sanitizeText(content);
  
  // Handle newlines
  if (options.preserveNewlines) {
    sanitized = sanitized.replace(/\n/g, '<br>');
  }
  
  // Handle truncation
  const isTruncated = options.maxLength ? sanitized.length > options.maxLength : false;
  const display = isTruncated ? sanitized.substring(0, options.maxLength!) + '...' : sanitized;
  
  return {
    display,
    isTruncated,
    tooltip: options.showTooltip && isTruncated ? sanitized : undefined
  };
} 