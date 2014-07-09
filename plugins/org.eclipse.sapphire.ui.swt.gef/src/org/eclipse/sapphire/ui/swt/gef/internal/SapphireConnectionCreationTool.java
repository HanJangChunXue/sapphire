/******************************************************************************
 * Copyright (c) 2014 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Shenxue Zhou - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.ui.swt.gef.internal;

import org.eclipse.gef.requests.CreationFactory;
import org.eclipse.gef.tools.ConnectionCreationTool;

/**
 * @author <a href="mailto:shenxue.zhou@oracle.com">Shenxue Zhou</a>
 */

public class SapphireConnectionCreationTool extends ConnectionCreationTool 
{
	/**
	 * Default Constructor.
	 */
	public SapphireConnectionCreationTool() 
	{
		setUnloadWhenFinished(true);
	}

	/**
	 * Constructs a new SapphireConnectionCreationTool with the given factory.
	 * 
	 * @param factory
	 *            the creation factory
	 */
	public SapphireConnectionCreationTool(CreationFactory factory) 
	{
		setFactory(factory);
		setUnloadWhenFinished(true);
	}

}
