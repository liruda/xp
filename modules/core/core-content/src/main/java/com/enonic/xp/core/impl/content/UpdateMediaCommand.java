package com.enonic.xp.core.impl.content;

import java.util.List;

import com.google.common.base.Preconditions;

import com.enonic.xp.attachment.CreateAttachment;
import com.enonic.xp.attachment.CreateAttachments;
import com.enonic.xp.content.Content;
import com.enonic.xp.content.UpdateContentParams;
import com.enonic.xp.content.UpdateMediaParams;
import com.enonic.xp.media.MediaInfo;
import com.enonic.xp.media.MediaInfoService;
import com.enonic.xp.schema.content.ContentTypeFromMimeTypeResolver;
import com.enonic.xp.schema.content.ContentTypeName;

final class UpdateMediaCommand
    extends AbstractCreatingOrUpdatingContentCommand
{
    private final UpdateMediaParams params;

    private final MediaInfoService mediaInfoService;

    private UpdateMediaCommand( final Builder builder )
    {
        super( builder );
        this.params = builder.params;
        this.mediaInfoService = builder.mediaInfoService;
    }

    public static Builder create( final UpdateMediaParams params )
    {
        return new Builder( params );
    }

    public static Builder create( final UpdateMediaParams params, final AbstractCreatingOrUpdatingContentCommand source )
    {
        return new Builder( params, source );
    }

    Content execute()
    {
        params.validate();

        return doExecute();
    }

    private Content doExecute()
    {
        final MediaInfo mediaInfo = mediaInfoService.parseMediaInfo( params.getByteSource() );
        if ( ( params.getMimeType() == null || isBinaryContentType( params.getMimeType() ) ) && mediaInfo.getMediaType() != null )
        {
            params.mimeType( mediaInfo.getMediaType() );
        }

        Preconditions.checkNotNull( params.getMimeType(), "Unable to resolve media type" );

        final ContentTypeName resolvedTypeFromMimeType = ContentTypeFromMimeTypeResolver.resolve( params.getMimeType() );
        final ContentTypeName type = resolvedTypeFromMimeType != null
            ? resolvedTypeFromMimeType
            : isExecutableContentType( params.getMimeType(), params.getName() )
                ? ContentTypeName.executableMedia()
                : ContentTypeName.unknownMedia();

        final Content existingContent = getContent( params.getContent() );
        Preconditions.checkArgument( existingContent.getType().equals( type ),
                                     "Updated content must be of type: " + existingContent.getType() );

        final CreateAttachment mediaAttachment = CreateAttachment.create()
            .name( params.getName() )
            .mimeType( params.getMimeType() )
            .label( "source" )
            .byteSource( params.getByteSource() )
            .text( type.isTextualMedia() ? mediaInfo.getTextContent() : null )
            .build();

        final MediaFormDataBuilder mediaFormBuilder = new MediaFormDataBuilder().type( type )
            .attachment( params.getName() )
            .focalX( params.getFocalX() )
            .focalY( params.getFocalY() )
            .caption( params.getCaption() )
            .artist( params.getArtistList().isEmpty() ? List.of( "" ) : params.getArtistList() )
            .copyright( params.getCopyright() )
            .tags( params.getTagList().isEmpty() ? List.of("") : params.getTagList() );

        final UpdateContentParams updateParams = new UpdateContentParams().contentId( params.getContent() )
            .clearAttachments( true )
            .createAttachments( CreateAttachments.from( mediaAttachment ) )
            .editor( editable -> {
                mediaFormBuilder.build( editable.data );
                editable.workflowInfo = params.getWorkflowInfo();
            } );

        return UpdateContentCommand.create( this )
            .params( updateParams )
            .mediaInfo( mediaInfo )
            .contentTypeService( this.contentTypeService )
            .siteService( this.siteService )
            .xDataService( this.xDataService )
            .pageDescriptorService( this.pageDescriptorService )
            .partDescriptorService( this.partDescriptorService )
            .layoutDescriptorService( this.layoutDescriptorService )
            .build()
            .execute();
    }

    public static class Builder
        extends AbstractCreatingOrUpdatingContentCommand.Builder<Builder>
    {
        private final UpdateMediaParams params;

        private MediaInfoService mediaInfoService;

        Builder( final UpdateMediaParams params )
        {
            this.params = params;
        }

        Builder( final UpdateMediaParams params, final AbstractCreatingOrUpdatingContentCommand source )
        {
            super( source );
            this.params = params;
        }

        public Builder mediaInfoService( final MediaInfoService value )
        {
            this.mediaInfoService = value;
            return this;
        }

        @Override
        void validate()
        {
            super.validate();
            Preconditions.checkNotNull( params );
        }

        public UpdateMediaCommand build()
        {
            validate();
            return new UpdateMediaCommand( this );
        }

    }

}
