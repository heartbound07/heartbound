import React from 'react';

interface GameCardProps {
  title: string;
  imageSrc: string;
  logoSrc: string;
  altText: string;
}

export function GameCard({ title, imageSrc, logoSrc, altText }: GameCardProps) {
  return (
    <div className="game-card bg-white/10 backdrop-blur-md rounded-lg overflow-hidden hover:scale-105 transition-transform">
      <img src={imageSrc} alt={altText} className="w-full" />
      <div className="p-4">
        <img src={logoSrc} alt={`${title} logo`} className="w-12 h-12 mb-2" />
        <h3 className="text-white font-bold">{title}</h3>
      </div>
    </div>
  );
} 