import React, { useState, useRef, useCallback } from 'react'
import { Upload, X, Check, RotateCw } from 'lucide-react'
import ReactCrop, { Crop, PixelCrop, centerCrop, makeAspectCrop } from 'react-image-crop'
import 'react-image-crop/dist/ReactCrop.css'
import { motion, AnimatePresence } from 'framer-motion'

interface BadgeUploadProps {
  currentImageUrl?: string
  onUpload: (url: string) => void
  onRemove?: () => void
  showRemoveButton?: boolean
  className?: string
}

export function BadgeUpload({ 
  currentImageUrl, 
  onUpload, 
  onRemove,
  showRemoveButton = false,
  className = '' 
}: BadgeUploadProps) {
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
  const allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp']
  const acceptAttribute = "image/jpeg, image/png, image/gif, image/webp, image/jpg"

  // Get Cloudinary settings from environment variables
  const cloudName = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME as string
  const uploadPreset = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET as string

  // When file is selected, prepare for cropping instead of directly uploading
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

  // Function to center and set initial crop to a circle (aspect ratio 1:1)
  const onImageLoad = useCallback((e: React.SyntheticEvent<HTMLImageElement>) => {
    const { width, height } = e.currentTarget
    
    // Make a centered 1:1 crop for circle
    const crop = centerCrop(
      makeAspectCrop(
        {
          unit: '%',
          width: 90,
        },
        1, // Enforce 1:1 aspect ratio for circular crop
        width,
        height
      ),
      width,
      height
    )
    
    setCrop(crop)
  }, [])

  // Rotate the image
  const handleRotateImage = () => {
    setRotation((prev) => (prev + 90) % 360)
  }

  // Cancel cropping and reset states
  const handleCancelCrop = () => {
    setShowCropModal(false)
    setImgSrc('')
    setSelectedFile(null)
    
    // Clean up object URL
    if (imgSrc) {
      URL.revokeObjectURL(imgSrc)
    }
  }

  // When crop is complete, upload the cropped image
  const handleCropComplete = async () => {
    if (!completedCrop || !imgRef.current || !selectedFile) return
    
    setUploading(true)
    
    try {
      // Create canvas with the cropped portion
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')
      
      if (!ctx) {
        throw new Error('No 2d context')
      }
      
      // Set canvas size to the crop size
      const scaleX = imgRef.current.naturalWidth / imgRef.current.width
      const scaleY = imgRef.current.naturalHeight / imgRef.current.height
      
      canvas.width = completedCrop.width * scaleX
      canvas.height = completedCrop.height * scaleY
      
      // If there's rotation, handle it in the canvas context
      if (rotation) {
        ctx.save()
        ctx.translate(canvas.width / 2, canvas.height / 2)
        ctx.rotate((rotation * Math.PI) / 180)
        ctx.translate(-canvas.width / 2, -canvas.height / 2)
      }
      
      // Draw the cropped image on the canvas
      ctx.drawImage(
        imgRef.current,
        completedCrop.x * scaleX,
        completedCrop.y * scaleY,
        completedCrop.width * scaleX,
        completedCrop.height * scaleY,
        0,
        0,
        completedCrop.width * scaleX,
        completedCrop.height * scaleY
      )
      
      if (rotation) {
        ctx.restore()
      }
      
      
      // Create a circular mask using a second canvas
      const circularCanvas = document.createElement('canvas')
      const circularCtx = circularCanvas.getContext('2d')
      
      if (!circularCtx) {
        throw new Error('No 2d context for circular canvas')
      }
      
      // Set circular canvas dimensions (same as first canvas)
      circularCanvas.width = canvas.width
      circularCanvas.height = canvas.height
      
      // Create circular clipping path
      circularCtx.beginPath()
      circularCtx.arc(
        canvas.width / 2,
        canvas.height / 2,
        Math.min(canvas.width, canvas.height) / 2,
        0,
        Math.PI * 2
      )
      circularCtx.closePath()
      circularCtx.clip()
      
      // Draw the original cropped image onto the circular canvas
      circularCtx.drawImage(canvas, 0, 0)
      
      // Convert circular canvas to blob
      const circularBlob = await new Promise<Blob>((resolve) => {
        circularCanvas.toBlob((blob) => {
          if (blob) resolve(blob)
          else throw new Error('Circular canvas to Blob conversion failed')
        }, 'image/png')
      })
      
      // Create a new file from the blob with a new name
      const fileExtension = 'png'
      const fileName = `badge_${Date.now()}.${fileExtension}`
      const circularFile = new File([circularBlob], fileName, { type: `image/${fileExtension}` })
      
      // Upload to Cloudinary
      const formData = new FormData()
      formData.append('file', circularFile)
      formData.append('upload_preset', uploadPreset)
      formData.append('folder', 'heartbound/badges')
      
      const response = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/image/upload`, {
        method: 'POST',
        body: formData,
      })
      
      if (!response.ok) {
        throw new Error(`Upload failed with status: ${response.status}`)
      }
      
      const data = await response.json()
      
      // Pass the secure URL to the parent component
      onUpload(data.secure_url)
      
      // Close the crop modal and reset states
      setShowCropModal(false)
      setImgSrc('')
      setSelectedFile(null)
      
    } catch (err) {
      console.error('Error during image processing or upload:', err)
      setError('Failed to process or upload image')
    } finally {
      setUploading(false)
      
      // Clean up object URL
      if (imgSrc) {
        URL.revokeObjectURL(imgSrc)
      }
    }
  }

  // Function to initiate file upload via the hidden input
  const handleClickUpload = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click()
    }
  }

  return (
    <div className={`relative ${className}`}>
      <input
        type="file"
        ref={fileInputRef}
        className="hidden"
        accept={acceptAttribute}
        onChange={handleFileChange}
      />
      
      {/* Current badge display or upload button */}
      <div
        className="relative flex h-48 w-48 cursor-pointer items-center justify-center overflow-hidden rounded-full border-2 border-dashed border-white/30 bg-black/20 transition-all"
        onMouseEnter={() => setIsHovering(true)}
        onMouseLeave={() => setIsHovering(false)}
        onClick={handleClickUpload}
      >
        {/* Show current badge if available */}
        {currentImageUrl ? (
          <>
            <img
              src={currentImageUrl}
              alt="Badge"
              className="h-full w-full object-cover rounded-full"
            />
            <div
              className={`absolute inset-0 flex items-center justify-center rounded-full bg-black/60 transition-opacity ${
                isHovering ? 'opacity-100' : 'opacity-0'
              }`}
            >
              <div className="flex flex-col items-center justify-center">
                <Upload className="mb-2 h-8 w-8 text-white/90" />
                <span className="text-sm font-medium text-white/90">Change Badge</span>
              </div>
            </div>
          </>
        ) : (
          // Show upload button if no current image
          <div className="flex flex-col items-center justify-center p-4 text-center">
            <Upload className="mb-2 h-8 w-8 text-white/90" />
            <span className="text-sm font-medium text-white/90">Upload Badge Image</span>
            <span className="mt-1 text-xs text-white/60">Click to browse</span>
          </div>
        )}
        
        {/* Show loading indicator while uploading */}
        {uploading && (
          <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/80">
            <div className="h-10 w-10 animate-spin rounded-full border-4 border-white/20 border-t-white/90"></div>
          </div>
        )}
      </div>
      
      {/* Remove button */}
      {showRemoveButton && currentImageUrl && onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation()
            onRemove()
          }}
          className="absolute -right-2 -top-2 flex h-8 w-8 items-center justify-center rounded-full bg-red-500 text-white shadow-md hover:bg-red-600 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
        >
          <X className="h-5 w-5" />
        </button>
      )}
      
      {/* Error message */}
      {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
      
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
                <h3 className="text-lg font-medium text-white">Crop Badge (Circular)</h3>
                <button
                  onClick={handleCancelCrop}
                  className="rounded-full p-1 text-white/70 hover:bg-white/10 hover:text-white"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
              
              <div className="mb-4 overflow-hidden rounded-lg">
                {imgSrc && (
                  <div className="relative">
                    <ReactCrop
                      crop={crop}
                      onChange={(_, percentCrop) => setCrop(percentCrop)}
                      onComplete={(c) => setCompletedCrop(c)}
                      aspect={1} // Force 1:1 aspect ratio for circular crop
                      circularCrop // Enable circular crop UI
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
                    
                    {/* Circular overlay to show the user how the badge will look */}
                    {crop && (
                      <div className="absolute top-0 left-0 h-full w-full pointer-events-none flex items-center justify-center">
                        <div className="border-2 border-white/60 rounded-full" 
                          style={{
                            width: `${crop.width}%`,
                            height: `${crop.height}%`,
                            transform: `translate(${(crop.x - (100 - crop.width) / 2)}%, ${(crop.y - (100 - crop.height) / 2)}%)`,
                          }}>
                        </div>
                      </div>
                    )}
                  </div>
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
                    disabled={!completedCrop || uploading}
                  >
                    {uploading ? (
                      <>
                        <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/20 border-t-white/90"></div>
                        Processing...
                      </>
                    ) : (
                      <>
                        <Check className="mr-1 h-4 w-4" />
                        Crop & Upload
                      </>
                    )}
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
