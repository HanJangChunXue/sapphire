/******************************************************************************
 * Copyright (c) 2012 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Ling Hao - initial implementation and ongoing maintenance
 *    Shenxue Zhou - Refreshing connections attached to the node
 ******************************************************************************/

package org.eclipse.sapphire.ui.gef.diagram.editor.commands;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.gef.commands.Command;
import org.eclipse.sapphire.ui.diagram.editor.DiagramConnectionPart;
import org.eclipse.sapphire.ui.gef.diagram.editor.model.DiagramConnectionModel;
import org.eclipse.sapphire.ui.gef.diagram.editor.model.DiagramModel;
import org.eclipse.sapphire.ui.gef.diagram.editor.model.DiagramNodeModel;

/**
 * @author <a href="mailto:ling.hao@oracle.com">Ling Hao</a>
 */

public class LabelNodeCommand extends Command {
	
	private DiagramNodeModel node;
	private String labelText;

	public LabelNodeCommand(DiagramNodeModel node, String labelText) {
		this.node = node;
		this.labelText = labelText;
	}

	@Override
	public void execute() 
	{
		DiagramModel diagramModel = node.getDiagramModel();
		// Make a copy of all the connections that are attached to this node before setting the 
		// node label.
		List<DiagramConnectionModel> connModels = diagramModel.getConnections();
		List<DiagramConnectionPart> connParts1 = new ArrayList<DiagramConnectionPart>();
		List<DiagramConnectionPart> connParts2 = new ArrayList<DiagramConnectionPart>();
		for (DiagramConnectionModel connModel : connModels)
		{
			// Need to disable connection part listeners so the attached connections don't get deleted.
			// Will re-enable them once the end points are refreshed. 
			if (connModel.getSourceNode().equals(node) || (connModel.getTargetNode().equals(node)))
			{
				DiagramConnectionPart connPart = connModel.getModelPart();
	            connPart.removeModelListener();
	            connPart.getDiagramConnectionTemplate().removeModelListener();
	
				if (connModel.getSourceNode().equals(node))
				{
					connParts1.add(connModel.getModelPart());
				}
				else if (connModel.getTargetNode().equals(node))
				{
					connParts2.add(connModel.getModelPart());
				}
			}
		}		
		
		node.getModelPart().setLabel(labelText);
		
		// Refreshing endpoints of attached connections and re-enable listeners on them.
		for (DiagramConnectionPart connPart : connParts1)
		{
			connPart.resetEndpoint1();
            connPart.addModelListener();
            connPart.getDiagramConnectionTemplate().addModelListener();
		}
		for (DiagramConnectionPart connPart : connParts2)
		{
			connPart.resetEndpoint2();
            connPart.addModelListener();
            connPart.getDiagramConnectionTemplate().addModelListener();
		}		
	}
	
}
