package com.enonic.xp.core.impl.content.processor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.ByteSource;

import com.enonic.xp.attachment.Attachment;
import com.enonic.xp.attachment.CreateAttachment;
import com.enonic.xp.attachment.CreateAttachments;
import com.enonic.xp.content.ContentEditor;
import com.enonic.xp.content.ContentService;
import com.enonic.xp.content.CreateContentParams;
import com.enonic.xp.content.EditableContent;
import com.enonic.xp.content.ExtraData;
import com.enonic.xp.content.ExtraDatas;
import com.enonic.xp.content.Media;
import com.enonic.xp.content.processor.ContentProcessor;
import com.enonic.xp.content.processor.ProcessCreateParams;
import com.enonic.xp.content.processor.ProcessCreateResult;
import com.enonic.xp.content.processor.ProcessUpdateParams;
import com.enonic.xp.content.processor.ProcessUpdateResult;
import com.enonic.xp.data.PropertyTree;
import com.enonic.xp.data.ValueTypes;
import com.enonic.xp.form.FormItem;
import com.enonic.xp.form.FormItemType;
import com.enonic.xp.form.Input;
import com.enonic.xp.image.Cropping;
import com.enonic.xp.inputtype.InputTypeName;
import com.enonic.xp.media.MediaInfo;
import com.enonic.xp.schema.content.ContentType;
import com.enonic.xp.schema.content.ContentTypeName;
import com.enonic.xp.schema.content.ContentTypeService;
import com.enonic.xp.schema.content.GetContentTypeParams;
import com.enonic.xp.schema.xdata.XData;
import com.enonic.xp.schema.xdata.XDataName;
import com.enonic.xp.schema.xdata.XDataService;
import com.enonic.xp.schema.xdata.XDatas;
import com.enonic.xp.util.Exceptions;
import com.enonic.xp.util.GeoPoint;

import static com.enonic.xp.media.MediaInfo.IMAGE_INFO;
import static com.enonic.xp.media.MediaInfo.IMAGE_INFO_IMAGE_HEIGHT;
import static com.enonic.xp.media.MediaInfo.IMAGE_INFO_IMAGE_WIDTH;
import static com.enonic.xp.media.MediaInfo.IMAGE_INFO_PIXEL_SIZE;
import static com.enonic.xp.media.MediaInfo.MEDIA_INFO_BYTE_SIZE;

@Component
public final class ImageContentProcessor
    implements ContentProcessor
{
    private static final Logger LOG = LoggerFactory.getLogger( ImageContentProcessor.class );

    private static final ImmutableMap<String, String> FIELD_CONFORMITY_MAP = ImmutableMap.<String, String>builder().
        put( "tiffImagelength", "imageHeight" ).
        put( "tiffImagewidth", "imageWidth" ).
        put( "exposureBiasValue", "exposureBias" ).
        put( "FNumber", "aperture" ).
        put( "exposureTime", "shutterTime" ).
        put( "subjectDistanceRange", "focusDistance" ).
        put( "gpsAltitude", "altitude" ).
        put( "gpsImgDirection", "direction" ).
        put( "whiteBalanceMode", "whiteBalance" ).
        put( "isoSpeedRatings", "iso" ).
        put( "dcDescription", "description" ).
        put( "iccColorSpace", "colorSpace" ).
        put( "exifSubifdColorSpace", "colorSpace" ).
        put( "dctermsModified", "date" ).
        put( "tiffMake", "make" ).
        put( "tiffModel", "model" ).
        put( "exifSubifdLensModel", "lens" ).
        put( "exifIsospeedratings", "iso" ).
        put( "exifSubifdFocalLength", "focalLength" ).
        put( "exifSubifdExposureBiasValue", "exposureBias" ).
        put( "exifSubifdApertureValue", "aperture" ).
        put( "exifSubifdExposureTime", "shutterTime" ).
        put( "exifSubifdWhiteBalanceMode", "whiteBalance" ).
        put( "exifSubifdExposureProgram", "exposureProgram" ).
        put( "exifSubifdMeteringMode", "meteringMode" ).
        put( "exifSubifdExposureMode", "exposureMode" ).
        put( "exifIfd0Orientation", "orientation" ).
        put( "globalAltitude", "altitude" ).
        put( "exifSubifdFlash", "flash" ).
        build();

    private static final String GEO_LONGITUDE = "geoLong";

    private static final String GEO_LATITUDE = "geoLat";

    private ContentService contentService;

    private ContentTypeService contentTypeService;

    private XDataService xDataService;

    @Override
    public boolean supports( final ContentType contentType )
    {
        return contentType.getName().isImageMedia();
    }

    private XDatas getXDatas( final ContentTypeName contentTypeName )
    {
        final ContentType contentType = contentTypeService.getByName( GetContentTypeParams.from( contentTypeName ) );
        return xDataService.getFromContentType( contentType );
    }

    @Override
    public ProcessCreateResult processCreate( final ProcessCreateParams params )
    {
        final CreateContentParams createContentParams = params.getCreateContentParams();
        final MediaInfo mediaInfo = params.getMediaInfo();

        final CreateAttachments originalAttachments = createContentParams.getCreateAttachments();
        Preconditions.checkArgument( originalAttachments.getSize() == 1, "Expected only one attachment" );

        final CreateAttachment sourceAttachment = originalAttachments.first();

        final XDatas contentXDatas = getXDatas( createContentParams.getType() );
        ExtraDatas extraDatas = null;

        if ( mediaInfo != null )
        {
            extraDatas = extractMetadata( mediaInfo, contentXDatas, sourceAttachment );
        }

        final CreateAttachments.Builder builder = CreateAttachments.create();
        builder.add( sourceAttachment );

        return new ProcessCreateResult( CreateContentParams.create( createContentParams ).
            createAttachments( builder.build() ).extraDatas( extraDatas ).
            build() );
    }

    @Override
    public ProcessUpdateResult processUpdate( final ProcessUpdateParams params )
    {
        final CreateAttachments createAttachments = params.getCreateAttachments();
        final MediaInfo mediaInfo = params.getMediaInfo();

        final CreateAttachment sourceAttachment = createAttachments == null ? null : createAttachments.first();

        final ContentEditor editor;
        if ( mediaInfo != null )
        {
            editor = editable -> {

                final Map<XDataName, ExtraData> extraDatas =
                    editable.extraDatas.stream().collect( Collectors.toMap( ExtraData::getName, o -> o ) );

                final XDatas contentXDatas = getXDatas( params.getContentType().getName() );

                extractMetadata( mediaInfo, contentXDatas, sourceAttachment ).
                    forEach( extraData -> extraDatas.put( extraData.getName(), extraData ) );

                editable.extraDatas = ExtraDatas.create().addAll( extraDatas.values() ).build();
            };
        }
        else
        {
            editor = editable -> {

                if ( !params.getContentType().getName().isDescendantOfMedia() )
                {
                    return;
                }

                editable.extraDatas = updateImageMetadata( editable );

            };
        }
        return new ProcessUpdateResult( createAttachments, editor );

    }


    private ExtraDatas updateImageMetadata( final EditableContent editable )
    {
        final Media media = (Media) editable.source;
        final Attachment mediaAttachment = media.getMediaAttachment();
        if ( mediaAttachment == null )
        {
            return editable.extraDatas;
        }

        final ByteSource binary = contentService.getBinary( editable.source.getId(), mediaAttachment.getBinaryReference() );
        if ( binary == null )
        {
            return editable.extraDatas;
        }

        final BufferedImage image = toBufferedImage( binary );
        if ( image == null )
        {
            return editable.extraDatas;
        }

        final Cropping cropping = media.getCropping();
        final long byteSize = mediaAttachment.getSize();

        final long imageWidth;
        final long imageHeight;
        final long imageSize;
        if ( cropping == null || cropping.isUnmodified() )
        {
            imageWidth = image.getWidth();
            imageHeight = image.getHeight();
            imageSize = imageWidth * imageHeight;
        }
        else
        {
            final BufferedImage croppedImage = cropImage( image, cropping );
            imageWidth = croppedImage.getWidth();
            imageHeight = croppedImage.getHeight();
            imageSize = imageWidth * imageHeight;
        }

        ExtraData extraData = editable.extraDatas.getMetadata( MediaInfo.IMAGE_INFO_METADATA_NAME );
        if ( extraData != null )
        {
            final PropertyTree xData = extraData.getData();
            setLongProperty( xData, IMAGE_INFO_PIXEL_SIZE, imageSize );
            setLongProperty( xData, IMAGE_INFO_IMAGE_HEIGHT, imageHeight );
            setLongProperty( xData, IMAGE_INFO_IMAGE_WIDTH, imageWidth );
            setLongProperty( xData, MEDIA_INFO_BYTE_SIZE, byteSize );
        }

        return editable.extraDatas;
    }

    private void setLongProperty( final PropertyTree propertyTree, final String path, final Long value )
    {
        propertyTree.removeProperties( path );
        propertyTree.setLong( path, value );
    }

    private BufferedImage toBufferedImage( final ByteSource source )
    {
        try (InputStream stream = source.openStream())
        {
            return ImageIO.read( stream );
        }
        catch ( IOException e )
        {
            LOG.warn( "Failed to read BufferedImage from InputStream", e );
            return null;
        }
    }

    private BufferedImage cropImage( final BufferedImage image, final Cropping cropping )
    {
        final double width = image.getWidth();
        final double height = image.getHeight();
        return image.getSubimage( (int) ( width * cropping.left() ), (int) ( height * cropping.top() ), (int) ( width * cropping.width() ),
                                  (int) ( height * cropping.height() ) );
    }

    private ExtraDatas extractMetadata( final MediaInfo mediaInfo, final XDatas xDatas, final CreateAttachment sourceAttachment )
    {
        final ExtraDatas.Builder extradatasBuilder = ExtraDatas.create();
        final Map<XDataName, ExtraData> metadataMap = new HashMap<>();
        final ExtraData geoData = extractGeoLocation( mediaInfo, xDatas );
        if ( geoData != null )
        {
            metadataMap.put( MediaInfo.GPS_INFO_METADATA_NAME, geoData );
            extradatasBuilder.add( geoData );
        }
        for ( Map.Entry<String, Collection<String>> entry : mediaInfo.getMetadata().asMap().entrySet() )
        {
            for ( XData xData : xDatas )
            {
                final String formItemName = getConformityName( entry.getKey() );
                final FormItem formItem = xData.getForm().getFormItems().getItemByName( formItemName );
                if ( formItem == null )
                {
                    continue;
                }
                ExtraData extraData = metadataMap.get( xData.getName() );
                if ( extraData == null )
                {
                    extraData = new ExtraData( xData.getName(), new PropertyTree() );
                    metadataMap.put( xData.getName(), extraData );
                    extradatasBuilder.add( extraData );
                }
                if ( FormItemType.INPUT.equals( formItem.getType() ) )
                {
                    Input input = (Input) formItem;
                    if ( InputTypeName.DATE_TIME.equals( input.getInputType() ) )
                    {
                        extraData.getData().addLocalDateTime( formItemName,
                                                              ValueTypes.LOCAL_DATE_TIME.convert( entry.getValue().toArray()[0] ) );
                    }
                    else if ( InputTypeName.LONG.equals( input.getInputType() ) )
                    {
                        final Long[] longValues = entry.getValue().stream().map( Long::parseLong ).toArray( Long[]::new );
                        extraData.getData().addLongs( formItemName, longValues );
                    }
                    else
                    {
                        extraData.getData().addStrings( formItemName, entry.getValue() );
                    }
                }
            }
        }
        fillComputedFormItems( metadataMap.values(), mediaInfo, sourceAttachment );
        return extradatasBuilder.build();
    }

    private ExtraData extractGeoLocation( final MediaInfo mediaInfo, final XDatas xDatas )
    {
        final ImmutableMultimap<String, String> mediaItems = mediaInfo.getMetadata();
        final Double geoLat = parseDouble( mediaItems.get( GEO_LATITUDE ).stream().findFirst().orElse( null ) );
        final Double geoLong = parseDouble( mediaItems.get( GEO_LONGITUDE ).stream().findFirst().orElse( null ) );
        if ( geoLat == null || geoLong == null )
        {
            return null;
        }
        final XData geoMixin = xDatas.getXData( MediaInfo.GPS_INFO_METADATA_NAME );
        if ( geoMixin == null )
        {
            return null;
        }
        final ExtraData extraData = new ExtraData( geoMixin.getName(), new PropertyTree() );
        final FormItem formItem = geoMixin.getForm().getFormItems().getItemByName( MediaInfo.GPS_INFO_GEO_POINT );
        if ( FormItemType.INPUT.equals( formItem.getType() ) )
        {
            final Input input = (Input) formItem;
            if ( InputTypeName.GEO_POINT.equals( input.getInputType() ) )
            {
                final GeoPoint geoPoint = new GeoPoint( geoLat, geoLong );
                extraData.getData().addGeoPoint( formItem.getName(), ValueTypes.GEO_POINT.convert( geoPoint ) );
            }
        }
        return extraData;
    }

    private Double parseDouble( final String str )
    {
        if ( str == null )
        {
            return null;
        }
        try
        {
            return Double.parseDouble( str );
        }
        catch ( NumberFormatException e )
        {
            return null;
        }
    }

    private String getConformityName( String tikaFieldValue )
    {
        if ( FIELD_CONFORMITY_MAP.containsValue( tikaFieldValue ) )
        {
            return null;
        }
        return FIELD_CONFORMITY_MAP.getOrDefault( tikaFieldValue, tikaFieldValue );
    }

    private void fillComputedFormItems( Collection<ExtraData> extraDataList, MediaInfo mediaInfo, final CreateAttachment sourceAttachment )
    {
        for ( ExtraData extraData : extraDataList )
        {
            final PropertyTree xData = extraData.getData();
            if ( IMAGE_INFO.equals( extraData.getName().getLocalName() ) )
            {
                final Collection<String> tiffImageLengths = mediaInfo.getMetadata().get( "tiffImagelength" );
                final Collection<String> tiffImageWidths = mediaInfo.getMetadata().get( "tiffImagewidth" );
                if ( !tiffImageLengths.isEmpty() && !tiffImageWidths.isEmpty() )
                {
                    final long tiffImageLength = Long.parseLong( tiffImageLengths.toArray()[0].toString() );
                    final long tiffImageWidth = Long.parseLong( tiffImageWidths.toArray()[0].toString() );
                    xData.setLong( IMAGE_INFO_PIXEL_SIZE, tiffImageLength * tiffImageWidth );
                    xData.setLong( IMAGE_INFO_IMAGE_HEIGHT, tiffImageLength );
                    xData.setLong( IMAGE_INFO_IMAGE_WIDTH, tiffImageWidth );
                }
                if ( sourceAttachment != null )
                {
                    try
                    {
                        long mediaInfoByteSize = sourceAttachment.getByteSource().size();
                        xData.setLong( MEDIA_INFO_BYTE_SIZE, mediaInfoByteSize );
                    }
                    catch ( IOException e )
                    {
                        throw Exceptions.newRuntime( "Failed to read BufferedImage from InputStream" ).withCause( e );
                    }
                }
            }
        }
    }

    @Reference
    public void setXDataService( final XDataService xDataService )
    {
        this.xDataService = xDataService;
    }

    @Reference
    public void setContentService( final ContentService contentService )
    {
        this.contentService = contentService;
    }

    @Reference
    public void setContentTypeService( final ContentTypeService contentTypeService )
    {
        this.contentTypeService = contentTypeService;
    }

}
