/******************************************************************************
 * Copyright (c) 2013 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Shenxue Zhou - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.ui.swt.gef.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.EdgeList;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.draw2d.graph.NodeList;
import org.eclipse.sapphire.ui.diagram.editor.DiagramConnectionPart;
import org.eclipse.sapphire.ui.diagram.editor.DiagramNodePart;
import org.eclipse.sapphire.ui.swt.gef.DiagramConfigurationManager;
import org.eclipse.sapphire.ui.swt.gef.SapphireDiagramEditor;
import org.eclipse.sapphire.ui.swt.gef.model.DiagramConnectionModel;
import org.eclipse.sapphire.ui.swt.gef.model.DiagramModel;
import org.eclipse.sapphire.ui.swt.gef.model.DiagramNodeModel;

/**
 * @author <a href="mailto:shenxue.zhou@oracle.com">Shenxue Zhou</a>
 */

public abstract class DiagramGraphLayout 
{
	private static final int PADDING = 36;
	
	public abstract int getGraphDirection();
	
	public void layout(final SapphireDiagramEditor diagramEditor)
	{
		layout(diagramEditor, false);
	}

	public void layout(final SapphireDiagramEditor diagramEditor, boolean autoLayout)
	{
		final DirectedGraph graph = mapDiagramToGraph(diagramEditor);
		graph.setDefaultPadding(new Insets(PADDING));
		new NodeJoiningDirectedGraphLayout().visit(graph);
		mapGraphCoordinatesToDiagram(graph, diagramEditor, autoLayout);		
	}
	
	@SuppressWarnings("unchecked")
	private DirectedGraph mapDiagramToGraph(final SapphireDiagramEditor diagramEditor) 
	{
		DiagramModel diagramModel = diagramEditor.getDiagramModel();
		Map<DiagramNodeModel, Node> shapeToNode = new HashMap<DiagramNodeModel, Node>();
		DirectedGraph dg = new DirectedGraph();
		dg.setDirection(getGraphDirection());
		EdgeList edgeList = new EdgeList();
		NodeList nodeList = new NodeList();
		List<DiagramNodeModel> children = diagramModel.getNodes();
		for (DiagramNodeModel child : children) 
		{
			Node node = new Node();
			Rectangle bounds = child.getShapePresentation().getFigure().getBounds();//child.getNodeBounds();
			node.x = bounds.x;
			node.y = bounds.y;
			node.width = bounds.width;
			node.height = bounds.height;
			node.data = child;
			shapeToNode.put(child, node);
			nodeList.add(node);
		}
		List<DiagramConnectionModel> connections = diagramModel.getConnections();
		for (DiagramConnectionModel connection : connections) 
		{
			DiagramNodeModel sourceNode = connection.getSourceNode();
			DiagramNodeModel targetNode = connection.getTargetNode();
			Edge edge = new Edge(connection, shapeToNode.get(sourceNode), shapeToNode.get(targetNode));
			edge.weight = 2;
			edge.data = connection;
			edgeList.add(edge);
		}
		dg.nodes = nodeList;
		dg.edges = edgeList;
		return dg;
	}
	
	private void mapGraphCoordinatesToDiagram(final DirectedGraph graph, 
			final SapphireDiagramEditor diagramEditor, final boolean autoLayout) 
	{
		diagramEditor.getConfigurationManager().getConnectionRouter().clear();
		mapGraphNodeCoordinatesToDiagram(graph, autoLayout);
		mapGraphEdgeCoordinatesToDiagram(graph, diagramEditor, autoLayout);
	}

	@SuppressWarnings("unchecked")
	private void mapGraphNodeCoordinatesToDiagram(final DirectedGraph graph, boolean autoLayout)
	{
		NodeList myNodes = new NodeList();
		myNodes.addAll(graph.nodes);
		for (Object object : myNodes) 
		{
			Node node = (Node) object;
			DiagramNodeModel nodeModel = (DiagramNodeModel) node.data;
			DiagramNodePart nodePart = nodeModel.getModelPart();
			nodePart.setNodeBounds(node.x, node.y, autoLayout, false);
		}		
	}
		
	@SuppressWarnings("unchecked")
	private void mapGraphEdgeCoordinatesToDiagram(DirectedGraph graph, 
			final SapphireDiagramEditor diagramEditor, final boolean autoLayout)
	{
		// add bend points generated by the graph layout
		EdgeList myEdges = new EdgeList();
		myEdges.addAll(graph.edges);
		DiagramConfigurationManager configManager = diagramEditor.getConfigurationManager();
		
		for (Object object : myEdges)
		{
			Edge edge = (Edge)object;
			if (!(edge.data instanceof DiagramConnectionModel))
			{
				continue;
			}
			DiagramConnectionModel conn = (DiagramConnectionModel)edge.data;
			NodeList nodes = edge.vNodes;
			DiagramConnectionPart connPart = conn.getModelPart();
			ArrayList<org.eclipse.sapphire.ui.Point> connBendPoints = new ArrayList<org.eclipse.sapphire.ui.Point>();
			if (nodes != null)
			{
				//int bpIndex = 0;
				for (int i = 0; i < nodes.size(); i++)
				{
					Node vn = nodes.getNode(i);
					int x = vn.x;
					int y = vn.y;
					if (getGraphDirection() == PositionConstants.EAST)
					{
						if (edge.isFeedback())
						{
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x + vn.width, y));
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x, y));
						}
						else
						{
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x, y));
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x + vn.width, y));
						}
					}
					else
					{
						if (edge.isFeedback())
						{
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x, y + vn.height));
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x, y));
						}
						else
						{
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x, y));
							connBendPoints.add(new org.eclipse.sapphire.ui.Point(x, y + vn.height));
						}
					}
				}
			}
			else 
			{
	        	Point bendPoint = configManager.getConnectionRouter().route(conn);
	        	if (bendPoint != null)
	        	{
	        		connBendPoints.add(new org.eclipse.sapphire.ui.Point(bendPoint.x, bendPoint.y));
	        	}				
			}
			connPart.resetBendpoints(connBendPoints);
			connPart.setLabelPosition(null);
		}		
	}
		
}
