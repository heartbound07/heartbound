package com.app.heartbound.config.security;

/**
 * JSON View definitions for role-based serialization.
 * Used to control what data is exposed to users based on their privileges.
 */
public class Views {
    
    /**
     * Public view - data visible to all authenticated users
     */
    public interface Public {}
    
    /**
     * Admin view - extends Public view with additional administrative data
     */
    public interface Admin extends Public {}
} 