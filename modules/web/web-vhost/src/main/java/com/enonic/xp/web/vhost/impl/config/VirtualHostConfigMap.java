package com.enonic.xp.web.vhost.impl.config;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.enonic.xp.security.IdProviderKey;
import com.enonic.xp.web.vhost.VirtualHost;
import com.enonic.xp.web.vhost.impl.mapping.VirtualHostIdProvidersMapping;
import com.enonic.xp.web.vhost.impl.mapping.VirtualHostMapping;

import static com.google.common.base.Strings.isNullOrEmpty;

final class VirtualHostConfigMap
{
    private static final String DEFAULT_ID_PROVIDER_VALUE = "default";

    private static final String ENABLED_ID_PROVIDER_VALUE = "enabled";

    private static final Pattern MAPPING_NAME_PATTERN = Pattern.compile( "mapping\\.(?<name>[^.]+)\\..+" );

    private final Map<String, String> map;

    VirtualHostConfigMap( final Map<String, String> map )
    {
        this.map = map;
    }

    public boolean isEnabled()
    {
        return getBoolean( "enabled", false );
    }

    public List<VirtualHost> buildMappings()
    {
        return findMappingNames().stream()
            .map( this::buildMapping )
            .sorted( Comparator.comparing( VirtualHost::getOrder )
                         .thenComparing( VirtualHost::getSource, Comparator.comparing( String::length ).reversed() )
                         .thenComparing( VirtualHost::getSource ) )
            .collect( Collectors.toUnmodifiableList() );
    }

    private VirtualHostMapping buildMapping( final String name )
    {

        final String prefix = "mapping." + name + ".";
        final String hostString = getString( prefix + "host" );
        final String host = hostString != null ? hostString : "localhost";

        final String source = normalizePath( getString( prefix + "source" ) );
        final String target = normalizePath( getString( prefix + "target" ) );
        final VirtualHostIdProvidersMapping idProvidersMapping = getHostIdProvidersMapping( prefix );
        final int order = getInt( prefix + "order", Integer.MAX_VALUE );

        return new VirtualHostMapping( name, host, source, target, idProvidersMapping, order );
    }

    private VirtualHostIdProvidersMapping getHostIdProvidersMapping( final String mappingPrefix )
    {
        final String idProviderPrefix = mappingPrefix + "idProvider" + ".";

        final VirtualHostIdProvidersMapping.Builder hostIdProvidersMapping = VirtualHostIdProvidersMapping.create();

        getIdProviders( idProviderPrefix ).forEach( ( idProviderName, idProviderStatus ) -> {

            final IdProviderKey idProviderKey = IdProviderKey.from( idProviderName );

            if ( DEFAULT_ID_PROVIDER_VALUE.equals( idProviderStatus ) )
            {
                hostIdProvidersMapping.setDefaultIdProvider( idProviderKey );
            }
            if ( ENABLED_ID_PROVIDER_VALUE.equals( idProviderStatus ) )
            {
                hostIdProvidersMapping.addIdProviderKey( idProviderKey );
            }

        } );

        return hostIdProvidersMapping.build();
    }

    private Map<String, String> getIdProviders( final String idProviderPrefix )
    {
        return this.map.entrySet()
            .stream()
            .filter( entry -> entry.getKey().startsWith( idProviderPrefix ) )
            .collect( Collectors.toMap( entry -> entry.getKey().replace( idProviderPrefix, "" ), Map.Entry::getValue ) );
    }

    private String getString( final String name )
    {
        final String value = this.map.get( name );
        if ( isNullOrEmpty( value ) )
        {
            return null;
        }

        return value.trim();
    }

    private boolean getBoolean( final String name, final boolean defValue )
    {
        final String value = getString( name );
        return value != null ? "true".equals( value ) : defValue;
    }

    private int getInt( final String name, final int defValue )
    {
        final String value = getString( name );
        return value != null ? Integer.parseInt( value ) : defValue;
    }

    private Set<String> findMappingNames()
    {
        return this.map.keySet().stream().map( this::findMappingName ).filter( Objects::nonNull ).collect( Collectors.toSet() );
    }

    private String findMappingName( final String key )
    {
        final Matcher matcher = MAPPING_NAME_PATTERN.matcher( key );
        if ( !matcher.matches() )
        {
            return null;
        }

        return matcher.group( "name" );
    }

    private String normalizePath( final String value )
    {
        if ( value == null || "/".equals( value ) )
        {
            return "/";
        }

        final StringBuilder result = new StringBuilder();

        if ( !value.startsWith( "/" ) )
        {
            result.append( "/" );
        }

        if ( value.endsWith( "/" ) )
        {
            result.append( value, 0, value.length() - 1 );
        }
        else
        {
            result.append( value );
        }

        return result.toString();
    }
}
