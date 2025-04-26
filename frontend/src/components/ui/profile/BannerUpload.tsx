import React, { useState, useRef } from 'react'
import { Cloudinary } from '@cloudinary/url-gen'
import { auto } from '@cloudinary/url-gen/actions/resize'
import { autoGravity } from '@cloudinary/url-gen/qualifiers/gravity'
import { AdvancedImage } from '@cloudinary/react'
import { Upload, X } from 'lucide-react'

interface BannerUploadProps {
  currentBannerUrl?: string
  bannerColor: string
  onUpload: (url: string) => void
  onRemove?: () => void
  showRemoveButton?: boolean
  className?: string
}

export function BannerUpload({ 
  currentBannerUrl, 
  bannerColor, 
  onUpload, 
  onRemove,
  showRemoveButton = false,
  className = '' 
}: BannerUploadProps) {
  const [isHovering, setIsHovering] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Define allowed file extensions
  const allowedExtensions = ['jpg', 'jpeg', 'png', 'gif']
  const acceptAttribute = "image/jpeg, image/png, image/gif, image/jpg"

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
        setError('Please select a JPG, PNG, or GIF file')
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
        console.error('Banner upload failed:', error)
        setError('Upload failed. Please try again.')
      } finally {
        setUploading(false)
      }
    }
  }

  // Handle banner click to open file picker
  const handleBannerClick = () => {
    if (!uploading && fileInputRef.current) {
      fileInputRef.current.click()
    }
  }

  // Set up Cloudinary image transformation
  const cld = new Cloudinary({ cloud: { cloudName } })
  const imageUrl = currentBannerUrl
  let cldImage
  
  if (imageUrl && cloudName) {
    // Check if it's already a Cloudinary URL
    if (imageUrl.includes('cloudinary')) {
      // Extract public ID from Cloudinary URL
      const parts = imageUrl.split('/')
      const publicId = parts[parts.length - 1].split('.')[0]
      
      cldImage = cld
        .image(publicId)
        .format('auto')
        .quality('auto')
        .resize(auto().gravity(autoGravity())) // Use auto gravity for banners
    } else {
      // Use external URL as is
      cldImage = null
    }
  }

  return (
    <div className={`${className}`}>
      <div 
        className="relative h-32 w-full overflow-hidden rounded-lg border border-white/10"
        onMouseEnter={() => setIsHovering(true)}
        onMouseLeave={() => setIsHovering(false)}
        onClick={handleBannerClick}
      >
        {cldImage ? (
          <AdvancedImage 
            cldImg={cldImage} 
            className="h-full w-full object-cover" 
          />
        ) : imageUrl ? (
          <img 
            src={imageUrl} 
            alt="Banner" 
            className="h-full w-full object-cover"
          />
        ) : (
          <div 
            className={`h-full w-full ${bannerColor.startsWith('bg-') ? bannerColor : ''}`} 
            style={!bannerColor.startsWith('bg-') ? { backgroundColor: bannerColor } : {}}
          />
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
            <div className="h-8 w-8 border-2 border-white border-t-transparent rounded-full animate-spin" />
          </div>
        )}
        
        {/* Remove button */}
        {showRemoveButton && onRemove && currentBannerUrl && !uploading && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
            }}
            className="absolute top-2 right-2 bg-white/15 hover:bg-red-500/90 text-white rounded-full p-1.5 transition-all duration-200 backdrop-blur-sm border border-white/20 shadow-md hover:shadow-lg hover:scale-105"
            title="Remove banner"
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
