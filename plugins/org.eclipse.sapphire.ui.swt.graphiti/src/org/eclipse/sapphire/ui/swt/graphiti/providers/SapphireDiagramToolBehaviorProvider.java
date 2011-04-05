/******************************************************************************
 * Copyright (c) 2011 Oracle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Shenxue Zhou - initial implementation and ongoing maintenance
 ******************************************************************************/

package org.eclipse.sapphire.ui.swt.graphiti.providers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.common.util.EList;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.context.IDoubleClickContext;
import org.eclipse.graphiti.features.custom.ICustomFeature;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.Image;
import org.eclipse.graphiti.mm.algorithms.Text;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.mm.pictograms.Shape;
import org.eclipse.graphiti.platform.IPlatformImageConstants;
import org.eclipse.graphiti.tb.DefaultToolBehaviorProvider;
import org.eclipse.graphiti.tb.IContextButtonPadData;
import org.eclipse.graphiti.tb.IDecorator;
import org.eclipse.graphiti.tb.ImageDecorator;
import org.eclipse.graphiti.ui.services.GraphitiUi;
import org.eclipse.sapphire.modeling.IModelElement;
import org.eclipse.sapphire.ui.Point;
import org.eclipse.sapphire.ui.def.HorizontalAlignment;
import org.eclipse.sapphire.ui.def.VerticalAlignment;
import org.eclipse.sapphire.ui.diagram.def.DecoratorPlacement;
import org.eclipse.sapphire.ui.diagram.def.IDiagramDecoratorDef;
import org.eclipse.sapphire.ui.diagram.def.IDiagramImageDecoratorDef;
import org.eclipse.sapphire.ui.diagram.def.IDiagramNodeProblemDecoratorDef;
import org.eclipse.sapphire.ui.diagram.def.ProblemDecoratorSize;
import org.eclipse.sapphire.ui.diagram.editor.DiagramNodePart;
import org.eclipse.sapphire.ui.swt.graphiti.features.SapphireDoubleClickNodeFeature;

/**
 * @author <a href="mailto:shenxue.zhou@oracle.com">Shenxue Zhou</a>
 */

public class SapphireDiagramToolBehaviorProvider extends DefaultToolBehaviorProvider 
{
	private static final int SMALL_ERROR_DECORATOR_WIDTH = 7;
	private static final int SMALL_ERROR_DECORATOR_HEIGHT = 8;
	private static final int LARGE_ERROR_DECORATOR_WIDTH = 16;
	private static final int LARGE_ERROR_DECORATOR_HEIGHT = 16;
	
	public SapphireDiagramToolBehaviorProvider(IDiagramTypeProvider dtp) 
	{
		super(dtp);
	}

	/**
	 * Override super class impl to not include "Remove"
	 */
	@Override
	protected void setGenericContextButtons(IContextButtonPadData data, PictogramElement pe, int identifiers) 
	{
		identifiers -= CONTEXT_BUTTON_REMOVE;
		super.setGenericContextButtons(data, pe, identifiers);
	}
	
	@Override
	public ICustomFeature getDoubleClickFeature(IDoubleClickContext context) 
	{
		PictogramElement[] pes = context.getPictogramElements();
		for (PictogramElement pe : pes)
		{
			if (pe instanceof ContainerShape)
			{
				Object bo = getFeatureProvider().getBusinessObjectForPictogramElement(pe);
				if (bo instanceof DiagramNodePart)
				{
					DiagramNodePart nodePart = (DiagramNodePart)bo;
					if (nodePart.getDefaultActionHandler() != null)
					{
						SapphireDoubleClickNodeFeature dblClikFeature = 
							new SapphireDoubleClickNodeFeature(getFeatureProvider(), nodePart);
						return dblClikFeature;
					}
				}
			}
		}
		return null;
	}
	
	@Override
	public IDecorator[] getDecorators(PictogramElement pe) 
	{
		IFeatureProvider featureProvider = getFeatureProvider();
		Object bo = featureProvider.getBusinessObjectForPictogramElement(pe);
		if (bo instanceof DiagramNodePart)
		{
			List<IDecorator> decoratorList = new ArrayList<IDecorator>();			
			DiagramNodePart nodePart = (DiagramNodePart)bo;
			
			if (nodePart.getProblemIndicatorDef().isShowDecorator().getContent())
			{
				addNodeProblemDecorator(pe, nodePart, decoratorList);
			}
			
			List<IDiagramImageDecoratorDef> imageDecorators = nodePart.getImageDecorators();
			for (IDiagramImageDecoratorDef imageDecorator : imageDecorators)
			{
				addNodeImageDecorator(pe, imageDecorator, decoratorList);
			}
			return decoratorList.toArray(new IDecorator[0]);
		}
		return super.getDecorators(pe);
	}
	
	@Override
	public String getToolTip(GraphicsAlgorithm ga) 
	{
		PictogramElement pe = ga.getPictogramElement();
		Object bo = getFeatureProvider().getBusinessObjectForPictogramElement(pe);
		if (bo instanceof DiagramNodePart) 
		{
			String name = ((DiagramNodePart) bo).getLabel();
			if (name != null && name.length() > 0) 
			{
				return name;
			}
		}
		return super.getToolTip(ga);
	}
	
	private void addNodeProblemDecorator(PictogramElement pe, DiagramNodePart nodePart, List<IDecorator> decoratorList)
	{
		IModelElement model = nodePart.getModelElement();
		IDiagramNodeProblemDecoratorDef decoratorDef = nodePart.getProblemIndicatorDef();
		IStatus status = model.validate();
		ImageDecorator imageRenderingDecorator = null;
		if (status.getSeverity() != IStatus.OK)
		{
			if (status.getSeverity() == IStatus.WARNING)
			{
				if (decoratorDef.getSize().getContent() == ProblemDecoratorSize.SMALL)
				{
					imageRenderingDecorator = new ImageDecorator(ErrorIndicatorImageProvider.IMG_WARNING_DECORATOR);
				}
				else
				{
					imageRenderingDecorator = new ImageDecorator(IPlatformImageConstants.IMG_ECLIPSE_WARNING);
				}
			}
			else if (status.getSeverity() == IStatus.ERROR)
			{
				if (decoratorDef.getSize().getContent() == ProblemDecoratorSize.SMALL)
				{
					imageRenderingDecorator = new ImageDecorator(ErrorIndicatorImageProvider.IMG_ERROR_DECORATOR);
				}
				else
				{
					imageRenderingDecorator = new ImageDecorator(IPlatformImageConstants.IMG_ECLIPSE_ERROR);
				}
			}
		}
		if (imageRenderingDecorator != null)
		{
			int indicatorWidth = decoratorDef.getSize().getContent() == ProblemDecoratorSize.LARGE ? LARGE_ERROR_DECORATOR_WIDTH : SMALL_ERROR_DECORATOR_WIDTH;
			int indicatorHeight = decoratorDef.getSize().getContent() == ProblemDecoratorSize.LARGE ? LARGE_ERROR_DECORATOR_HEIGHT : SMALL_ERROR_DECORATOR_HEIGHT;
			
			Point pt = getDecoratorPosition(pe, decoratorDef, indicatorWidth, indicatorHeight);
			imageRenderingDecorator.setX(pt.getX());
			imageRenderingDecorator.setY(pt.getY());
			imageRenderingDecorator.setMessage(status.getMessage());
			decoratorList.add(imageRenderingDecorator);
		}		
	}
	
	private void addNodeImageDecorator(PictogramElement pe, IDiagramImageDecoratorDef imageDecoratorDef,
								List<IDecorator> decoratorList)
	{
		String imageId = imageDecoratorDef.getImageId().getContent();
		if (imageId != null)
		{
			ImageDecorator imageRenderingDecorator = new ImageDecorator(imageId);
			org.eclipse.swt.graphics.Image image = GraphitiUi.getImageService().getImageForId(imageId);
			int imageWidth = image.getImageData().width;
			int imageHeight = image.getImageData().height;
			Point pt = getDecoratorPosition(pe, imageDecoratorDef, imageWidth, imageHeight);
			imageRenderingDecorator.setX(pt.getX());
			imageRenderingDecorator.setY(pt.getY());
			decoratorList.add(imageRenderingDecorator);
		}
	}
	
	private Point getDecoratorPosition(PictogramElement pe, IDiagramDecoratorDef decoratorDef, int decoratorWidth, int decoratorHeight)
	{
		GraphicsAlgorithm referencedGA = null;
		Text text = null;
		ContainerShape containerShape = (ContainerShape)pe;
		EList<Shape> children = containerShape.getChildren();
		for (Shape child : children)
		{
			GraphicsAlgorithm ga = child.getGraphicsAlgorithm();
			if (ga instanceof Image)
			{
				if (decoratorDef.getDecoratorPlacement().getContent() == DecoratorPlacement.IMAGE)
				{
					referencedGA = ga;
					break;
				}				
			}
			else if (ga instanceof Text)
			{
				if (decoratorDef.getDecoratorPlacement().getContent() == DecoratorPlacement.LABEL)
				{
					referencedGA = ga;
					break;
				}
				if (text == null)
				{
					text = (Text)ga;
				}
			}
		}		
		if (referencedGA == null)
		{
			referencedGA = text;
		}
		
		if (referencedGA != null)
		{
			HorizontalAlignment horizontalAlign = decoratorDef.getHorizontalAlignment().getContent();						
			int offsetX = 0;
			int offsetY = 0;
			if (horizontalAlign == HorizontalAlignment.RIGHT)
			{
				offsetX = referencedGA.getWidth() - decoratorWidth;
			    offsetX -= decoratorDef.getHorizontalMargin().getContent();
			}
			else if (horizontalAlign == HorizontalAlignment.LEFT)
			{
				offsetX += decoratorDef.getHorizontalMargin().getContent();
			}
			else if (horizontalAlign == HorizontalAlignment.CENTER)
			{
				offsetX = (referencedGA.getWidth() - decoratorWidth) >> 1;
			}
			
			VerticalAlignment verticalAlign = decoratorDef.getVerticalAlignment().getContent();
			
			if (verticalAlign == VerticalAlignment.BOTTOM)
			{
				offsetY = referencedGA.getHeight() - decoratorHeight;
				offsetY -= decoratorDef.getVerticalMargin().getContent();
			}
			else if (verticalAlign == VerticalAlignment.TOP)
			{
				offsetY += decoratorDef.getVerticalMargin().getContent();
			}
			else if (verticalAlign == VerticalAlignment.CENTER)
			{
				offsetY = (referencedGA.getHeight() - decoratorHeight) / 2;
			}
			
			return new Point(offsetX + referencedGA.getX(), offsetY + referencedGA.getY());
		}
		return new Point(0, 0);
	}
}
