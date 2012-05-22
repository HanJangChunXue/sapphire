/******************************************************************************
 * Copyright (c) 2012 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Shenxue Zhou - initial implementation and ongoing maintenance
<<<<<<< ArchitectureDiagramLayoutPersistenceService.java
 *    Konstantin Komissarchik - [378756] Convert ModelElementListener and ModelPropertyListener to common listener infrastructure
=======
 *    Konstantin Komissarchik - [376245] Revert action in StructuredTextEditor does not revert diagram nodes and connections in SapphireDiagramEditor
>>>>>>> 1.10
 ******************************************************************************/

package org.eclipse.sapphire.samples.architecture.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.sapphire.FilteredListener;
import org.eclipse.sapphire.Listener;
import org.eclipse.sapphire.modeling.ModelElementList;
import org.eclipse.sapphire.modeling.PropertyEvent;
import org.eclipse.sapphire.samples.architecture.ConnectionBendpoint;
import org.eclipse.sapphire.samples.architecture.IArchitecture;
import org.eclipse.sapphire.samples.architecture.IComponent;
import org.eclipse.sapphire.samples.architecture.IComponentDependency;
import org.eclipse.sapphire.ui.Bounds;
import org.eclipse.sapphire.ui.Point;
import org.eclipse.sapphire.ui.diagram.editor.DiagramConnectionBendPoints;
import org.eclipse.sapphire.ui.diagram.editor.DiagramConnectionEvent;
import org.eclipse.sapphire.ui.diagram.editor.DiagramConnectionPart;
import org.eclipse.sapphire.ui.diagram.editor.DiagramNodeBounds;
import org.eclipse.sapphire.ui.diagram.editor.DiagramNodeEvent;
import org.eclipse.sapphire.ui.diagram.editor.DiagramNodePart;
import org.eclipse.sapphire.ui.diagram.editor.DiagramPageEvent;
import org.eclipse.sapphire.ui.diagram.editor.IdUtil;
import org.eclipse.sapphire.ui.diagram.editor.SapphireDiagramEditorPagePart;
import org.eclipse.sapphire.ui.diagram.editor.SapphireDiagramPartListener;
import org.eclipse.sapphire.ui.diagram.layout.ConnectionHashKey;
import org.eclipse.sapphire.ui.diagram.layout.DiagramLayoutPersistenceService;

/**
 * @author <a href="mailto:shenxue.zhou@oracle.com">Shenxue Zhou</a>
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

public class ArchitectureDiagramLayoutPersistenceService extends DiagramLayoutPersistenceService 
{
	private IArchitecture architecture;
	private SapphireDiagramPartListener diagramPartListener;
	private Listener componentListener;	
	private Listener componentDependencyListener;
	private Map<String, DiagramNodeBounds> nodeBounds;
	private Map<ConnectionHashKey, DiagramConnectionBendPoints> connectionBendPoints;
	private boolean dirty;
	
	@Override
    protected void init()
    {
    	super.init();    	
    	this.architecture = (IArchitecture)context( SapphireDiagramEditorPagePart.class ).getLocalModelElement();
    	this.nodeBounds = new HashMap<String, DiagramNodeBounds>();
    	this.connectionBendPoints = new HashMap<ConnectionHashKey, DiagramConnectionBendPoints>();
    	this.dirty = false;
    	load();
    	refreshPersistedPartsCache();
    	addDiagramPartListener();
    	addModelListeners();
    }

	private void setGridVisible(boolean visible)
	{
		this.architecture.setShowGrid(visible);
	}
	
	private void setGuidesVisible(boolean visible)
	{
		this.architecture.setShowGuides(visible);
	}

	private void read(DiagramNodePart nodePart)
	{
		
		IComponent component = (IComponent)nodePart.getLocalModelElement();
		if (!component.disposed())
		{
			String nodeId = IdUtil.computeNodeId(nodePart);
	    	if (this.nodeBounds.containsKey(nodeId) && this.nodeBounds.get(nodeId) != null)
	    	{
	    		nodePart.setNodeBounds(this.nodeBounds.get(nodeId)); 		
	    	}
		}
	}
	
	private void write(DiagramNodePart nodePart) 
	{
		IComponent component = (IComponent)nodePart.getLocalModelElement();
		if (!component.disposed())
		{
			if (isNodeLayoutChanged(nodePart))
			{				
				this.architecture.detach(this.componentListener, "/Components/Bounds/*");
				writeComponentBounds(component, nodePart);
				this.architecture.attach(this.componentListener, "/Components/Bounds/*");
			}
			
			refreshDirtyState();
		}		
	}

	private void read(DiagramConnectionPart connPart)
	{
    	ConnectionHashKey key = ConnectionHashKey.createKey(connPart);
    	if (this.connectionBendPoints.containsKey(key) && this.connectionBendPoints.get(key) != null)
    	{    		
    		connPart.resetBendpoints(this.connectionBendPoints.get(key));
    	}		
	}
	
	private void write(DiagramConnectionPart connPart) 
	{
		IComponentDependency dependency = (IComponentDependency)connPart.getLocalModelElement();
		if (!dependency.disposed())
		{
			if (isConnectionLayoutChanged(connPart))
			{
				this.architecture.detach(this.componentDependencyListener, "/Components/Dependencies/ConnectionBendpoints/*");
				writeDependencyBendPoints(dependency, connPart);
				this.architecture.attach(this.componentDependencyListener, "/Components/Dependencies/ConnectionBendpoints/*");
			}
			
			refreshDirtyState();
		}			
	}
	
	@Override
	public void dispose()
	{
		if (this.diagramPartListener != null)
		{
		    context( SapphireDiagramEditorPagePart.class ).removeListener(this.diagramPartListener);
		}
		if (this.componentListener != null)
		{
			this.architecture.detach(this.componentListener, "/Components/Bounds/*");
		}
		if (this.componentDependencyListener != null)
		{
			this.architecture.detach(this.componentDependencyListener, "/Components/Dependencies/ConnectionBendpoints/*");
		}
	}

	private void load()
	{
	    context( SapphireDiagramEditorPagePart.class ).setGridVisible(this.architecture.isShowGrid().getContent());
	    context( SapphireDiagramEditorPagePart.class ).setShowGuides(this.architecture.isShowGuides().getContent());
		
		ModelElementList<IComponent> components = this.architecture.getComponents();
		for (IComponent component : components)
		{
			DiagramNodePart nodePart = context( SapphireDiagramEditorPagePart.class ).getDiagramNodePart(component);
			if (nodePart != null)
			{
				String nodeId = IdUtil.computeNodeId(nodePart);
				DiagramNodeBounds bounds = null;
				if (component.getBounds().getX().getContent(false) != null && component.getBounds().getX().getContent(false) != null)
				{
					bounds = new DiagramNodeBounds(component.getBounds().getX().getContent(), 
							component.getBounds().getY().getContent(), 
							component.getBounds().getWidth().getContent(), 
							component.getBounds().getHeight().getContent(),
							false, false);
					nodePart.setNodeBounds(bounds);					
					
				}
				
				// load the embedded connection layout
				ModelElementList<IComponentDependency> dependencies = component.getDependencies();
				for (IComponentDependency dependency : dependencies)
				{
					DiagramConnectionPart connPart = context( SapphireDiagramEditorPagePart.class ).getDiagramConnectionPart(dependency);
					if (connPart != null)
					{
						String connId = IdUtil.computeConnectionId(connPart);
						ConnectionHashKey connKey = ConnectionHashKey.createKey(nodeId, connId);
						List<Point> pts = new ArrayList<Point>();
						
						ModelElementList<ConnectionBendpoint> bendpoints = dependency.getConnectionBendpoints();
						if (bendpoints.size() > 0)
						{
							int index = 0;
							for (ConnectionBendpoint bendpoint : bendpoints)
							{
								connPart.addBendpoint(index++, bendpoint.getX().getContent(), 
										bendpoint.getY().getContent());
								pts.add(new Point(bendpoint.getX().getContent(), bendpoint.getY().getContent()));
							}							
						}
					}
				}				
			}
		}		
	}
	
	private void handleNodeLayoutChange(IComponent component)
	{
		DiagramNodePart nodePart = context( SapphireDiagramEditorPagePart.class ).getDiagramNodePart(component);
		if (nodePart != null && component.getBounds().getX().getContent(false) != null && 
				component.getBounds().getY().getContent(false) != null)
		{
			DiagramNodeBounds nodeBounds = new DiagramNodeBounds(component.getBounds().getX().getContent(),
					component.getBounds().getY().getContent());
			nodePart.setNodeBounds(nodeBounds);
		}
	}
	
	private void handleConnectionBendpointChange(IComponentDependency componentDependency)
	{
		DiagramConnectionPart connPart = context( SapphireDiagramEditorPagePart.class ).getDiagramConnectionPart(componentDependency);
		if (connPart != null)
		{
			List<Point> bendpoints = new ArrayList<Point>();
			for (ConnectionBendpoint bendpoint : componentDependency.getConnectionBendpoints())
			{
				if (bendpoint.getX().getContent(false) != null && bendpoint.getY().getContent(false) != null)
				{
					bendpoints.add(new Point(bendpoint.getX().getContent(), bendpoint.getY().getContent()));
				}
			}
			connPart.resetBendpoints(bendpoints, false, false);
		}
	}
	
	private void addNodeToPersistenceCache(DiagramNodePart nodePart)
	{
		String nodeId = IdUtil.computeNodeId(nodePart);
		this.nodeBounds.put(nodeId, nodePart.getNodeBounds());
	}
	
	private void addConnectionToPersistenceCache(DiagramConnectionPart connPart)
	{
		ConnectionHashKey connKey = ConnectionHashKey.createKey(connPart);
		this.connectionBendPoints.put(connKey, connPart.getConnectionBendpoints());
	}

	private void addDiagramPartListener()
	{
		this.diagramPartListener = new SapphireDiagramPartListener() 
		{
			@Override
            public void handleNodeAddEvent(final DiagramNodeEvent event)
            {
				read((DiagramNodePart)event.getPart());
            }
			
			@Override
		    public void handleNodeMoveEvent(final DiagramNodeEvent event)
		    {
				DiagramNodePart nodePart = (DiagramNodePart)event.getPart();
				DiagramNodeBounds nodeBounds = nodePart.getNodeBounds();
				
				if (nodeBounds.isAutoLayout())
				{
					// need to add the node bounds to the persistence cache so that "revert" could work
					addNodeToPersistenceCache(nodePart);
					refreshDirtyState();
				}
				else if (!nodeBounds.isDefaultPosition())
				{
					write((DiagramNodePart)event.getPart());
				}
		    }
			
			@Override
		    public void handleNodeDeleteEvent(final DiagramNodeEvent event)
			{
			    refreshDirtyState();
			}
			
			@Override
	        public void handleConnectionAddEvent(final DiagramConnectionEvent event)
			{
				DiagramConnectionPart connPart = (DiagramConnectionPart)event.getPart();
				read(connPart);
			}
			
			@Override
			public void handleConnectionDeleteEvent(final DiagramConnectionEvent event)
			{
				refreshDirtyState();
			}
			
			@Override
			public void handleConnectionAddBendpointEvent(final DiagramConnectionEvent event)
			{
				write((DiagramConnectionPart)event.getPart());
			}
			
			@Override
		    public void handleConnectionRemoveBendpointEvent(final DiagramConnectionEvent event)
		    {
				write((DiagramConnectionPart)event.getPart());
			}

		    public void handleConnectionMoveBendpointEvent(final DiagramConnectionEvent event)
		    {
		    	write((DiagramConnectionPart)event.getPart());
		    }

		    @Override
		    public void handleConnectionResetBendpointsEvent(final DiagramConnectionEvent event)
		    {
		    	DiagramConnectionPart connPart = (DiagramConnectionPart)event.getPart();
		    	DiagramConnectionBendPoints bendPoints = connPart.getConnectionBendpoints();
		    	if (bendPoints.isAutoLayout())
		    	{
		    		addConnectionToPersistenceCache(connPart);
		    		refreshDirtyState();
		    	}
		    	else
		    	{
		    		if (bendPoints.isDefault())
		    		{
		    			// Both the SapphireDiagramEditor and this class listen on connection
		    			// events and we don't control who receives the events first.
						// During "revert", t=if the default bend point is added after the connection 
						// was read, we need to re-read the connection to ensure "revert" works.		    			
		    			read(connPart);
		    		}
		    		else
		    		{
		    			write((DiagramConnectionPart)event.getPart());
		    		}
		    	}
		    }
		    
		    @Override
			public void handleGridStateChangeEvent(final DiagramPageEvent event)
			{
		    	SapphireDiagramEditorPagePart diagramPart = (SapphireDiagramEditorPagePart)event.getPart();
		    	setGridVisible(diagramPart.isGridVisible());
			}
			
			@Override
			public void handleGuideStateChangeEvent(final DiagramPageEvent event)
			{
		    	SapphireDiagramEditorPagePart diagramPart = (SapphireDiagramEditorPagePart)event.getPart();
		    	setGuidesVisible(diagramPart.isShowGuides());
			}
			
			@Override
			public void handleDiagramSaveEvent(final DiagramPageEvent event)
			{
				doSave();
			}
						
		};
		context( SapphireDiagramEditorPagePart.class ).addListener(this.diagramPartListener);
	}
	
	private void addModelListeners()
	{
		this.componentListener = new FilteredListener<PropertyEvent>() 
		{
		    @Override
		    protected void handleTypedEvent( final PropertyEvent event )
		    {
		    	if (event != null && event.element() != null)
		    	{
		    		IComponent component = event.element().nearest(IComponent.class);
		    		if (component != null)
		    		{
		    			handleNodeLayoutChange(component);
		    		}
		    	}
		    }
		};
		
		this.componentDependencyListener = new FilteredListener<PropertyEvent>()
		{
			@Override
			protected void handleTypedEvent( final PropertyEvent event )
			{
				if (event != null && event.element() != null)
				{
					IComponentDependency componentDependency = event.element().nearest(IComponentDependency.class);
					if (componentDependency != null)
					{
						handleConnectionBendpointChange(componentDependency);
					}
				}
			}
		};
		
		this.architecture.attach(this.componentListener, "/Components/Bounds/*");
		this.architecture.attach(this.componentDependencyListener, "/Components/Dependencies/ConnectionBendpoints/*");
		
	}
	
	private void writeComponentBounds(IComponent component, DiagramNodePart node)
	{
		Bounds bounds = node.getNodeBounds();
		component.getBounds().setX(bounds.getX());
		component.getBounds().setY(bounds.getY());
		if (node.canResizeShape())
		{
			if (bounds.getWidth() > 0)
				component.getBounds().setWidth(bounds.getWidth());
			if (bounds.getHeight() > 0)
				component.getBounds().setHeight(bounds.getHeight());
		}		
	}
	
	private void writeDependencyBendPoints(IComponentDependency dependency, DiagramConnectionPart connPart)
	{
		dependency.getConnectionBendpoints().clear();
		List<Point> bendpoints = connPart.getConnectionBendpoints().getBendPoints();
		for (Point bendpoint : bendpoints)
		{
			ConnectionBendpoint bpLayout = dependency.getConnectionBendpoints().insert();
			bpLayout.setX(bendpoint.getX());
			bpLayout.setY(bendpoint.getY());
		}
		
	}
		
	private void doSave()
	{
		refreshPersistedPartsCache();
		// For nodes that are placed using default node positions and connection bend points that
		// are calculated using connection router, we don't modify the corresponding model properties
		// in order to allow "revert" in source editor to work correctly.
		// So we need to do an explicit save of the node bounds and connection bend points here.
		this.architecture.detach(this.componentListener, "/Components/Bounds/*");
		this.architecture.detach(this.componentDependencyListener, "/Components/Dependencies/ConnectionBendpoints/*");
		
		for (DiagramNodePart nodePart : context( SapphireDiagramEditorPagePart.class ).getNodes())
		{
			IComponent component = (IComponent)nodePart.getLocalModelElement();
			if (!component.disposed())
			{
				writeComponentBounds(component, nodePart);
			}
		}
		
		for (DiagramConnectionPart connPart : context( SapphireDiagramEditorPagePart.class ).getConnections())
		{
			IComponentDependency dependency = (IComponentDependency)connPart.getLocalModelElement();
			if (!dependency.disposed())
			{
				writeDependencyBendPoints(dependency, connPart);
			}
		}
		
		this.architecture.attach(this.componentListener, "/Components/Bounds/*");
		this.architecture.attach(this.componentDependencyListener, "/Components/Dependencies/ConnectionBendpoints/*");
	}
	
    private boolean isNodeLayoutChanged(DiagramNodePart nodePart)
    {
		DiagramNodeBounds newBounds = nodePart.getNodeBounds();
		boolean changed = false;
		String nodeId = IdUtil.computeNodeId(nodePart);
		if (this.nodeBounds.containsKey(nodeId))
		{
			DiagramNodeBounds oldBounds = this.nodeBounds.get(nodeId);
			if (!newBounds.equals(oldBounds))
			{
				changed = true;
			}
		}
		else
		{
			changed = true;
		}
    	return changed;
    }
	
    private boolean isConnectionLayoutChanged(DiagramConnectionPart connPart)
    {
		// Detect whether the connection bendpoints have been changed.
    	DiagramConnectionBendPoints bendpoints = connPart.getConnectionBendpoints();
		ConnectionHashKey key = ConnectionHashKey.createKey(connPart);
		boolean changed = false;
		if (this.connectionBendPoints.containsKey(key))
		{		
			DiagramConnectionBendPoints oldBendpoints = this.connectionBendPoints.get(key);
			if (!bendpoints.equals(oldBendpoints))
			{
				changed = true;
			}
		}
		else
		{
			changed = true;
		}
    	return changed;
    }
    
    private boolean isDiagramLayoutChanged()
    {
    	boolean changed = false;
		for (DiagramNodePart nodePart : context( SapphireDiagramEditorPagePart.class ).getNodes())
		{
			if (!nodePart.getLocalModelElement().disposed() && isNodeLayoutChanged(nodePart))
			{
				changed = true;
				break;
			}
		}
		for (DiagramConnectionPart connPart : context( SapphireDiagramEditorPagePart.class ).getConnections())
		{
			if (!connPart.getLocalModelElement().disposed() && isConnectionLayoutChanged(connPart))
			{
				changed = true;
				break;
			}
		}
		
    	return changed;
    }
    
    @Override
    public boolean dirty()
    {
        return this.dirty;
    }

    private void refreshDirtyState()
    {
        final boolean after = isDiagramLayoutChanged();
        
        if( this.dirty != after )
        {
            final boolean before = this.dirty;
            this.dirty = after;
            
            broadcast( new DirtyStateEvent( before, after ) );
        }
    }
	
	private void refreshPersistedPartsCache()
	{
		this.nodeBounds.clear();
		this.connectionBendPoints.clear();
		for (DiagramConnectionPart connPart : context( SapphireDiagramEditorPagePart.class ).getConnections())
		{
			addConnectionToPersistenceCache(connPart);
		}
		for (DiagramNodePart nodePart : context( SapphireDiagramEditorPagePart.class ).getNodes())
		{
			addNodeToPersistenceCache(nodePart);
		}		
	}
	
}
