import React, { useState, useRef } from 'react'
import { Cloudinary } from '@cloudinary/url-gen'
import { auto } from '@cloudinary/url-gen/actions/resize'
import { autoGravity } from '@cloudinary/url-gen/qualifiers/gravity'
import { AdvancedImage } from '@cloudinary/react'
import { Camera, X } from 'lucide-react'
import { useAuth } from '@/contexts/auth'

interface AvatarUploadProps {
  currentAvatarUrl?: string
  onUpload: (url: string) => void
  onRemove?: () => void
  showRemoveButton?: boolean
  size?: number
  className?: string
}

export function AvatarUpload({ 
  currentAvatarUrl, 
  onUpload, 
  onRemove,
  showRemoveButton = false,
  size = 96, 
  className = '' 
}: AvatarUploadProps) {
  const { hasRole } = useAuth()
  const [isHovering, setIsHovering] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Define allowed file types based on user role
  const isPremiumUser = hasRole('MONARCH') || hasRole('ADMIN') || hasRole('MODERATOR')
  const acceptAttribute = isPremiumUser ? "image/jpeg, image/png, image/gif, image/jpg" : "image/jpeg, image/png, image/jpg"
  const allowedExtensions = isPremiumUser ? ['jpg', 'jpeg', 'png', 'gif'] : ['jpg', 'jpeg', 'png']

  // Get Cloudinary settings from environment variables
  const cloudName = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME as string
  const uploadPreset = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET as string

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0]
      
      // Validate file type with role-based restrictions
      if (!file.type.match('image.*')) {
        setError('Please select an image file')
        return
      }
      
      // Additional validation for file extensions based on role
      const fileExtension = file.name.split('.').pop()?.toLowerCase() || ''
      if (!allowedExtensions.includes(fileExtension)) {
        setError(isPremiumUser 
          ? 'Please select a JPG, PNG, or GIF file' 
          : 'Please select a JPG or PNG file')
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
      
      // Add role-based tags to the upload
      formData.append('tags', isPremiumUser ? 'premium-avatar' : 'standard-avatar')

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
        console.error('Avatar upload failed:', error)
        setError('Upload failed. Please try again.')
      } finally {
        setUploading(false)
      }
    }
  }

  // Handle avatar click to open file picker
  const handleAvatarClick = () => {
    if (!uploading && fileInputRef.current) {
      fileInputRef.current.click()
    }
  }

  // Set up Cloudinary image transformation
  const cld = new Cloudinary({ cloud: { cloudName } })
  const imageUrl = currentAvatarUrl
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
        .resize(auto().gravity(autoGravity()).width(size).height(size))
    } else {
      // Use external URL as is
      cldImage = null
    }
  }

  return (
    <div className={`${className} relative`}>
      <div 
        className="relative w-24 h-24"
        onMouseEnter={() => setIsHovering(true)}
        onMouseLeave={() => setIsHovering(false)}
        onClick={handleAvatarClick}
      >
        {cldImage ? (
          <AdvancedImage 
            cldImg={cldImage} 
            className="h-24 w-24 rounded-full border-2 border-white/10 transition-all duration-200 object-cover" 
          />
        ) : imageUrl ? (
          <img 
            src={imageUrl} 
            alt="Avatar" 
            className="h-24 w-24 rounded-full border-2 border-white/10 transition-all duration-200 object-cover"
          />
        ) : (
          <div className="h-24 w-24 rounded-full border-2 border-dashed border-white/10 flex items-center justify-center bg-white/5">
            <Camera className="h-8 w-8 text-white/40" />
          </div>
        )}
        
        {/* Hover overlay */}
        <div
          className={`absolute inset-0 flex items-center justify-center rounded-full bg-black/60 transition-opacity duration-200 ${
            isHovering && !uploading ? 'opacity-100' : 'opacity-0'
          }`}
        >
          <Camera className="h-6 w-6 text-white" />
        </div>
        
        {/* Loading overlay */}
        {uploading && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/60 rounded-full">
            <div className="h-8 w-8 border-2 border-white border-t-transparent rounded-full animate-spin" />
          </div>
        )}
        
        {/* Remove button - Moved inside the avatar div for better positioning */}
        {showRemoveButton && onRemove && currentAvatarUrl && !uploading && (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
            }}
            className="absolute top-0 right-0 transform translate-x-1/3 -translate-y-1/3 bg-white/15 hover:bg-red-500/90 text-white rounded-full p-1.5 transition-all duration-200 backdrop-blur-sm border border-white/20 shadow-md hover:shadow-lg hover:scale-105"
            title="Remove avatar"
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
        <div className="mt-2 text-xs text-red-400 text-center">
          {error}
        </div>
      )}
    </div>
  )
}
