import React from 'react';
import DOMPurify from 'dompurify';

interface SafeHtmlRendererProps {
  html: string;
  className?: string;
  tag?: keyof JSX.IntrinsicElements;
  policy?: 'strict' | 'basic' | 'rich';
  maxLength?: number;
}

/**
 * SafeHtmlRenderer Component
 * 
 * Safely renders HTML content using DOMPurify for sanitization.
 * Provides different sanitization policies for different content types.
 * 
 * Usage:
 * <SafeHtmlRenderer html={userContent} policy="basic" />
 */
export const SafeHtmlRenderer: React.FC<SafeHtmlRendererProps> = ({
  html,
  className = '',
  tag: Tag = 'div',
  policy = 'basic',
  maxLength
}) => {
  // Return early if no content
  if (!html || html.trim() === '') {
    return null;
  }

  // Truncate content if maxLength is specified
  let content = maxLength && html.length > maxLength 
    ? html.substring(0, maxLength) + '...' 
    : html;

  // Configure DOMPurify based on policy
  const sanitizedHtml = React.useMemo(() => {
    const config = getSanitizationConfig(policy);
    return DOMPurify.sanitize(content, config);
  }, [content, policy]);

  return (
    <Tag 
      className={className}
      dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
    />
  );
};

/**
 * Get DOMPurify configuration based on sanitization policy
 */
function getSanitizationConfig(policy: 'strict' | 'basic' | 'rich') {
  switch (policy) {
    case 'strict':
      // STRICT: Strip all HTML, text only
      return {
        ALLOWED_TAGS: [],
        ALLOWED_ATTR: [],
        KEEP_CONTENT: true
      };

    case 'basic':
      // BASIC: Allow only text content, strip all HTML but preserve text
      return {
        ALLOWED_TAGS: ['br'], // Allow line breaks only
        ALLOWED_ATTR: [],
        KEEP_CONTENT: true
      };

    case 'rich':
      // RICH: Allow safe formatting tags (for future use)
      return {
        ALLOWED_TAGS: ['b', 'i', 'u', 'strong', 'em', 'br', 'p'],
        ALLOWED_ATTR: ['class'],
        ALLOW_DATA_ATTR: false,
        KEEP_CONTENT: true,
        // Additional safety measures
        FORBID_TAGS: ['script', 'object', 'embed', 'link', 'style', 'iframe'],
        FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'style']
      };

    default:
      return getSanitizationConfig('basic');
  }
}

/**
 * SafeText Component
 * 
 * For displaying plain text content with basic sanitization.
 * This component strips all HTML and renders only text content.
 */
interface SafeTextProps {
  text: string;
  className?: string;
  tag?: keyof JSX.IntrinsicElements;
  maxLength?: number;
  showTooltip?: boolean;
}

export const SafeText: React.FC<SafeTextProps> = ({
  text,
  className = '',
  tag: Tag = 'span',
  maxLength,
  showTooltip = false
}) => {
  // Return early if no content
  if (!text || text.trim() === '') {
    return null;
  }

  // Sanitize text by stripping all HTML
  const sanitizedText = React.useMemo(() => {
    return DOMPurify.sanitize(text, {
      ALLOWED_TAGS: [],
      ALLOWED_ATTR: [],
      KEEP_CONTENT: true
    });
  }, [text]);

  // Truncate if needed
  const displayText = maxLength && sanitizedText.length > maxLength 
    ? sanitizedText.substring(0, maxLength) + '...' 
    : sanitizedText;

  const isTruncated = maxLength && sanitizedText.length > maxLength;

  return (
    <Tag 
      className={className}
      title={showTooltip && isTruncated ? sanitizedText : undefined}
    >
      {displayText}
    </Tag>
  );
};

/**
 * Utility function to sanitize text content
 */
export function sanitizeText(text: string): string {
  if (!text || text.trim() === '') {
    return '';
  }

  return DOMPurify.sanitize(text, {
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
    KEEP_CONTENT: true
  });
}

/**
 * Utility function to check if content is safe
 */
export function isContentSafe(content: string): boolean {
  if (!content || content.trim() === '') {
    return true;
  }

  const sanitized = DOMPurify.sanitize(content, {
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
    KEEP_CONTENT: true
  });

  return content === sanitized;
}

export default SafeHtmlRenderer; 