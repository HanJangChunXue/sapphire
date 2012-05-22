/******************************************************************************
 * Copyright (c) 2012 Oracle and Liferay
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Konstantin Komissarchik - initial implementation and ongoing maintenance
 *    Gregory Amerson - [372359] Provide means to extend the behavior of adapt methods
 ******************************************************************************/

package org.eclipse.sapphire.modeling;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.sapphire.Event;
import org.eclipse.sapphire.FilteredListener;
import org.eclipse.sapphire.Listener;
import org.eclipse.sapphire.ListenerContext;
import org.eclipse.sapphire.modeling.ModelPath.AllDescendentsSegment;
import org.eclipse.sapphire.modeling.ModelPath.AllSiblingsSegment;
import org.eclipse.sapphire.modeling.ModelPath.ModelRootSegment;
import org.eclipse.sapphire.modeling.ModelPath.ParentElementSegment;
import org.eclipse.sapphire.modeling.ModelPath.TypeFilterSegment;
import org.eclipse.sapphire.modeling.annotations.ClearOnDisable;
import org.eclipse.sapphire.modeling.util.NLS;
import org.eclipse.sapphire.services.AdapterService;
import org.eclipse.sapphire.services.DefaultValueService;
import org.eclipse.sapphire.services.DependenciesAggregationService;
import org.eclipse.sapphire.services.DerivedValueService;
import org.eclipse.sapphire.services.EnablementService;
import org.eclipse.sapphire.services.EqualityService;
import org.eclipse.sapphire.services.InitialValueService;
import org.eclipse.sapphire.services.Service;
import org.eclipse.sapphire.services.ServiceContext;
import org.eclipse.sapphire.services.ValidationAggregationService;
import org.eclipse.sapphire.services.ValidationService;
import org.eclipse.sapphire.services.internal.ElementInstanceServiceContext;
import org.eclipse.sapphire.services.internal.PropertyInstanceServiceContext;

/**
 * @author <a href="mailto:konstantin.komissarchik@oracle.com">Konstantin Komissarchik</a>
 * @author <a href="mailto:gregory.amerson@liferay.com">Gregory Amerson</a>
 */

public abstract class ModelElement extends ModelParticle implements IModelElement
{
    private final ModelElementType type;
    private final ModelProperty parentProperty;
    private Status validation;
    private final ListenerContext listeners = new ListenerContext();
    private final Map<ModelProperty,Boolean> enablementStatuses;
    private boolean enablementServicesInitialized;
    private ElementInstanceServiceContext elementServiceContext;
    private final Map<ModelProperty,PropertyInstanceServiceContext> propertyServiceContexts;
    private boolean disposed = false;
    
    public ModelElement( final ModelElementType type,
                         final IModelParticle parent,
                         final ModelProperty parentProperty,
                         final Resource resource )
    {
        super( parent, resource );
        
        this.type = type;
        this.parentProperty = parentProperty;
        this.validation = null;
        this.enablementStatuses = new HashMap<ModelProperty,Boolean>();
        this.propertyServiceContexts = new HashMap<ModelProperty,PropertyInstanceServiceContext>();
        
        if( parent != null )
        {
            final ModelElement p = (ModelElement) parent.nearest( IModelElement.class );
            this.listeners.coordinate( p.listeners );
        }
        
        resource.init( this );
        
        final Map<ModelProperty,Listener> listenerByProperty = new HashMap<ModelProperty,Listener>();
        final Map<ModelProperty,Set<ModelPath>> dependenciesByProperty = new HashMap<ModelProperty,Set<ModelPath>>();
        
        for( final ModelProperty property : type.properties() )
        {
            final Set<ModelPath> dependencies = service( property, DependenciesAggregationService.class ).dependencies();
            
            if( ! dependencies.isEmpty() )
            {
                final Listener listener = new FilteredListener<PropertyEvent>()
                {
                    @Override
                    protected void handleTypedEvent( final PropertyEvent event )
                    {
                        if( ! disposed() )
                        {
                            refresh( property );
                        }
                    }
                };
                
                listenerByProperty.put( property, listener );
                dependenciesByProperty.put( property, dependencies );
    
                for( ModelPath dependency : dependencies )
                {
                    attach( listener, dependency );
                }
            }

            final Listener triggerRefreshOnServiceEventListener = new Listener()
            {
                @Override
                public void handle( final Event event )
                {
                    if( ! disposed() )
                    {
                        refresh( property );
                    }
                }
            };
            
            for( ValidationService validationService : services( property, ValidationService.class ) )
            {
                validationService.attach( triggerRefreshOnServiceEventListener );
            }
            
            final DefaultValueService defaultValueService = service( property, DefaultValueService.class );
            
            if( defaultValueService != null )
            {
                defaultValueService.attach( triggerRefreshOnServiceEventListener );
            }
            
            final DerivedValueService derivedValueService = service( property, DerivedValueService.class );
            
            if( derivedValueService != null )
            {
                derivedValueService.attach( triggerRefreshOnServiceEventListener );
            }

            if( property.hasAnnotation( ClearOnDisable.class ) )
            {
                Listener listener = null;
                
                if( property instanceof ValueProperty )
                {
                    final ValueProperty prop = (ValueProperty) property;
                    
                    listener = new FilteredListener<PropertyEnablementEvent>()
                    {
                        @Override
                        protected void handleTypedEvent( final PropertyEnablementEvent event )
                        {
                            if( event.before() == true && event.after() == false )
                            {
                                write( prop, null );
                            }
                        }
                    };
                }
                else if( property instanceof ListProperty )
                {
                    final ListProperty prop = (ListProperty) property;
                    
                    listener = new FilteredListener<PropertyEnablementEvent>()
                    {
                        @Override
                        protected void handleTypedEvent( final PropertyEnablementEvent event )
                        {
                            if( event.before() == true && event.after() == false )
                            {
                                read( prop ).clear();
                            }
                        }
                    };
                }
                else if( property instanceof ElementProperty && ! ( property instanceof ImpliedElementProperty ) )
                {
                    final ElementProperty prop = (ElementProperty) property;
                    
                    listener = new FilteredListener<PropertyEnablementEvent>()
                    {
                        @Override
                        protected void handleTypedEvent( final PropertyEnablementEvent event )
                        {
                            if( event.before() == true && event.after() == false )
                            {
                                read( prop ).remove();
                            }
                        }
                    };
                }
                
                if( listener != null )
                {
                    attach( listener, property.getName() );
                }
            }
        }
        
        if( ! listenerByProperty.isEmpty() )
        {
            final Listener disposeListener = new FilteredListener<ElementDisposeEvent>()
            {
                @Override
                protected void handleTypedEvent( final ElementDisposeEvent event )
                {
                    for( Map.Entry<ModelProperty,Listener> entry : listenerByProperty.entrySet() )
                    {
                        final ModelProperty property = entry.getKey();
                        final Listener listener = entry.getValue();
                        
                        for( ModelPath dependency : dependenciesByProperty.get( property ) )
                        {
                            detach( listener, dependency );
                        }
                    }
                }
            };
            
            attach( disposeListener );
        }
    }
    
    public ModelElementType type()
    {
        return this.type;
    }
    
    public ModelProperty getParentProperty()
    {
        return this.parentProperty;
    }
    
    public final void initialize()
    {
        for( ModelProperty property : properties() ) 
        {
            if( property instanceof ValueProperty ) 
            {
                final InitialValueService initialValueService = service( property, InitialValueService.class );
                
                if( initialValueService != null ) 
                {
                    write( (ValueProperty) property, initialValueService.value() );
                }
            }
        }
    }
    
    public List<ModelProperty> properties()
    {
        return this.type.properties();
    }

    public <T extends ModelProperty> T property( final String name )
    {
        return this.type.property( name );
    }

    public Object read( final ModelProperty property )
    {
        final String msg = NLS.bind( Resources.cannotReadProperty, property.getName() );
        throw new IllegalArgumentException( msg );
    }
    
    @SuppressWarnings( "unchecked" )
    
    public final <T> Value<T> read( final ValueProperty property )
    {
        return (Value<T>) read( (ModelProperty) property );
    }
    
    @SuppressWarnings( "unchecked" )
    
    public final <T extends IModelElement> ModelElementHandle<T> read( final ElementProperty property )
    {
        return (ModelElementHandle<T>) read( (ModelProperty) property );
    }

    @SuppressWarnings( "unchecked" )
    
    public final <T extends IModelElement> T read( final ImpliedElementProperty property )
    {
        return (T) read( (ModelProperty) property );
    }

    @SuppressWarnings( "unchecked" )
    
    public final <T extends IModelElement> ModelElementList<T> read( final ListProperty property )
    {
        return (ModelElementList<T>) read( (ModelProperty) property );
    }

    @SuppressWarnings( "unchecked" )
    
    public final <T> Transient<T> read( final TransientProperty property )
    {
        return (Transient<T>) read( (ModelProperty) property );
    }
    
    public final SortedSet<String> read( final ModelPath path )
    {
        final SortedSet<String> result = new TreeSet<String>();
        read( path, result );
        return result;
    }

    public final void read( final ModelPath path,
                            final Collection<String> result )
    {
        synchronized( root() )
        {
            final ModelPath.Segment head = path.head();
            
            if( head instanceof ModelRootSegment )
            {
                ( (IModelElement) root() ).read( path.tail(), result );
            }
            else if( head instanceof ParentElementSegment )
            {
                IModelParticle parent = parent();
                
                if( parent == null )
                {
                    logInvalidModelPathMessage( path );
                    return;
                }
                else
                {
                    if( parent instanceof ModelElementList<?> )
                    {
                        parent = parent.parent();
                    }
                }
                
                ( (IModelElement) parent ).read( path.tail(), result );
            }
            else if( head instanceof AllSiblingsSegment )
            {
                IModelParticle parent = parent();
                
                if( parent == null || ! ( parent instanceof ModelElementList<?> ) )
                {
                    logInvalidModelPathMessage( path );
                    return;
                }
                
                parent = parent.parent();
                
                final ModelPath p = ( new ModelPath( getParentProperty().getName() ) ).append( path.tail() );
                ( (IModelElement) parent ).read( p, result );
            }
            else if( head instanceof AllDescendentsSegment )
            {
                for( ModelProperty property : properties() )
                {
                    final Object obj = read( property );
                    
                    if( obj instanceof Value<?> )
                    {
                        final String val = ( (Value<?>) obj ).getText();
                        
                        if( val != null )
                        {
                            result.add( val );
                        }
                    }
                    else if( obj instanceof IModelElement )
                    {
                        ( (IModelElement) obj ).read( path, result );
                    }
                    else if( obj instanceof ModelElementList<?> )
                    {
                        for( IModelElement entry : (ModelElementList<?>) obj )
                        {
                            entry.read( path, result );
                        }
                    }
                }
            }
            else if( head instanceof TypeFilterSegment )
            {
                final String t = type().getSimpleName();
                boolean match = false;
                
                for( String type : ( (TypeFilterSegment) head ).getTypes() )
                {
                    if( type.equalsIgnoreCase( t ) )
                    {
                        match = true;
                        break;
                    }
                }
                
                if( match )
                {
                    read( path.tail(), result );
                }
            }
            else
            {
                final String propertyName = ( (ModelPath.PropertySegment) head ).getPropertyName();
                final ModelProperty property = property( propertyName );

                if( property == null )
                {
                    logInvalidModelPathMessage( path );
                    return;
                }
                
                final Object obj = read( property );
                
                if( obj instanceof Value<?> )
                {
                    final String val = ( (Value<?>) obj ).getText();
                    
                    if( val != null )
                    {
                        result.add( val );
                    }
                    
                    if( path.length() != 1 )
                    {
                        logInvalidModelPathMessage( path );
                        return;
                    }
                }
                else if( obj instanceof IModelElement )
                {
                    ( (IModelElement) obj ).read( path.tail(), result );
                }
                else if( obj instanceof ModelElementList<?> )
                {
                    for( IModelElement entry : (ModelElementList<?>) obj )
                    {
                        entry.read( path.tail(), result );
                    }
                }
            }
        }
    }
    
    public void write( final ValueProperty property,
                       final Object value )
    {
        final String msg = NLS.bind( Resources.cannotWriteProperty, property.getName() );
        throw new IllegalArgumentException( msg );
    }
    
    public void write( final TransientProperty property,
                       final Object value )
    {
        final String msg = NLS.bind( Resources.cannotWriteProperty, property.getName() );
        throw new IllegalArgumentException( msg );
    }
    
    public final void refresh()
    {
        refresh( false, false );
    }
    
    public final void refresh( final boolean force )
    {
        refresh( force, false );
    }
    
    public final void refresh( final boolean force,
                               final boolean deep )
    {
        synchronized( root() )
        {
            for( ModelProperty property : properties() )
            {
                refresh( property, force, deep );
            }
        }
    }
    
    public final void refresh( final ModelProperty property )
    {
        refresh( property, false, false );
    }
    
    public final void refresh( final ModelProperty property,
                               final boolean force )
    {
        refresh( property, force, false );
    }
    
    public final void refresh( final ModelProperty property,
                               final boolean force,
                               final boolean deep )
    {
        refreshProperty( property, force );
        
        if( deep )
        {
            if( property instanceof ElementProperty )
            {
                final IModelElement child;
                
                if( property instanceof ImpliedElementProperty )
                {
                    child = read( (ImpliedElementProperty) property );
                }
                else
                {
                    child = read( (ElementProperty) property ).element();
                }
                
                if( child != null )
                {
                    child.refresh( force, true );
                }
            }
            else if( property instanceof ListProperty )
            {
                for( IModelElement child : read( (ListProperty) property ) )
                {
                    child.refresh( force, true );
                }
            }
        }
    }

    protected void refreshProperty( final ModelProperty property,
                                    final boolean force )
    {
        // The default implementation does not do anything.
    }
    
    public final void copy( final IModelElement element )
    {
        if( this.type != element.type() )
        {
            throw new IllegalArgumentException();
        }
        
        for( ModelProperty property : this.type.properties() )
        {
            if( ! property.isReadOnly() )
            {
                if( property instanceof ValueProperty )
                {
                    final ValueProperty prop = (ValueProperty) property;
                    write( prop, element.read( prop ).getText( false ) );
                }
                else if( property instanceof ImpliedElementProperty )
                {
                    final ImpliedElementProperty prop = (ImpliedElementProperty) property;
                    read( prop ).copy( element.read( prop ) );
                }
                else if( property instanceof ElementProperty )
                {
                    final ElementProperty prop = (ElementProperty) property;
                    final IModelElement elementChild = element.read( prop ).element();
                    final ModelElementHandle<?> handle = read( prop );
                    
                    if( elementChild == null )
                    {
                        handle.remove();
                    }
                    else
                    {
                        final IModelElement thisChild = handle.element( true, elementChild.type() );
                        thisChild.copy( elementChild );
                    }
                }
                else if( property instanceof ListProperty )
                {
                    final ListProperty prop = (ListProperty) property;
                    final ModelElementList<?> list = read( prop );
                    
                    list.clear();
                    
                    for( final IModelElement elementChild : element.read( prop ) )
                    {
                        final IModelElement thisChild = list.insert( elementChild.type() );
                        thisChild.copy( elementChild );
                    }
                }
                else if( property instanceof TransientProperty )
                {
                    final TransientProperty prop = (TransientProperty) property;
                    write( prop, element.read( prop ).content() );
                }
            }
        }
    }
    
    @Override
    public final boolean equals( final Object obj )
    {
        synchronized( root() )
        {
            boolean result = false;
            
            if( this == obj )
            {
                result = true;
            }
            else if( obj instanceof IModelElement && ! disposed() )
            {
                final EqualityService equalityService = service( EqualityService.class );
                
                if( equalityService != null )
                {
                    result = equalityService.doEquals( obj );
                }
            }
            
            return result;
        }
    }

    @Override
    public final int hashCode()
    {
        synchronized( root() )
        {
            int result;
         
            if( disposed() )
            {
                result = super.hashCode();
            }
            else
            {
                final EqualityService equalityService = service( EqualityService.class );
                
                if( equalityService != null )
                {
                    result = equalityService.doHashCode();
                }
                else
                {
                    result = super.hashCode();
                }
            }
            
            return result;
        }
    }

    public final <S extends Service> S service( final Class<S> serviceType )
    {
        final List<S> services = services( serviceType );
        return ( services.isEmpty() ? null : services.get( 0 ) );
    }

    public final <S extends Service> List<S> services( final Class<S> serviceType )
    {
        synchronized( root() )
        {
            if( this.elementServiceContext == null )
            {
                this.elementServiceContext = new ElementInstanceServiceContext( this );
            }
        }
        
        return this.elementServiceContext.services( serviceType );
    }

    public final <S extends Service> S service( final ModelProperty property,
                                                final Class<S> serviceType )
    {
        final List<S> services = services( property, serviceType );
        return ( services.isEmpty() ? null : services.get( 0 ) );
    }
    
    public final <S extends Service> List<S> services( final ModelProperty property,
                                                       final Class<S> serviceType )
    {
        PropertyInstanceServiceContext context;
        
        synchronized( root() )
        {
            context = this.propertyServiceContexts.get( property );
            
            if( context == null )
            {
                context = new PropertyInstanceServiceContext( this, property );
                this.propertyServiceContexts.put( property, context );
            }
        }
        
        return context.services( serviceType );
    }

    public final boolean enabled( final ModelProperty property )
    {
        synchronized( root() )
        {
            Boolean status = this.enablementStatuses.get( property );
            
            if( status == null )
            {
                refreshPropertyEnablement( property, true );
                status = this.enablementStatuses.get( property );
            }
            
            return status;
        }
    }
    
    protected final EnablementRefreshResult refreshPropertyEnablement( final ModelProperty property )
    {
        return refreshPropertyEnablement( property, false );
    }
    
    private final EnablementRefreshResult refreshPropertyEnablement( final ModelProperty property,
                                                                     final boolean notifyListenersIfNecessary )
    {
        synchronized( root() )
        {
            if( ! this.enablementServicesInitialized )
            {
                for( final ModelProperty prop : this.type.properties() )
                {
                    final Listener enablementServiceListener = new Listener()
                    {
                        @Override
                        public void handle( final Event event )
                        {
                            if( ! disposed() )
                            {
                                refreshPropertyEnablement( prop, true );
                            }
                        }
                    };
                    
                    for( EnablementService service : services( prop, EnablementService.class ) )
                    {
                        service.attach( enablementServiceListener );
                    }
                }
                
                this.enablementServicesInitialized = true;
            }
            
            boolean newState = true;
            
            for( EnablementService service : services( property, EnablementService.class ) )
            {
                newState = ( newState && service.enablement() );
                
                if( newState == false )
                {
                    break;
                }
            }
            
            final Boolean oldState = this.enablementStatuses.get( property );
            
            if( oldState == null )
            {
                this.enablementStatuses.put( property, newState );
            }
            else if( ! oldState.equals( newState ) )
            {
                this.enablementStatuses.put( property, newState );
                
                if( notifyListenersIfNecessary )
                {
                    broadcast( new PropertyEnablementEvent( this, property, oldState, newState ) );
                }
            }
            
            return new EnablementRefreshResult( oldState, newState );
        }
    }

    public Status validation()
    {
        if( this.validation == null )
        {
            refreshValidationResult();
        }
        
        return this.validation;
    }
    
    private void refreshValidationResult()
    {
        final ValidationAggregationService service = service( ValidationAggregationService.class );
        final Status validation = service.validation();
        
        if( this.validation == null )
        {
            this.validation = validation;
            
            final Listener validationAggregationServiceListener = new Listener()
            {
                @Override
                public void handle( final Event event )
                {
                    refreshValidationResult();
                }
            };
            
            service.attach( validationAggregationServiceListener );
        }
        else if( ! this.validation.equals( validation ) )
        {
            final Status oldValidationState = this.validation;
            this.validation = validation;
            
            broadcast( new ElementValidationEvent( this, oldValidationState, this.validation ) );
        }
    }
    
    public final boolean attach( final Listener listener )
    {
        return this.listeners.attach( listener );
    }
    
    public final void attach( final Listener listener,
                              final String path )
    {
        attach( listener, new ModelPath( path ) );
    }
    
    public final void attach( final Listener listener,
                              final ModelPath path )
    {
        final ModelPath.Segment head = path.head();
        
        if( head instanceof ModelRootSegment )
        {
            ( (IModelElement) root() ).attach( listener, path.tail() );
        }
        else if( head instanceof ParentElementSegment )
        {
            IModelParticle parent = parent();
            
            if( parent == null )
            {
                logInvalidModelPathMessage( path );
                return;
            }
            else
            {
                if( parent instanceof ModelElementList<?> )
                {
                    parent = parent.parent();
                }
            }
            
            ( (IModelElement) parent ).attach( listener, path.tail() );
        }
        else if( head instanceof AllSiblingsSegment )
        {
            IModelParticle parent = parent();
            
            if( parent == null || ! ( parent instanceof ModelElementList<?> ) )
            {
                logInvalidModelPathMessage( path );
                return;
            }
            
            parent = parent.parent();
            
            final ModelPath p = ( new ModelPath( this.parentProperty.getName() ) ).append( path.tail() );
            ( (IModelElement) parent ).attach( listener, p );
        }
        else if( head instanceof AllDescendentsSegment )
        {
            if( attach( new PropagationListener( path, listener ) ) )
            {
                for( ModelProperty property : this.type.properties() )
                {
                    if( property instanceof ImpliedElementProperty )
                    {
                        read( (ImpliedElementProperty) property ).attach( listener, path );
                    }
                    else if( property instanceof ElementProperty )
                    {
                        final IModelElement element = read( (ElementProperty) property ).element();
                        
                        if( element != null )
                        {
                            element.attach( listener, path );
                        }
                    }
                    else if( property instanceof ListProperty )
                    {
                        final ModelElementList<?> list = read( (ListProperty) property );
                        
                        for( IModelElement x : list )
                        {
                            x.attach( listener, path );
                        }
                    }
                    else
                    {
                        attach( PropertyEvent.filter( listener ) );
                    }
                }
            }
        }
        else if( head instanceof TypeFilterSegment )
        {
            final String t = this.type.getSimpleName();
            boolean match = false;
            
            for( String type : ( (TypeFilterSegment) head ).getTypes() )
            {
                if( type.equalsIgnoreCase( t ) )
                {
                    match = true;
                    break;
                }
            }
            
            if( match )
            {
                attach( listener, path.tail() );
            }
        }
        else
        {
            final String propertyName = ( (ModelPath.PropertySegment) head ).getPropertyName();
            final ModelProperty property = this.type.property( propertyName );
            
            if( property == null )
            {
                logInvalidModelPathMessage( path );
                return;
            }

            if( path.length() == 1 )
            {
                attach( PropertyEvent.filter( listener, property ) );
            }
            else
            {
                final ModelPath tail = path.tail();
                
                if( property instanceof ImpliedElementProperty )
                {
                    read( (ImpliedElementProperty) property ).attach( listener, tail );
                }
                else if( property instanceof ElementProperty )
                {
                    if( attach( PropertyEvent.filter( new PropagationListener( tail, listener ), property ) ) )
                    {
                        final IModelElement element = read( (ElementProperty) property ).element();
                        
                        if( element != null )
                        {
                            element.attach( listener, tail );
                        }
                    }
                }
                else if( property instanceof ListProperty )
                {
                    if( attach( PropertyEvent.filter( new PropagationListener( tail, listener ), property ) ) )
                    {
                        final ModelElementList<?> list = read( (ListProperty) property );
                        
                        for( IModelElement x : list )
                        {
                            x.attach( listener, tail );
                        }
                    }
                }
                else
                {
                    logInvalidModelPathMessage( path );
                }
            }
        }
    }
    
    public final boolean detach( final Listener listener )
    {
        return this.listeners.detach( listener );
    }
    
    public final void detach( final Listener listener,
                              final String path )
    {
        detach( listener, new ModelPath( path ) );
    }
    
    public final void detach( final Listener listener,
                              final ModelPath path )
    {
        final ModelPath.Segment head = path.head();
        
        if( head instanceof ModelRootSegment )
        {
            ( (IModelElement) root() ).detach( listener, path.tail() );
        }
        else if( head instanceof ParentElementSegment )
        {
            IModelParticle parent = parent();
            
            if( parent == null )
            {
                logInvalidModelPathMessage( path );
                return;
            }
            else
            {
                if( parent instanceof ModelElementList<?> )
                {
                    parent = parent.parent();
                }
            }
            
            ( (IModelElement) parent ).detach( listener, path.tail() );
        }
        else if( head instanceof AllSiblingsSegment )
        {
            IModelParticle parent = parent();
            
            if( parent == null || ! ( parent instanceof ModelElementList<?> ) )
            {
                logInvalidModelPathMessage( path );
                return;
            }
            
            parent = parent.parent();
            
            final ModelPath p = ( new ModelPath( this.parentProperty.getName() ) ).append( path.tail() );
            ( (IModelElement) parent ).detach( listener, p );
        }
        else if( head instanceof AllDescendentsSegment )
        {
            detach( new PropagationListener( path, listener ) );
            
            for( ModelProperty property : this.type.properties() )
            {
                if( property instanceof ImpliedElementProperty )
                {
                    read( (ImpliedElementProperty) property ).detach( listener, path );
                }
                else if( property instanceof ElementProperty )
                {
                    final IModelElement element = read( (ElementProperty) property ).element();
                    
                    if( element != null )
                    {
                        element.detach( listener, path );
                    }
                }
                else if( property instanceof ListProperty )
                {
                    final ModelElementList<?> list = read( (ListProperty) property );
                    
                    for( IModelElement x : list )
                    {
                        x.detach( listener, path );
                    }
                }
                else
                {
                    detach( PropertyEvent.filter( listener ) );
                }
            }
        }
        else if( head instanceof TypeFilterSegment )
        {
            final String t = this.type.getSimpleName();
            boolean match = false;
            
            for( String type : ( (TypeFilterSegment) head ).getTypes() )
            {
                if( type.equalsIgnoreCase( t ) )
                {
                    match = true;
                    break;
                }
            }
            
            if( match )
            {
                detach( listener, path.tail() );
            }
        }
        else
        {
            final String propertyName = ( (ModelPath.PropertySegment) head ).getPropertyName();
            final ModelProperty property = this.type.property( propertyName );
            
            if( property == null )
            {
                logInvalidModelPathMessage( path );
                return;
            }
            
            if( path.length() == 1 )
            {
                detach( PropertyEvent.filter( listener, property ) );
            }
            else
            {
                final ModelPath tail = path.tail();
                
                if( property instanceof ImpliedElementProperty )
                {
                    read( (ImpliedElementProperty) property ).detach( listener, tail );
                }
                else if( property instanceof ElementProperty )
                {
                    detach( PropertyEvent.filter( new PropagationListener( tail, listener ), property ) );

                    final IModelElement element = read( (ElementProperty) property ).element();
                        
                    if( element != null )
                    {
                        element.detach( listener, tail );
                    }
                }
                else if( property instanceof ListProperty )
                {
                    detach( PropertyEvent.filter( new PropagationListener( tail, listener ), property ) );
                    
                    final ModelElementList<?> list = read( (ListProperty) property );
                    
                    for( IModelElement x : list )
                    {
                        x.detach( listener, tail );
                    }
                }
                else
                {
                    logInvalidModelPathMessage( path );
                }
            }
        }
    }
    
    protected final void broadcast( final Event event )
    {
        this.listeners.broadcast( event );
    }
    
    protected final void broadcast()
    {
        this.listeners.broadcast();
    }
    
    final void broadcastPropertyContentEvent( final ModelProperty property )
    {
        broadcast( new PropertyContentEvent( this, property ) );
    }

    final void broadcastPropertyValidationEvent( final ModelProperty property,
                                                 final Status before,
                                                 final Status after )
    {
        broadcast( new PropertyValidationEvent( this, property, before, after ) );
    }
    
    public final boolean disposed()
    {
        synchronized( root() )
        {
            return this.disposed;
        }
    }
    
    public final void dispose()
    {
        synchronized( root() )
        {
            this.disposed = true;
            
            broadcast( new ElementDisposeEvent( this ) );
            
            for( ModelProperty property : this.type.properties() )
            {
                if( property instanceof ListProperty )
                {
                    for( IModelElement child : read( (ListProperty) property ) )
                    {
                        child.dispose();
                    }
                }
                else if( property instanceof ImpliedElementProperty )
                {
                    read( (ImpliedElementProperty) property ).dispose();
                }
                else if( property instanceof ElementProperty )
                {
                    final IModelElement child = read( (ElementProperty) property ).element( false );
                    
                    if( child != null )
                    {
                        child.dispose();
                    }
                }
            }
            
            try
            {
                resource().dispose();
            }
            catch( Exception e )
            {
                LoggingService.log( e );
            }
            
            if( this.elementServiceContext != null )
            {
                this.elementServiceContext.dispose();
            }
            
            for( ServiceContext context : this.propertyServiceContexts.values() )
            {
                context.dispose();
            }
        }
    }
    
    private void logInvalidModelPathMessage( final ModelPath path )
    {
        final String message 
            = NLS.bind( Resources.invalidModelPath, this.type.getModelElementClass().getName(), path.toString() );
        
        LoggingService.log( Status.createErrorStatus( message ) );
    }
    
    @Override
    public <A> A adapt( final Class<A> adapterType )
    {
        A result = null;

        for( AdapterService service : services( AdapterService.class ) )
        {
            result = service.adapt( adapterType );
            
            if( result != null )
            {
                break;
            }
        }

        if( result == null )
        {
            result = super.adapt( adapterType );
        }

        return result;
    }

    protected static final boolean equal( final String value1,
                                          final String value2 )
    {
        final String val1 = normalize( value1 );
        final String val2 = normalize( value2 );
        
        boolean valuesAreEqual = false;
        
        if( val1 == val2 )
        {
            valuesAreEqual = true;
        }
        else if( val1 != null && val2 != null )
        {
            valuesAreEqual = val1.equals( val2 );
        }
    
        return valuesAreEqual;
    }
    
    protected static final String normalize( final String value )
    {
        if( value != null && value.equals( "" ) )
        {
            return null;
        }
        
        return value;
    }
    
    protected final class EnablementRefreshResult
    {
        private final Boolean oldEnablementState;
        private final boolean newEnablementState;
        
        public EnablementRefreshResult( final Boolean before,
                                        final boolean after )
        {
            this.oldEnablementState = before;
            this.newEnablementState = after;
        }
        
        public Boolean before()
        {
            return this.oldEnablementState;
        }
        
        public boolean after()
        {
            return this.newEnablementState;
        }
        
        public boolean changed()
        {
            if( this.oldEnablementState == null )
            {
                return false;
            }
            else
            {
                return ( this.oldEnablementState != this.newEnablementState );
            }
        }
    }
    
    private final class PropagationListener extends Listener
    {
        private final ModelPath path;
        private final Listener listener;
        
        public PropagationListener( final ModelPath path,
                                    final Listener listener )
        {
            this.path = path;
            this.listener = PropertyEvent.filter( listener );
        }
        
        @Override
        public boolean equals( final Object obj )
        {
            if( obj instanceof PropagationListener )
            {
                final PropagationListener pl = (PropagationListener) obj;
                return this.path.equals( pl.path ) && ( this.listener == pl.listener );
            }
            
            return false;
        }
        
        @Override
        public int hashCode()
        {
            return this.path.hashCode() ^ this.listener.hashCode();
        }
        
        @Override
        public void handle( final Event event )
        {
            if( event instanceof PropertyContentEvent )
            {
                final ModelProperty property = ( (PropertyContentEvent) event ).property();
                
                if( property instanceof ListProperty )
                {
                    final ModelElementList<?> list = read( (ListProperty) property );
                    
                    for( IModelElement x : list )
                    {
                        x.attach( this.listener, this.path );
                    }
                    
                    this.listener.handle( event );
                }
                else if( property instanceof ElementProperty && ! ( property instanceof ImpliedElementProperty ) )
                {
                    final IModelElement element = read( (ElementProperty) property ).element();
                    
                    if( element != null )
                    {
                        element.attach( this.listener, this.path );
                    }
                    
                    this.listener.handle( event );
                }
            }
        }
    }
    
    private static final class Resources extends NLS
    {
        public static String invalidModelPath;
        public static String cannotReadProperty;
        public static String cannotWriteProperty;
        
        static
        {
            initializeMessages( ModelElement.class.getName(), Resources.class );
        }
    }
    
}
