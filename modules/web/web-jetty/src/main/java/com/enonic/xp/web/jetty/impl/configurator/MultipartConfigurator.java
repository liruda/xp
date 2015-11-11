package com.enonic.xp.web.jetty.impl.configurator;

import javax.servlet.MultipartConfigElement;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.common.base.Strings;

public final class MultipartConfigurator
    extends JettyConfigurator<ServletHolder>
{
    private final static long MB = 1024 * 1024;

    @Override
    protected void doConfigure()
    {
        final MultipartConfigElement elem = newMultipartConfig();
        this.object.getRegistration().setMultipartConfig( elem );
    }

    private MultipartConfigElement newMultipartConfig()
    {
        return new MultipartConfigElement( getStore(), getMaxFileSize(), getMaxRequestSize(), getFileSizeThreshold() );
    }

    private String getStore()
    {
        final String location = this.config.multipart_store();
        return Strings.isNullOrEmpty( location ) ? FileUtils.getTempDirectoryPath() : location;
    }

    private long getMaxFileSize()
    {
        return this.config.multipart_maxFileSize() * MB;
    }

    private long getMaxRequestSize()
    {
        return this.config.multipart_maxRequestSize() * MB;
    }

    private int getFileSizeThreshold()
    {
        return this.config.multipart_fileSizeThreshold();
    }
}