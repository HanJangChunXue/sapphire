/******************************************************************************
 * Copyright (c) 2012 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Konstantin Komissarchik - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.samples.gallery;

import org.eclipse.sapphire.modeling.IModelElement;
import org.eclipse.sapphire.modeling.ModelElementType;
import org.eclipse.sapphire.modeling.Value;
import org.eclipse.sapphire.modeling.ValueProperty;
import org.eclipse.sapphire.modeling.annotations.GenerateImpl;
import org.eclipse.sapphire.modeling.annotations.LongString;
import org.eclipse.sapphire.modeling.annotations.Whitespace;
import org.eclipse.sapphire.modeling.xml.annotations.XmlBinding;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

@GenerateImpl

public interface IWhitespaceHandlingGallery

    extends IModelElement

{
    ModelElementType TYPE = new ModelElementType( IWhitespaceHandlingGallery.class );
    
    // *** Preserved ***
    
    @LongString
    @Whitespace( trim = false, collapse = false )
    @XmlBinding( path = "preserved" )
    
    ValueProperty PROP_PRESERVED = new ValueProperty( TYPE, "Preserved" );
    
    Value<String> getPreserved();
    void setPreserved( String value );
    
    // *** Trimmed ***
    
    @LongString
    @Whitespace( trim = true, collapse = false )
    @XmlBinding( path = "trimmed" )
    
    ValueProperty PROP_TRIMMED = new ValueProperty( TYPE, "Trimmed" );
    
    Value<String> getTrimmed();
    void setTrimmed( String value );
    
    // *** Collapsed ***
    
    @LongString
    @Whitespace( trim = true, collapse = true )
    @XmlBinding( path = "collapsed" )
    
    ValueProperty PROP_COLLAPSED = new ValueProperty( TYPE, "Collapsed" );
    
    Value<String> getCollapsed();
    void setCollapsed( String value );

}
