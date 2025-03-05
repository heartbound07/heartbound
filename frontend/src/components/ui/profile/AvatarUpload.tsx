import React, { useState, useRef } from 'react'
import { Cloudinary } from '@cloudinary/url-gen'
import { auto } from '@cloudinary/url-gen/actions/resize'
import { autoGravity } from '@cloudinary/url-gen/qualifiers/gravity'
import { AdvancedImage } from '@cloudinary/react'
import { Camera } from 'lucide-react'

interface AvatarUploadProps {
  currentAvatarUrl?: string
  onUpload: (url: string) => void
  size?: number
  className?: string
}

export function AvatarUpload({ currentAvatarUrl, onUpload, size = 96, className = '' }: AvatarUploadProps) {
  const [isHovering, setIsHovering] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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
    <div className={`${className}`}>
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
      </div>
      
      {/* Hidden file input */}
      <input 
        ref={fileInputRef}
        type="file" 
        accept="image/*" 
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
