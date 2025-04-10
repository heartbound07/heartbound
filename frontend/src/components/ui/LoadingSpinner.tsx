import { SkeletonAuthentication } from "./SkeletonUI";

interface LoadingSpinnerProps {
  title?: string;
  description?: string;
  size?: "sm" | "md" | "lg";
  fullScreen?: boolean;
  theme?: "valorant" | "dashboard";
  /**
   * Use skeleton loading UI instead of spinner when fullScreen is true
   * @default false
   */
  useSkeleton?: boolean;
}

export function LoadingSpinner({
  title = "Loading...",
  description,
  size = "md",
  fullScreen = false,
  theme = "valorant",
  useSkeleton = false,
}: LoadingSpinnerProps) {
  // If using skeleton UI for fullscreen loading (recommended for better UX)
  if (fullScreen && useSkeleton) {
    return (
      <SkeletonAuthentication 
        theme={theme} 
      />
    );
  }

  // Size mappings
  const sizeClasses = {
    sm: "w-8 h-8",
    md: "w-12 h-12",
    lg: "w-16 h-16"
  };

  // Theme-specific colors
  const themeColors = {
    valorant: {
      background: "from-[#0F1923] to-[#1A242F]",
      container: "bg-zinc-900/50",
      spinnerColors: {
        borderTop: "#FF4655",
        borderRight: "#FF4655",
        borderBottom: "#FF4655"
      }
    },
    dashboard: {
      background: "from-[#111827] to-[#1f2937]",
      container: "bg-[#1a1b1e]/60",
      spinnerColors: {
        borderTop: "#6366f1",
        borderRight: "#8b5cf6",
        borderBottom: "#d946ef"
      }
    }
  };

  // Get current theme colors
  const currentSpinnerColors = themeColors[theme].spinnerColors;
  
  const containerClass = fullScreen 
    ? `min-h-screen bg-gradient-to-br ${themeColors[theme].background} text-white font-sans flex items-center justify-center`
    : "flex flex-col items-center justify-center p-6";

  return (
    <div 
      className={containerClass}
      style={{ contain: "layout style" }}
    >
      <div 
        className={`p-6 rounded-xl ${themeColors[theme].container} backdrop-blur-sm border border-white/5 shadow-lg flex flex-col items-center`}
        style={{ transform: "translateZ(0)" }}
      >
        <div 
          className={`${sizeClasses[size]} rounded-full border-2 animate-spin mb-4`}
          style={{ 
            willChange: "transform",
            transform: "translateZ(0)",
            borderTopColor: currentSpinnerColors.borderTop,
            borderRightColor: currentSpinnerColors.borderRight,
            borderBottomColor: currentSpinnerColors.borderBottom,
            borderLeftColor: "transparent"
          }}
        ></div>
        {title && (
          <div className="text-xl font-medium text-white/90">{title}</div>
        )}
        {description && (
          <div className="text-sm text-white/50 mt-2">{description}</div>
        )}
      </div>
    </div>
  );
}
