import { ButtonHTMLAttributes } from 'react';
import { cn } from '../utils/cn';

interface CustomButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'ghost';
}

export function CustomButton({ 
  children, 
  className, 
  variant = 'default',
  ...props 
}: CustomButtonProps) {
  return (
    <button
      className={cn(
        'rounded-full font-grandstander font-medium transition-all duration-200 text-lg',
        variant === 'default' && 'bg-white/20 hover:bg-white/30 text-white px-8 py-2',
        variant === 'ghost' && 'bg-white/10 hover:bg-white/20 text-white px-8 py-2',
        className
      )}
      {...props}
    >
      {children}
    </button>
  );
} 