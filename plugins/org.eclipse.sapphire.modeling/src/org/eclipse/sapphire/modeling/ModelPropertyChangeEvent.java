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

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

public final class ModelPropertyChangeEvent
{
    private final IModelElement modelElement;
    private final ModelProperty property;
    private final boolean oldEnablementState;
    private final boolean newEnablementState;
    
    public ModelPropertyChangeEvent( final IModelElement modelElement,
                                     final ModelProperty property,
                                     final Boolean oldEnablementState,
                                     final boolean newEnablementState )
    {
        this.modelElement = modelElement;
        this.property = property;
        this.oldEnablementState = ( oldEnablementState == null ? newEnablementState : oldEnablementState );
        this.newEnablementState = newEnablementState;
    }
    
    public IModelElement getModelElement()
    {
        return this.modelElement;
    }
    
    public ModelProperty getProperty()
    {
        return this.property;
    }
    
    public boolean getOldEnablementState()
    {
        return this.oldEnablementState;
    }
    
    public boolean getNewEnablementState()
    {
        return this.newEnablementState;
    }

}