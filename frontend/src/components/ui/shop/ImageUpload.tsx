import React, { useState, useRef } from 'react'
import { Upload, X } from 'lucide-react'

interface ImageUploadProps {
  currentImageUrl?: string
  onUpload: (url: string) => void
  onRemove?: () => void
  showRemoveButton?: boolean
  className?: string
}

export function ImageUpload({ 
  currentImageUrl, 
  onUpload, 
  onRemove,
  showRemoveButton = false,
  className = '' 
}: ImageUploadProps) {
  const [isHovering, setIsHovering] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Define allowed file extensions
  const allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp']
  const acceptAttribute = "image/jpeg, image/png, image/gif, image/webp, image/jpg"

  // Get Cloudinary settings from environment variables
  const cloudName = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME as string
  const uploadPreset = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET as string

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0]
      
      // Validate file type
      if (!file.type.match('image.*')) {
        setError('Please select an image file')
        return
      }
      
      // Additional validation for file extensions
      const fileExtension = file.name.split('.').pop()?.toLowerCase() || ''
      if (!allowedExtensions.includes(fileExtension)) {
        setError('Please select a JPG, PNG, GIF, or WebP file')
        return
      }
      
      // Validate file size (max 5MB)
      if (file.size > 5 * 1024 * 1024) {
        setError('Image size should be less than 5MB')
        return
      }
      
      setError(null)
      setUploading(true)
      
      const formData = new FormData()
      formData.append('file', file)
      formData.append('upload_preset', uploadPreset)
      formData.append('tags', 'shop_item')

      try {
        // Upload directly to Cloudinary
        const response = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/upload`, {
          method: 'POST',
          body: formData,
        })
        
        if (!response.ok) {
          throw new Error('Upload failed')
        }
        
        const data = await response.json()
        if (data.secure_url) {
          onUpload(data.secure_url)
        }
      } catch (error) {
        console.error('Image upload failed:', error)
        setError('Upload failed. Please try again.')
      } finally {
        setUploading(false)
      }
    }
  }

  // Handle image container click to open file picker
  const handleImageClick = () => {
    if (!uploading && fileInputRef.current) {
      fileInputRef.current.click()
    }
  }

  return (
    <div className={`${className}`}>
      <div 
        className="relative h-48 w-full overflow-hidden rounded-lg border border-white/10 bg-slate-800/50"
        onMouseEnter={() => setIsHovering(true)}
        onMouseLeave={() => setIsHovering(false)}
        onClick={handleImageClick}
      >
        {currentImageUrl ? (
          <img 
            src={currentImageUrl} 
            alt="Item Image" 
            className="h-full w-full object-contain"
          />
        ) : (
          <div className="flex h-full w-full flex-col items-center justify-center">
            <Upload className="mb-2 h-8 w-8 text-white/40" />
            <span className="text-sm text-white/60">Upload Image</span>
          </div>
        )}
        
        {/* Hover overlay */}
        <div
          className={`absolute inset-0 flex items-center justify-center bg-black/60 transition-opacity duration-200 ${
            isHovering && !uploading ? 'opacity-100' : 'opacity-0'
          }`}
        >
          <Upload className="h-6 w-6 text-white" />
        </div>
        
        {/* Loading overlay */}
        {uploading && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/60">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-white border-t-transparent" />
          </div>
        )}
        
        {/* Remove button */}
        {showRemoveButton && onRemove && currentImageUrl && !uploading && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
            }}
            className="absolute right-2 top-2 rounded-full border border-white/20 bg-white/15 p-1.5 text-white shadow-md backdrop-blur-sm transition-all duration-200 hover:scale-105 hover:bg-red-500/90 hover:shadow-lg"
            title="Remove image"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
      
      {/* Hidden file input */}
      <input 
        ref={fileInputRef}
        type="file" 
        accept={acceptAttribute}
        onChange={handleFileChange} 
        className="hidden" 
        disabled={uploading}
      />
      
      {error && (
        <div className="mt-2 text-xs text-red-400">
          {error}
        </div>
      )}
    </div>
  )
} 