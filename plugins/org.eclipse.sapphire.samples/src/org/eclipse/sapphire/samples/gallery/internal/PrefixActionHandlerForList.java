/******************************************************************************
 * Copyright (c) 2016 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Konstantin Komissarchik - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.samples.gallery.internal;

import org.eclipse.sapphire.DisposeEvent;
import org.eclipse.sapphire.Element;
import org.eclipse.sapphire.ElementList;
import org.eclipse.sapphire.FilteredListener;
import org.eclipse.sapphire.Listener;
import org.eclipse.sapphire.PropertyContentEvent;
import org.eclipse.sapphire.ValueProperty;
import org.eclipse.sapphire.ui.Presentation;
import org.eclipse.sapphire.ui.SapphireAction;
import org.eclipse.sapphire.ui.def.ActionHandlerDef;
import org.eclipse.sapphire.ui.forms.PropertyEditorActionHandler;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

public final class PrefixActionHandlerForList extends PropertyEditorActionHandler
{
    private String prefix;
    
    @Override
    public void init( final SapphireAction action,
                      final ActionHandlerDef def )
    {
        super.init( action, def );
        
        this.prefix = def.getParam( "prefix" ) + " ";
        
        final Element element = getModelElement();
        
        final Listener listener = new FilteredListener<PropertyContentEvent>()
        {
            @Override
            protected void handleTypedEvent( final PropertyContentEvent event )
            {
                refreshEnabledState();
                refreshCheckedState();
            }
        };
        
        final String path = property().name() + "/*";
        
        element.attach( listener, path );
        
        refreshEnabledState();
        refreshCheckedState();
        
        attach
        (
            new FilteredListener<DisposeEvent>()
            {
                @Override
                protected void handleTypedEvent( final DisposeEvent event )
                {
                    element.detach( listener, path );                        
                }
            }
        );
    }
    
    @Override
    protected Object run( final Presentation context )
    {
        final ElementList<?> list = (ElementList<?>) property();
        
        if( ! list.isEmpty() )
        {
            final Element element = list.get( 0 );
            final ValueProperty property = (ValueProperty) element.properties().first().definition();
            final String oldValue = element.property( property ).text();
            final String newValue;
            
            if( isChecked() )
            {
                newValue = this.prefix + oldValue;
            }
            else
            {
                if( oldValue.startsWith( this.prefix ) )
                {
                    if( oldValue.length() == this.prefix.length() )
                    {
                        newValue = null;
                    }
                    else
                    {
                        newValue = oldValue.substring( this.prefix.length() );
                    }
                }
                else
                {
                    newValue = oldValue;
                }
            }
            
            element.property( property ).write( newValue );
        }
        
        return null;
    }
    
    private void refreshEnabledState()
    {
        setEnabled( ! property().empty() );
    }

    private void refreshCheckedState()
    {
        boolean checked = false;
        
        final ElementList<?> list = (ElementList<?>) property();
        
        if( ! list.isEmpty() )
        {
            final Element element = list.get( 0 );
            final ValueProperty property = (ValueProperty) element.properties().first().definition();
            final String value = element.property( property ).text();
            checked = ( value != null && value.startsWith( this.prefix ) );
        }
        
        setChecked( checked );
    }
    
}
