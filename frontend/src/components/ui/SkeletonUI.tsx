import React from "react";
import { cn } from "@/utils/cn";

// Add shimmer animation styles to match ShopPage
const shimmerStyles = `
  @keyframes shimmer {
    0% {
      background-position: -200% 0;
    }
    100% {
      background-position: 200% 0;
    }
  }
  
  .animate-shimmer {
    animation: shimmer 1.5s infinite;
  }
`;

// Add styles only once
let stylesInjected = false;

const injectStyles = () => {
  if (!stylesInjected && typeof document !== 'undefined') {
    const style = document.createElement('style');
    style.textContent = shimmerStyles;
    document.head.appendChild(style);
    stylesInjected = true;
  }
};

interface SkeletonProps {
  className?: string;
  /**
   * Visual variant of the skeleton
   * @default "default"
   */
  variant?: "default" | "circular" | "rounded" | "rectangular";
  /**
   * Whether to animate the skeleton
   * @default true
   */
  animate?: boolean;
  /**
   * Width of the skeleton (CSS value)
   */
  width?: string;
  /**
   * Height of the skeleton (CSS value)
   */
  height?: string;
  /**
   * Borderradius of the skeleton (CSS value)
   * @default "0.25rem" for "rounded" variant
   */
  borderRadius?: string;
  /**
   * Theme of the skeleton
   * @default "neutral"
   */
  theme?: "neutral" | "valorant" | "dashboard";
}

/**
 * Base Skeleton component for loading states
 */
export function Skeleton({
  className,
  variant = "default",
  animate = true,
  width,
  height,
  borderRadius,
  theme = "neutral",
}: SkeletonProps) {
  // Theme-specific colors - Updated to match ShopPage styling
  const themeClasses = {
    neutral: "from-gray-200 via-gray-300 to-gray-200 dark:from-gray-700 dark:via-gray-600 dark:to-gray-700",
    valorant: "from-[#1E293B]/30 via-[#1E293B]/50 to-[#1E293B]/30",
    dashboard: "from-[#1E293B]/30 via-[#1E293B]/50 to-[#1E293B]/30",
  };

  // Variant-specific styling
  const variantClasses = {
    default: "",
    circular: "rounded-full",
    rounded: borderRadius ? "" : "rounded",
    rectangular: "rounded-none",
  };

  const animationClass = animate
    ? "bg-gradient-to-r animate-shimmer bg-[length:200%_100%]"
    : "bg-gray-200 dark:bg-gray-700";

  const style: React.CSSProperties = {
    width,
    height,
    ...(borderRadius && { borderRadius }),
  };

  // Inject styles once
  React.useEffect(() => {
    injectStyles();
  }, []);

  return (
    <div
      className={cn(
        animationClass,
        themeClasses[theme],
        variantClasses[variant],
        className
      )}
      style={style}
      aria-hidden="true"
    />
  );
}

/**
 * Game Card Skeleton for loading states in game grid displays
 */
export function SkeletonGameCard({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-xl backdrop-blur-sm",
        "border border-white/10 shadow-lg",
        "bg-[rgba(30,41,59,0.3)]", // Match shop-item-card background
        "w-64",
        "transition-all duration-300 ease-in-out",
        className
      )}
    >
      <div className="aspect-[3/4] relative overflow-hidden rounded-lg">
        <Skeleton 
          className="w-full h-full"
          theme={theme}
          animate={true}
        />
      </div>
      
      {/* Static logo placeholder */}
      <div className="absolute bottom-3 left-3">
        <Skeleton
          variant="circular"
          width="32px"
          height="32px"
          theme={theme}
        />
      </div>
    </div>
  );
}

/**
 * Party Listing Skeleton for loading states in valorant party listings
 */
export function SkeletonPartyListing({ 
  className,
  theme = "valorant"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div
      className={cn(
        "rounded-xl border p-4 shadow-md backdrop-blur-sm",
        "border-[rgba(148,163,184,0.1)] bg-[rgba(30,41,59,0.3)]", // Match shop-item-card styling
        "hover:bg-[rgba(30,41,59,0.5)] transition-all duration-300",
        className
      )}
    >
      {/* Header with title and avatar */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex-1">
          <Skeleton 
            width="70%" 
            height="24px" 
            className="mb-2"
            theme={theme}
          />
          <Skeleton 
            width="40%" 
            height="16px"
            theme={theme}
          />
        </div>
        <Skeleton 
          variant="circular" 
          width="40px" 
          height="40px"
          theme={theme}
        />
      </div>
      
      {/* Tags row */}
      <div className="flex flex-wrap gap-2 mb-3">
        {[1, 2, 3].map((i) => (
          <Skeleton 
            key={i} 
            width="60px" 
            height="20px" 
            borderRadius="9999px"
            theme={theme}
          />
        ))}
      </div>
      
      {/* Description */}
      <Skeleton 
        width="100%" 
        height="48px" 
        className="mb-3"
        theme={theme}
      />
      
      {/* Button row */}
      <div className="flex justify-end mt-2">
        <Skeleton 
          width="100px" 
          height="36px" 
          borderRadius="6px"
          theme={theme}
        />
      </div>
    </div>
  );
}

/**
 * Party Details Skeleton for loading states in party detail views
 */
export function SkeletonPartyDetails({ 
  className,
  theme = "valorant"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn("space-y-8", className)}>
      {/* Party Header */}
      <div className="bg-[rgba(30,41,59,0.3)] backdrop-blur-sm rounded-xl border border-[rgba(148,163,184,0.1)] shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-white/10">
          <div className="flex justify-between items-start gap-4">
            <div className="flex-1">
              <Skeleton 
                width="70%" 
                height="32px" 
                className="mb-3"
                theme={theme}
              />
              
              <div className="flex flex-wrap gap-2 mb-3">
                {[1, 2, 3, 4].map((i) => (
                  <Skeleton 
                    key={i} 
                    width="40px" 
                    height="40px" 
                    variant="circular"
                    theme={theme}
                  />
                ))}
              </div>
              
              <div className="flex gap-2 mt-3">
                {[1, 2].map((i) => (
                  <Skeleton 
                    key={i}
                    width="80px" 
                    height="24px" 
                    borderRadius="9999px"
                    theme={theme}
                  />
                ))}
              </div>
            </div>
            
            <div className="flex gap-2">
              {[1, 2].map((i) => (
                <Skeleton 
                  key={i}
                  width="40px" 
                  height="40px" 
                  variant="circular"
                  theme={theme}
                />
              ))}
            </div>
          </div>
          
          <Skeleton 
            width="100%" 
            height="48px" 
            className="mt-4"
            theme={theme}
          />
        </div>
        
        {/* Game Settings */}
        <div className="p-6 bg-[rgba(30,41,59,0.5)]">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {[1, 2, 3].map((i) => (
              <Skeleton 
                key={i}
                width="100%" 
                height="64px" 
                borderRadius="0.5rem"
                theme={theme}
              />
            ))}
          </div>
        </div>
      </div>
      
      {/* Player Slots */}
      <div className="bg-[rgba(30,41,59,0.3)] backdrop-blur-sm rounded-xl border border-[rgba(148,163,184,0.1)] shadow-2xl p-6">
        <Skeleton 
          width="30%" 
          height="24px" 
          className="mb-4"
          theme={theme}
        />
        
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-3">
          {[1, 2, 3, 4, 5].map((i) => (
            <Skeleton 
              key={i}
              width="100%" 
              height="80px" 
              borderRadius="0.5rem"
              theme={theme}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Profile Skeleton for loading states in profile views
 */
export function SkeletonProfile({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn("p-6 max-w-md mx-auto", className)}>
      <div className="flex flex-col items-center">
        <Skeleton 
          variant="circular" 
          width="96px" 
          height="96px" 
          className="mb-4"
          theme={theme}
        />
        
        <Skeleton 
          width="70%" 
          height="28px" 
          className="mb-2"
          theme={theme}
        />
        
        <Skeleton 
          width="50%" 
          height="20px" 
          className="mb-6"
          theme={theme}
        />
        
        <div className="w-full space-y-3">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton 
              key={i}
              width="100%" 
              height="24px"
              theme={theme}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * Authentication Skeleton for loading states during authentication flows
 */
export function SkeletonAuthentication({ 
  className,
  theme = "dashboard"
}: { 
  className?: string;
  theme?: "valorant" | "dashboard";
}) {
  return (
    <div className={cn(
      "min-h-screen bg-gradient-to-br flex items-center justify-center p-6",
      theme === "valorant" ? "from-[#0F1923] to-[#1A242F]" : "from-[#111827] to-[#1f2937]",
      className
    )}>
      <div className={cn(
        "p-8 rounded-xl backdrop-blur-sm border shadow-lg flex flex-col items-center",
        "border-[rgba(148,163,184,0.1)] bg-[rgba(30,41,59,0.3)]", // Match shop styling
        className
      )}>
        <Skeleton 
          variant="circular" 
          width="64px" 
          height="64px" 
          className="mb-6"
          theme={theme}
        />
        
        <Skeleton 
          width="200px" 
          height="28px" 
          className="mb-4"
          theme={theme}
        />
        
        <Skeleton 
          width="300px" 
          height="20px" 
          className="mb-2"
          theme={theme}
        />
      </div>
    </div>
  );
}
