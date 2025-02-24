import React from 'react';
import { cn } from '@/utils/cn';

interface GameCardProps {
  title: string;
  image: string;
  logo: string;
  alt: string;
  className?: string;
}

export function GameCard({ title, image, logo, alt, className }: GameCardProps) {
  return (
    <div
      className={cn(
        "group relative overflow-hidden rounded-2xl bg-black/10 backdrop-blur-sm transition-all duration-300 hover:scale-105 hover:bg-black/20",
        "border border-white/10 p-0.5 cursor-pointer w-full max-w-xs",
        "shadow-md hover:shadow-xl",
        "animate-gameCard",
        className
      )}
    >
      <div className="aspect-[3/4] relative overflow-hidden rounded-xl">
        <img src={image} alt={alt} className="object-cover w-full h-full" />
      </div>
      
      {/* Static logo at bottom-left - hidden on hover */}
      <div className="absolute bottom-2 left-2 group-hover:hidden">
        <img
          src={logo}
          alt={`${title} logo`}
          width="28"
          height="28"
          className="rounded-md bg-black/50 p-0.5 backdrop-blur-sm"
        />
      </div>

      {/* Hover overlay with optimized performance using will-change and translateZ(0) */}
      <div 
        className="absolute inset-0 flex flex-col items-center justify-center bg-black bg-opacity-40 backdrop-blur-sm opacity-0 transition-opacity duration-300 group-hover:opacity-100" 
        style={{ willChange: "opacity, backdrop-filter", transform: "translateZ(0)" }}
      >
        <p className="text-white text-lg font-semibold">{title}</p>
      </div>
    </div>
  );
} 