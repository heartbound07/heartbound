import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Home, AlertTriangle, ArrowLeft } from 'lucide-react';
import { useAuth } from '@/contexts/auth/useAuth';

export function NotFoundPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const handleGoHome = () => {
    // Redirect authenticated users to dashboard, unauthenticated to login
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    } else {
      navigate('/login', { replace: true });
    }
  };

  const handleGoBack = () => {
    // Go back in history, but fallback to home if no history
    if (window.history.length > 1) {
      navigate(-1);
    } else {
      handleGoHome();
    }
  };

  return (
    <div className="min-h-screen bg-theme-gradient flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.9, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ duration: 0.5, ease: "easeOut" }}
        className="w-full max-w-md"
      >
        <Card className="valorant-card text-center">
          <CardContent className="p-8 space-y-6">
            {/* Error Icon */}
            <motion.div
              initial={{ rotate: -10 }}
              animate={{ rotate: 0 }}
              transition={{ delay: 0.2, type: "spring", stiffness: 200 }}
              className="flex justify-center"
            >
              <div className="p-4 bg-status-error/10 rounded-full border border-status-error/20">
                <AlertTriangle className="h-16 w-16 text-status-error" />
              </div>
            </motion.div>

            {/* Error Code */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.3 }}
            >
              <h1 className="text-6xl font-bold text-primary mb-2">404</h1>
              <h2 className="text-2xl font-semibold text-white mb-3">
                Page Not Found
              </h2>
              <p className="text-theme-secondary leading-relaxed">
                The page you're looking for doesn't exist or has been moved. 
                This could be due to a typo in the URL or an outdated link.
              </p>
            </motion.div>

            {/* Action Buttons */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4 }}
              className="space-y-3"
            >
              <Button
                onClick={handleGoHome}
                className="w-full valorant-button-primary h-12"
                size="lg"
              >
                <Home className="h-5 w-5 mr-2" />
                {isAuthenticated ? 'Go to Dashboard' : 'Go to Login'}
              </Button>
              
              <Button
                onClick={handleGoBack}
                variant="outline"
                className="w-full h-12 border-theme text-theme-secondary hover:border-primary/50 hover:text-primary"
                size="lg"
              >
                <ArrowLeft className="h-5 w-5 mr-2" />
                Go Back
              </Button>
            </motion.div>

            {/* Additional Help Text */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.6 }}
              className="pt-4 border-t border-theme"
            >
              <p className="text-sm text-theme-tertiary">
                If you believe this is an error, please contact support or check the URL for typos.
              </p>
            </motion.div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
} 