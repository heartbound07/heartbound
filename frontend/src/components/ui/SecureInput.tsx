import React, { useState, useCallback } from 'react';
import { useInputValidation, useCharacterCount } from '@/hooks/useSanitizedContent';
import { sanitizeText, isContentSafe } from '@/components/SafeHtmlRenderer';

interface SecureInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  maxLength?: number;
  minLength?: number;
  required?: boolean;
  type?: 'text' | 'textarea';
  label?: string;
  helpText?: string;
  allowHtml?: boolean;
  stripHtml?: boolean;
  showCharacterCount?: boolean;
  realTimeValidation?: boolean;
}

/**
 * SecureInput Component
 * 
 * A secure input component that provides:
 * - Real-time validation with security checks
 * - Character count display
 * - Sanitization warnings
 * - Visual feedback for validation states
 * 
 * Usage:
 * <SecureInput 
 *   value={itemName} 
 *   onChange={setItemName}
 *   maxLength={100}
 *   minLength={3}
 *   label="Item Name"
 *   required
 * />
 */
export const SecureInput: React.FC<SecureInputProps> = ({
  value,
  onChange,
  placeholder = '',
  className = '',
  maxLength = 500,
  minLength = 0,
  required = false,
  type = 'text',
  label,
  helpText,
  allowHtml = false,
  stripHtml = true,
  showCharacterCount = true,
  realTimeValidation = true
}) => {
  const [touched, setTouched] = useState(false);
  const [showSanitizationWarning, setShowSanitizationWarning] = useState(false);

  const { validate } = useInputValidation({
    maxLength,
    minLength,
    allowHtml,
    stripHtml
  });

  const characterCount = useCharacterCount(value, maxLength);
  
  // Real-time validation
  const validation = realTimeValidation && touched ? validate(value) : { isValid: true, errors: [] };

  // Handle input change with sanitization feedback
  const handleChange = useCallback((newValue: string) => {
    // Check if content would be modified by sanitization
    const sanitized = stripHtml ? sanitizeText(newValue) : newValue;
    const wouldBeSanitized = sanitized !== newValue;
    
    // Show warning if content would be sanitized
    if (wouldBeSanitized && !allowHtml) {
      setShowSanitizationWarning(true);
      // Hide warning after 3 seconds
      setTimeout(() => setShowSanitizationWarning(false), 3000);
    } else {
      setShowSanitizationWarning(false);
    }

    onChange(newValue);
  }, [onChange, stripHtml, allowHtml]);

  const handleBlur = useCallback(() => {
    setTouched(true);
  }, []);

  // Get validation state classes
  const getValidationClasses = () => {
    if (!realTimeValidation || !touched) return '';
    
    if (validation.isValid) {
      return 'border-green-500 focus:border-green-500 focus:ring-green-500';
    } else {
      return 'border-red-500 focus:border-red-500 focus:ring-red-500';
    }
  };

  // Get character count color
  const getCharacterCountColor = () => {
    if (characterCount.isOverLimit) return 'text-red-500';
    if (characterCount.percentage > 80) return 'text-yellow-500';
    return 'text-slate-400';
  };

  const baseClasses = `
    w-full px-3 py-2 
    bg-slate-800 border border-slate-700 rounded-md 
    text-white placeholder-slate-400
    focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary
    transition-colors duration-200
    ${getValidationClasses()}
    ${className}
  `.trim();

  return (
    <div className="space-y-2">
      {/* Label */}
      {label && (
        <label className="block text-sm font-medium text-slate-300">
          {label}
          {required && <span className="text-red-400 ml-1">*</span>}
        </label>
      )}

      {/* Input Field */}
      <div className="relative">
        {type === 'textarea' ? (
          <textarea
            value={value}
            onChange={(e) => handleChange(e.target.value)}
            onBlur={handleBlur}
            placeholder={placeholder}
            className={`${baseClasses} min-h-[100px] resize-vertical`}
            maxLength={maxLength}
            required={required}
          />
        ) : (
          <input
            type="text"
            value={value}
            onChange={(e) => handleChange(e.target.value)}
            onBlur={handleBlur}
            placeholder={placeholder}
            className={baseClasses}
            maxLength={maxLength}
            required={required}
          />
        )}

        {/* Security Status Icon */}
        {touched && (
          <div className="absolute right-3 top-3">
            {validation.isValid ? (
              <svg className="w-5 h-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            ) : (
              <svg className="w-5 h-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            )}
          </div>
        )}
      </div>

      {/* Character Count */}
      {showCharacterCount && maxLength && (
        <div className={`text-xs ${getCharacterCountColor()} flex justify-between`}>
          <span>
            {characterCount.current} / {characterCount.max} characters
          </span>
          {characterCount.remaining !== null && (
            <span>
              {characterCount.isOverLimit ? 
                `${Math.abs(characterCount.remaining)} over limit` : 
                `${characterCount.remaining} remaining`
              }
            </span>
          )}
        </div>
      )}

      {/* Help Text */}
      {helpText && (
        <p className="text-xs text-slate-400">
          {helpText}
        </p>
      )}

      {/* Sanitization Warning */}
      {showSanitizationWarning && (
        <div className="p-2 bg-yellow-900/30 border border-yellow-600/50 rounded-md">
          <div className="flex items-center">
            <svg className="w-4 h-4 text-yellow-500 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.08 16.5c-.77.833.192 2.5 1.732 2.5z" />
            </svg>
            <span className="text-xs text-yellow-300">
              HTML content will be removed for security
            </span>
          </div>
        </div>
      )}

      {/* Validation Errors */}
      {realTimeValidation && touched && validation.errors.length > 0 && (
        <div className="space-y-1">
          {validation.errors.map((error, index) => (
            <div key={index} className="p-2 bg-red-900/30 border border-red-600/50 rounded-md">
              <div className="flex items-center">
                <svg className="w-4 h-4 text-red-500 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span className="text-xs text-red-300">{error}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Security Info */}
      {!isContentSafe(value) && value.length > 0 && (
        <div className="p-2 bg-red-900/30 border border-red-600/50 rounded-md">
          <div className="flex items-center">
            <svg className="w-4 h-4 text-red-500 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            <span className="text-xs text-red-300">
              Content contains potentially dangerous characters and will be sanitized
            </span>
          </div>
        </div>
      )}
    </div>
  );
};

export default SecureInput; 