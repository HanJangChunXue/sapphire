/******************************************************************************
 * Copyright (c) 2012 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Ling Hao - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.ui.gef.diagram.editor.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.graph.CompoundDirectedGraph;
import org.eclipse.draw2d.graph.CompoundDirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.EdgeList;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.draw2d.graph.NodeList;
import org.eclipse.sapphire.ui.Bounds;
import org.eclipse.sapphire.ui.SapphireRenderingContext;
import org.eclipse.sapphire.ui.diagram.SapphireDiagramActionHandler;
import org.eclipse.sapphire.ui.diagram.editor.DiagramConnectionPart;
import org.eclipse.sapphire.ui.diagram.editor.DiagramNodePart;
import org.eclipse.sapphire.ui.gef.diagram.editor.DiagramRenderingContext;
import org.eclipse.sapphire.ui.gef.diagram.editor.SapphireConnectionRouter;
import org.eclipse.sapphire.ui.gef.diagram.editor.model.DiagramConnectionModel;
import org.eclipse.sapphire.ui.gef.diagram.editor.model.DiagramModel;
import org.eclipse.sapphire.ui.gef.diagram.editor.model.DiagramNodeModel;

/**
 * @author <a href="mailto:ling.hao@oracle.com">Ling Hao</a>
 */

public abstract class DiagramGraphLayoutActionHandler extends
		SapphireDiagramActionHandler 
{
	private static final int PADDING = 36;
	
	@Override
	public boolean canExecute(Object obj) 
	{
		return true;
	}
	
	public abstract int getGraphDirection();

	@Override
	protected Object run(SapphireRenderingContext context) 
	{
		DiagramRenderingContext diagramCtx = (DiagramRenderingContext)context;
		DiagramModel diagramModel = diagramCtx.getDiagramEditor().getDiagramModel();
		final CompoundDirectedGraph graph = mapDiagramToGraph(diagramModel);
		graph.setDefaultPadding(new Insets(PADDING));
		new CompoundDirectedGraphLayout().visit(graph);
		mapGraphCoordinatesToDiagram(graph, diagramModel);
		
		return null;
	}

	@SuppressWarnings("unchecked")
	private CompoundDirectedGraph mapDiagramToGraph(DiagramModel diagramModel) 
	{
		Map<DiagramNodeModel, Node> shapeToNode = new HashMap<DiagramNodeModel, Node>();
		CompoundDirectedGraph dg = new CompoundDirectedGraph();
		if (getGraphDirection() == PositionConstants.EAST_WEST)
		{
			dg.setDirection(getGraphDirection());
		}
		EdgeList edgeList = new EdgeList();
		NodeList nodeList = new NodeList();
		List<DiagramNodeModel> children = diagramModel.getNodes();
		for (DiagramNodeModel child : children) 
		{
			Node node = new Node();
			Bounds bounds = child.getNodeBounds();
			node.x = bounds.getX();
			node.y = bounds.getY();
			node.width = bounds.getWidth();
			node.height = bounds.getHeight();
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
			edge.data = connection;
			edgeList.add(edge);
		}
		dg.nodes = nodeList;
		dg.edges = edgeList;
		return dg;
	}
	
	private void mapGraphCoordinatesToDiagram(final CompoundDirectedGraph graph, final DiagramModel diagramModel) {
		mapGraphNodeCoordinatesToDiagram(graph);
		removeDiagramManualBendpoints(diagramModel);
		mapGraphEdgeCoordinatesToDiagram(graph, diagramModel);
	}

	@SuppressWarnings("unchecked")
	private void mapGraphNodeCoordinatesToDiagram(final CompoundDirectedGraph graph)
	{
		NodeList myNodes = new NodeList();
		myNodes.addAll(graph.nodes);
		myNodes.addAll(graph.subgraphs);
		for (Object object : myNodes) 
		{
			Node node = (Node) object;
			DiagramNodeModel nodeModel = (DiagramNodeModel) node.data;
			DiagramNodePart nodePart = nodeModel.getModelPart();
			nodePart.setNodePosition(node.x, node.y);
		}		
	}
	
	private void removeDiagramManualBendpoints(final DiagramModel diagramModel) {
		List<DiagramConnectionModel> connections = diagramModel.getConnections();
		for (DiagramConnectionModel conn : connections) {
			DiagramConnectionPart connPart = conn.getModelPart();
			connPart.removeAllBendpoints();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void mapGraphEdgeCoordinatesToDiagram(CompoundDirectedGraph graph, final DiagramModel diagramModel)
	{
		// add bend points generated by the graph layout
		EdgeList myEdges = new EdgeList();
		myEdges.addAll(graph.edges);
		for (Object object : myEdges)
		{
			Edge edge = (Edge)object;
			DiagramConnectionModel conn = (DiagramConnectionModel)edge.data;
			NodeList nodes = edge.vNodes;
			DiagramConnectionPart connPart = conn.getModelPart();
			if (nodes != null)
			{
				int bpIndex = 0;
				for (int i = 0; i < nodes.size(); i++)
				{
					Node vn = nodes.getNode(i);
					int x = vn.x;
					int y = vn.y;
					if (getGraphDirection() == PositionConstants.EAST_WEST)
					{
						int offset = edge.isFeedback() ? -vn.width : vn.width;
						connPart.addBendpoint(bpIndex, x, y);
						connPart.addBendpoint(bpIndex + 1, x + offset, y);
					}
					else
					{
						int offset = edge.isFeedback() ? -vn.height : vn.height;
						connPart.addBendpoint(bpIndex, x, y);
						connPart.addBendpoint(bpIndex + 1, x, y + offset);
					}
					bpIndex += 2;
				}
			}
			else 
			{
	        	Point bendPoint = SapphireConnectionRouter.getInstance().route(conn);
	        	if (bendPoint != null)
	        	{
	        		connPart.addBendpoint(0, bendPoint.x, bendPoint.y);
	        	}				
			}
		}		
	}
	
}


