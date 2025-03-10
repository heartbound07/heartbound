import React from "react";

interface LoadingSpinnerProps {
  title?: string;
  description?: string;
  size?: "sm" | "md" | "lg";
  fullScreen?: boolean;
  theme?: "valorant" | "dashboard";
}

export function LoadingSpinner({
  title = "Loading...",
  description,
  size = "md",
  fullScreen = false,
  theme = "valorant",
}: LoadingSpinnerProps) {
  // Size mappings
  const sizeClasses = {
    sm: "w-8 h-8",
    md: "w-12 h-12",
    lg: "w-16 h-16",
  };

  // Theme-specific colors
  const colorClasses = {
    valorant: {
      primary: "#FF4655",
      container: "bg-zinc-900/50",
      background: "from-[#0F1923] to-[#1A242F]"
    },
    dashboard: {
      primary: "#5865F2", // Discord blue for dashboard theme
      container: "bg-[#1a1b1e]/60",
      background: "from-[#111827] to-[#1f2937]"
    }
  };

  const themeColors = colorClasses[theme];
  
  const containerClass = fullScreen 
    ? `min-h-screen bg-gradient-to-br ${themeColors.background} text-white font-sans flex items-center justify-center`
    : "flex flex-col items-center justify-center p-6";

  return (
    <div 
      className={containerClass}
      style={{ contain: "layout style" }}
    >
      <div 
        className={`p-6 rounded-xl ${themeColors.container} backdrop-blur-sm border border-white/5 shadow-lg flex flex-col items-center`}
        style={{ transform: "translateZ(0)" }}
      >
        <div 
          className={`${sizeClasses[size]} rounded-full border-2 border-t-[${themeColors.primary}] border-r-[${themeColors.primary}]/50 border-b-[${themeColors.primary}]/20 border-l-transparent animate-spin mb-4`}
          style={{ 
            willChange: "transform",
            transform: "translateZ(0)"
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
