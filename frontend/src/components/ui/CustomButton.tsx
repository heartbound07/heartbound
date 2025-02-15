import { Button } from "./button"
import { ButtonHTMLAttributes } from "react"

interface CustomButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'ghost'
}

export function CustomButton({ children, className, variant = 'default', ...props }: CustomButtonProps) {
  const baseClasses = "text-white rounded-full relative flex items-center justify-center transition-colors"
  const variantClasses = variant === 'default' 
    ? "bg-white/20 hover:bg-white/30" 
    : "bg-transparent hover:bg-white/10"

  return (
    <Button className={`${baseClasses} ${variantClasses} ${className}`} {...props}>
      {children}
    </Button>
  )
} 