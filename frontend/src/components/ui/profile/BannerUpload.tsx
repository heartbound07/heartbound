import React, { useState, useRef, useCallback } from 'react'
import { Cloudinary } from '@cloudinary/url-gen'
import { auto } from '@cloudinary/url-gen/actions/resize'
import { autoGravity } from '@cloudinary/url-gen/qualifiers/gravity'
import { AdvancedImage } from '@cloudinary/react'
import { Upload, X, Check, RotateCw } from 'lucide-react'
import ReactCrop, { Crop, PixelCrop } from 'react-image-crop'
import 'react-image-crop/dist/ReactCrop.css'
import { motion, AnimatePresence } from 'framer-motion'

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
  
  // Image cropping states
  const [showCropModal, setShowCropModal] = useState(false)
  const [imgSrc, setImgSrc] = useState('')
  const [crop, setCrop] = useState<Crop>()
  const [completedCrop, setCompletedCrop] = useState<PixelCrop>()
  const [rotation, setRotation] = useState(0)
  const imgRef = useRef<HTMLImageElement>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)

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
      
      setSelectedFile(file)
      
      // Create object URL for the image
      const objectUrl = URL.createObjectURL(file)
      setImgSrc(objectUrl)
      
      // Show crop modal
      setShowCropModal(true)
      
      // Reset crop and rotation states
      setCrop(undefined)
      setRotation(0)
    }
  }

  // Function to upload the cropped image
  const uploadCroppedImage = async (blob: Blob) => {
    setUploading(true)
    setShowCropModal(false)
    
    try {
      const formData = new FormData()
      // Convert blob to file with the original filename if available
      const fileToUpload = new File(
        [blob], 
        selectedFile?.name || 'cropped-banner.png',
        { type: blob.type }
      )
      
      formData.append('file', fileToUpload)
      formData.append('upload_preset', uploadPreset)
      formData.append('tags', 'banner')

      // Upload to Cloudinary
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
      // Clean up object URL
      if (imgSrc) {
        URL.revokeObjectURL(imgSrc)
        setImgSrc('')
      }
      setSelectedFile(null)
    }
  }
  
  // Function called when an image is loaded in the crop interface
  const onImageLoad = useCallback(() => {
    // Create a default crop area that covers most of the image
    // Banner typically has a wider aspect ratio
    setCrop({
      unit: '%',
      width: 90,
      height: 90,
      x: 5,
      y: 5
    })
  }, [])
  
  // Function to handle crop cancellation
  const handleCancelCrop = () => {
    setShowCropModal(false)
    
    // Clean up object URL
    if (imgSrc) {
      URL.revokeObjectURL(imgSrc)
      setImgSrc('')
    }
    setSelectedFile(null)
  }

  // Function to rotate the image
  const handleRotateImage = () => {
    setRotation((prev) => (prev + 90) % 360)
  }

  // Function to get the cropped image from canvas and prepare for upload
  const handleCropComplete = async () => {
    if (!imgRef.current || !completedCrop) return
    
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    
    if (!ctx) {
      setError('Browser does not support canvas operations')
      return
    }
    
    // Set canvas dimensions to the cropped size
    const scaleX = imgRef.current.naturalWidth / imgRef.current.width
    const scaleY = imgRef.current.naturalHeight / imgRef.current.height
    
    canvas.width = completedCrop.width * scaleX
    canvas.height = completedCrop.height * scaleY
    
    // Apply rotation if needed
    if (rotation > 0) {
      ctx.save()
      ctx.translate(canvas.width / 2, canvas.height / 2)
      ctx.rotate((rotation * Math.PI) / 180)
      
      // Adjust for the rotation
      if (rotation === 90 || rotation === 270) {
        ctx.drawImage(
          imgRef.current,
          completedCrop.x * scaleX,
          completedCrop.y * scaleY,
          completedCrop.width * scaleX,
          completedCrop.height * scaleY,
          -canvas.height / 2,
          -canvas.width / 2,
          canvas.height,
          canvas.width
        )
      } else {
        ctx.drawImage(
          imgRef.current,
          completedCrop.x * scaleX,
          completedCrop.y * scaleY,
          completedCrop.width * scaleX,
          completedCrop.height * scaleY,
          -canvas.width / 2,
          -canvas.height / 2,
          canvas.width,
          canvas.height
        )
      }
      ctx.restore()
    } else {
      // No rotation
      ctx.drawImage(
        imgRef.current,
        completedCrop.x * scaleX,
        completedCrop.y * scaleY,
        completedCrop.width * scaleX,
        completedCrop.height * scaleY,
        0,
        0,
        canvas.width,
        canvas.height
      )
    }
    
    // Get the cropped image as blob
    canvas.toBlob((blob) => {
      if (!blob) {
        setError('Failed to process image')
        return
      }
      
      uploadCroppedImage(blob)
    }, 'image/png', 0.95) // Using higher quality for uploads
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
        onClick={() => {
          if (!uploading && fileInputRef.current) {
            fileInputRef.current.click()
          }
        }}
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
      
      {/* Crop Modal */}
      <AnimatePresence>
        {showCropModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
          >
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="max-h-[90vh] w-full max-w-lg overflow-auto rounded-lg bg-slate-800 p-4 shadow-xl"
            >
              <div className="mb-4 flex items-center justify-between">
                <h3 className="text-lg font-medium text-white">Crop Banner</h3>
                <button
                  onClick={handleCancelCrop}
                  className="rounded-full p-1 text-white/70 hover:bg-white/10 hover:text-white"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
              
              <div className="mb-4 overflow-hidden rounded-lg">
                {imgSrc && (
                  <ReactCrop
                    crop={crop}
                    onChange={(_, percentCrop) => setCrop(percentCrop)}
                    onComplete={(c) => setCompletedCrop(c)}
                    // No forced aspect ratio for banner to allow flexible crops
                    className={`max-h-[50vh] w-full bg-slate-900/50 ${rotation ? 'transform' : ''}`}
                    style={{ transform: `rotate(${rotation}deg)` }}
                  >
                    <img
                      ref={imgRef}
                      alt="Crop preview"
                      src={imgSrc}
                      className="max-h-[50vh] w-full object-contain"
                      onLoad={onImageLoad}
                    />
                  </ReactCrop>
                )}
              </div>
              
              <div className="flex justify-between space-x-2">
                <button
                  type="button"
                  onClick={handleRotateImage}
                  className="flex items-center justify-center rounded-md bg-white/15 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-white/20"
                >
                  <RotateCw className="mr-1 h-4 w-4" />
                  Rotate
                </button>
                
                <div className="flex space-x-2">
                  <button
                    type="button"
                    onClick={handleCancelCrop}
                    className="rounded-md bg-white/15 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-white/20"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleCropComplete}
                    className="flex items-center justify-center rounded-md bg-primary px-3 py-2 text-sm font-medium text-white shadow transition-colors hover:bg-primary/90"
                    disabled={!completedCrop}
                  >
                    <Check className="mr-1 h-4 w-4" />
                    Crop & Upload
                  </button>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
