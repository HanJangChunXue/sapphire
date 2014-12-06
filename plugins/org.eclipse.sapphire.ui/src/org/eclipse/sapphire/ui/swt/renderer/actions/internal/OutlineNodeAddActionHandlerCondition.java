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

package org.eclipse.sapphire.ui.swt.renderer.actions.internal;

import org.eclipse.sapphire.ui.ISapphirePart;
import org.eclipse.sapphire.ui.SapphireCondition;
import org.eclipse.sapphire.ui.editor.views.masterdetails.MasterDetailsContentNode;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

public final class OutlineNodeAddActionHandlerCondition 

    extends SapphireCondition
    
{
    @Override
    protected boolean evaluate()
    {
        final ISapphirePart part = getPart();
        
        if( part instanceof MasterDetailsContentNode )
        {
            final MasterDetailsContentNode node = (MasterDetailsContentNode) part;
            return ( ! node.getChildListProperties().isEmpty() );
        }
        
        return false;
    }

}