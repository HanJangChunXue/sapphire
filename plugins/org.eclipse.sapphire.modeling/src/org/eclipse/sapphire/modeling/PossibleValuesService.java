/******************************************************************************
 * Copyright (c) 2011 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Konstantin Komissarchik - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.modeling;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.sapphire.modeling.util.NLS;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

public abstract class PossibleValuesService

    extends ModelPropertyService
    
{
    public final SortedSet<String> values()
    {
        final TreeSet<String> values = new TreeSet<String>();
        fillPossibleValues( values );
        return Collections.unmodifiableSortedSet( values );
    }
    
    protected abstract void fillPossibleValues( final SortedSet<String> values );
    
    public String getInvalidValueMessage( final String invalidValue )
    {
        return NLS.bind( Resources.defaultInvalidValueMessage, invalidValue, property().getLabel( true, CapitalizationType.NO_CAPS, false ) );
    }
    
    public Status.Severity getInvalidValueSeverity( final String invalidValue )
    {
        return Status.Severity.ERROR;
    }
    
    public boolean isCaseSensitive()
    {
        return true;
    }
    
    /**
     * Returns the label to use when presenting a given value to the user. The default implementation 
     * returns the value itself. If an unrecognized value is encountered, the implementation should
     * return the value itself.
     *   
     * @param value the value that will be presented to the user
     * @return the label to use when presenting a given value to the user
     */
    
    public String label( final String value )
    {
        return value;
    }
    
    /**
     * Returns the image to use when presenting a given value to the user. The default implementation
     * returns null. If an unrecognized value is encountered, the implementation should
     * return null.
     * 
     * @param value the value that will be presented to the user
     * @return the image to use when presenting a given value to the user
     */
    
    public ImageData image( final String value )
    {
        return null;
    }
    
    public static class PossibleValuesChangedEvent extends Event
    {
        public PossibleValuesChangedEvent( final ModelService service )
        {
            super( service );
        }
    }
    
    private static final class Resources extends NLS
    {
        public static String defaultInvalidValueMessage;
        
        static
        {
            initializeMessages( PossibleValuesService.class.getName(), Resources.class );
        }
    }
    
}
