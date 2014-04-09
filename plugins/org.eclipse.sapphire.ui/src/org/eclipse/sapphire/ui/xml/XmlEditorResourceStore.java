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

package org.eclipse.sapphire.ui.xml;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.sapphire.modeling.ByteArrayResourceStore;
import org.eclipse.sapphire.modeling.IModelElement;
import org.eclipse.sapphire.modeling.ModelElementDisposedEvent;
import org.eclipse.sapphire.modeling.ModelElementListener;
import org.eclipse.sapphire.modeling.ModelProperty;
import org.eclipse.sapphire.modeling.Resource;
import org.eclipse.sapphire.modeling.ResourceStoreException;
import org.eclipse.sapphire.modeling.ValidateEditException;
import org.eclipse.sapphire.modeling.xml.XmlElement;
import org.eclipse.sapphire.modeling.xml.XmlResource;
import org.eclipse.sapphire.modeling.xml.XmlResourceStore;
import org.eclipse.sapphire.ui.DelayedTasksExecutor;
import org.eclipse.sapphire.ui.SapphireEditor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.sse.core.internal.provisional.INodeAdapter;
import org.eclipse.wst.sse.core.internal.provisional.INodeNotifier;
import org.eclipse.wst.sse.ui.StructuredTextEditor;
import org.eclipse.wst.sse.ui.internal.provisional.extensions.ISourceEditingTextTools;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.ui.internal.provisional.IDOMSourceEditingTextTools;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 */

@SuppressWarnings( "restriction" )

public class XmlEditorResourceStore

    extends XmlResourceStore
    
{
    private StructuredTextEditor sourceEditor;
    private IModelElement rootModelElement;
    private final Map<Node,List<IModelElement>> nodeToModelElementsMap;
    private final Scrubber scrubber;
    private final ModelElementListener modelElementDisposeListener;
    
    public XmlEditorResourceStore( final SapphireEditor sapphireEditor,
                                   final StructuredTextEditor sourceEditor )
    {
        super( (ByteArrayResourceStore) null );
        
        this.sourceEditor = sourceEditor;
        this.rootModelElement = null;
        this.nodeToModelElementsMap = new HashMap<Node,List<IModelElement>>();
        this.scrubber = new Scrubber();
        this.scrubber.start();
        
        this.modelElementDisposeListener = new ModelElementListener()
        {
            @Override
            public void handleElementDisposedEvent( final ModelElementDisposedEvent event )
            {
                handleElementDisposed( event.getModelElement() );
            }
        };
        
        final ISourceEditingTextTools sourceEditingTextTools = (ISourceEditingTextTools) this.sourceEditor.getAdapter( ISourceEditingTextTools.class );
        final IDOMSourceEditingTextTools domSourceEditingTextTools = (IDOMSourceEditingTextTools) sourceEditingTextTools;
        final Document document = domSourceEditingTextTools.getDOMDocument();
        
        final INodeAdapter adapter = new INodeAdapter()
        {
            public boolean isAdapterForType( final Object type )
            {
                return false;
            }

            public void notifyChanged( final INodeNotifier notifier,
                                       final int eventType,
                                       final Object changedFeature,
                                       final Object oldValue,
                                       final Object newValue,
                                       final int pos )
            {
                /* System.err.println( "notifyChanged" );
                System.err.println( "  notifier = " + notifier.getClass().getName() );
                System.err.println( "  eventType = " + eventType );
                System.err.println( "  changedFeature = " + ( changedFeature == null ? "null" : changedFeature.getClass().getName() ) );
                System.err.println( "  oldValue = " + ( oldValue == null ? "null" : oldValue.getClass().getName() ) );
                System.err.println( "  newValue = " + ( newValue == null ? "null" : newValue.getClass().getName() ) );
                System.err.println( "  pos = " + pos ); */
                
                if( eventType == INodeNotifier.ADD && newValue instanceof IDOMNode )
                {
                    addAdapter( (IDOMNode) newValue, this );
                }
                
                handleXmlNodeChange( (Node) notifier );
            }
        };

        addAdapter( (IDOMNode) document, adapter );
        
        setDomDocument( document );
    }
    
    public StructuredTextEditor getXmlEditor()
    {
        return this.sourceEditor;
    }

    @Override
    public boolean isXmlDeclarationNeeded()
    {
        return true;
    }

    @Override
    public void save() throws ResourceStoreException
    {
        final IEditorInput input = this.sourceEditor.getEditorInput();
        
        if( input instanceof FileEditorInput )
        {
            final IFile file = ( (FileEditorInput) input ).getFile();
            
            if( ! file.exists() )
            {
                return;
            }
        }

        validateSave();
        this.sourceEditor.doSave( new NullProgressMonitor() );
    }

    @Override
    public void validateEdit()
    {
        final IEditorInput input = this.sourceEditor.getEditorInput();
        
        if( input instanceof FileEditorInput )
        {
            final IFile file = ( (FileEditorInput) input ).getFile();
            
            if( ! file.exists() )
            {
                final IStatus st = ResourcesPlugin.getWorkspace().validateEdit( new IFile[] { file }, IWorkspace.VALIDATE_PROMPT );
                
                if( st.getSeverity() == IStatus.ERROR )
                {
                    throw new ValidateEditException();
                }
                
                try
                {
                    file.create( new ByteArrayInputStream( new byte[ 0 ] ), true, new NullProgressMonitor() );
                }
                catch( CoreException e )
                {
                    throw new ValidateEditException( e );
                }
            }
        }
        
        if( this.sourceEditor.validateEditorInputState() == false )
        {
            throw new ValidateEditException();
        }
    }
    
    @Override
    public void validateSave()
    {
        if( this.sourceEditor.validateEditorInputState() == false )
        {
            throw new ValidateEditException();
        }
    }
    
    @Override
    @SuppressWarnings( "unchecked" )
    
    public <A> A adapt( final Class<A> adapterType )
    {
        A result = null;
        
        if( adapterType == ITextEditor.class )
        {
            result = (A) getXmlEditor();
        }
        else if( adapterType == File.class )
        {
            final IEditorInput input = this.sourceEditor.getEditorInput();
            
            if( input instanceof FileEditorInput)
            {
                result = (A) ( (FileEditorInput) input ).getFile().getLocation().toFile();
            }
            else
            {
                //Handle files that are not part of the current workspace.
                
                final URI uri = ( (FileStoreEditorInput) input ).getURI();
                result = (A) new File( uri );
            }
        }
        else if( adapterType == IFile.class )
        {
            final IEditorInput input = this.sourceEditor.getEditorInput();
            
            if( input instanceof FileEditorInput )
            {
                result = (A) ( (FileEditorInput) input ).getFile();
            }
        }
        else if( adapterType == IProject.class )
        {
            final IEditorInput input = this.sourceEditor.getEditorInput();
            
            if( input instanceof FileEditorInput )
            {
                result = (A) ( (FileEditorInput) input ).getFile().getProject();
            }
        }
        else
        {
            result = super.adapt( adapterType );
        }
        
        return result;
    }

    @Override
    public void registerRootModelElement( final IModelElement rootModelElement )
    {
        this.rootModelElement = rootModelElement;
    }

    @Override
    public void registerModelElement( final Node xmlNode,
                                      final IModelElement element )
    {
        synchronized( this.nodeToModelElementsMap )
        {
            List<IModelElement> elements = this.nodeToModelElementsMap.get( xmlNode );
            
            if( elements == null )
            {
                elements = new ArrayList<IModelElement>( 1 );
                this.nodeToModelElementsMap.put( xmlNode, elements );
            }
            
            elements.add( element );
            element.addListener( this.modelElementDisposeListener );
        }
    }
    
    private void handleElementDisposed( final IModelElement element )
    {
        final Resource resource = element.resource();
        
        if( resource instanceof XmlResource )
        {
            final XmlElement xmlElement = ( (XmlResource) resource ).getXmlElement(); 
            
            if( xmlElement != null )
            {
                synchronized( this.nodeToModelElementsMap )
                {
                    final Node xmlNode = xmlElement.getDomNode();
                    final List<IModelElement> elements = this.nodeToModelElementsMap.get( xmlNode );
                    
                    if( elements != null )
                    {
                        elements.remove( element );
                        
                        if( elements.isEmpty() )
                        {
                            this.nodeToModelElementsMap.remove( xmlNode );
                        }
                    }
                }
            }
        }
    }

    protected List<IModelElement> getModelElements( final Node xmlNode )
    {
        synchronized( this.nodeToModelElementsMap )
        {
            Node node = xmlNode;
            List<IModelElement> elements = this.nodeToModelElementsMap.get( node );
            
            while( elements == null && node != null && ! ( node instanceof Document ) )
            {
                node = node.getParentNode();
                elements = this.nodeToModelElementsMap.get( node );
            }
            
            if( elements == null )
            {
                elements = Collections.singletonList( this.rootModelElement );
            }
            
            return elements;
        }
    }
    
    protected void handleXmlNodeChange( final Node xmlNode )
    {
        handleXmlNodeChange( xmlNode, getModelElements( xmlNode ) );
    }
    
    protected void handleXmlNodeChange( final Node xmlNode,
                                        final List<IModelElement> nearestMatchModelElements )
    {
        DelayedTasksExecutor.schedule( new RefreshElementTask( nearestMatchModelElements ) );
    }
    
    private static void addAdapter( final IDOMNode node,
                                    final INodeAdapter adapter )
    {
        node.addAdapter( adapter );
        
        final NodeList children = node.getChildNodes();
        
        for( int i = 0, n = children.getLength(); i < n; i++ )
        {
            addAdapter( (IDOMNode) children.item( i ), adapter );
        }
    }
    
    protected static final class RefreshElementTask
    
        extends DelayedTasksExecutor.Task
        
    {
        private final List<IModelElement> elements;
        
        public RefreshElementTask( final List<IModelElement> elements )
        {
            this.elements = elements;
        }
        
        @Override
        public boolean equals( final Object obj )
        {
            if( obj != null && obj instanceof RefreshElementTask )
            {
                return ( this.elements.equals( ( (RefreshElementTask) obj ).elements ) );
            }
            
            return false;
        }
        
        @Override
        public int hashCode()
        {
            return this.elements.hashCode();
        }
        
        public void run()
        {
            for( final IModelElement element : this.elements )
            {
                element.refresh( false, true );
            }
        }
    }

    protected static final class RefreshPropertyTask
    
        extends DelayedTasksExecutor.Task
        
    {
        private final IModelElement element;
        private final ModelProperty property;
        
        public RefreshPropertyTask( final IModelElement element,
                                    final ModelProperty property )
        {
            this.element = element;
            this.property = property;
        }
        
        @Override
        public boolean equals( final Object obj )
        {
            if( obj != null && obj instanceof RefreshPropertyTask )
            {
                final RefreshPropertyTask task = (RefreshPropertyTask) obj;
                return this.element == task.element && this.property == task.property;
            }
            
            return false;
        }
        
        @Override
        public int hashCode()
        {
            return this.element.hashCode() + this.property.hashCode();
        }
        
        public void run()
        {
            this.element.refresh( this.property, false, true );
        }
    }

    private final class Scrubber
    
        extends Thread
        
    {
        private boolean stopRequested = false;
        
        public void run()
        {
            final Map<Node,List<IModelElement>> nodeToModelElementsMap = XmlEditorResourceStore.this.nodeToModelElementsMap;
            
            while( true )
            {
                synchronized( this )
                {
                    try
                    {
                        sleep( 10000 );
                    }
                    catch( InterruptedException e ) {}
                    
                    if( this.stopRequested == true )
                    {
                        return;
                    }
                }
                
                synchronized( nodeToModelElementsMap )
                {
                    for( Iterator<Map.Entry<Node,List<IModelElement>>> itr = nodeToModelElementsMap.entrySet().iterator();
                         itr.hasNext(); )
                    {
                        final Map.Entry<Node,List<IModelElement>> entry = itr.next();
                        
                        if( entry.getKey().getParentNode() == null )
                        {
                            /* final String nodeClassName = entry.getKey().getClass().getSimpleName();
                            final int nodeHashCode = entry.getKey().hashCode();
                            final String modelElementClassName = entry.getValue().getClass().getSimpleName();
                            final int modelElementHashCode = entry.getValue().hashCode();
                            
                            System.err.println( "SCRUBBER REMOVED: " + nodeClassName + ":" + nodeHashCode + " -> " +
                                                modelElementClassName + ":" + modelElementHashCode ); */
                            
                            itr.remove();
                        }
                    }
                }
            }
        }
        
        @SuppressWarnings( "unused" )
        
        public synchronized void dispose()
        {
            this.stopRequested = true;
        }
    }
    
}