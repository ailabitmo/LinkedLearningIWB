/*
 * Copyright (C) 2008-2013, fluid Operations AG
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.fluidops.iwb.layout;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.fluidops.ajax.FClientUpdate;
import com.fluidops.ajax.FClientUpdate.Prio;
import com.fluidops.ajax.components.FComponent;
import com.fluidops.ajax.components.FContainer;
import com.fluidops.ajax.components.FHTML;
import com.fluidops.ajax.components.FLabel;
import com.fluidops.ajax.components.FLinkButton;
import com.fluidops.ajax.components.FPopupWindow;
import com.fluidops.ajax.components.FTabPane2Lazy.ComponentHolder;
import com.fluidops.ajax.components.FTextArea;
import com.fluidops.iwb.api.EndpointImpl;
import com.fluidops.iwb.api.operator.Operator;
import com.fluidops.iwb.api.operator.OperatorFactory;
import com.fluidops.iwb.page.PageContext;
import com.fluidops.iwb.page.SearchPageContext;
import com.fluidops.iwb.ui.configuration.ReconfigureWidgetConfigurationForm;
import com.fluidops.iwb.util.UIUtil;
import com.fluidops.iwb.widget.AbstractWidget;
import com.fluidops.iwb.widget.BarChartWidget;
import com.fluidops.iwb.widget.GMapWidget;
import com.fluidops.iwb.widget.LineChartWidget;
import com.fluidops.iwb.widget.PieChartWidget;
import com.fluidops.iwb.widget.PivotWidget;
import com.fluidops.iwb.widget.SearchResultWidget;
import com.fluidops.iwb.widget.TableResultWidget;
import com.fluidops.iwb.widget.TimelineWidget;
import com.fluidops.iwb.widget.Widget;
import com.fluidops.util.Rand;
import com.fluidops.util.StringUtil;

/**
 * Arrange widgets in tabs. This is a specific class to be used from the HybridSearchServlet. 
 * The difference with the standard TabWidgetContainer is that
 * the widgets are embedded into containers which also include a toolbar in the top right corner.
 * 
 * @author andriy.nikolov
 */
public class AdHocSearchTabWidgetContainer extends TabWidgetContainer
{
	
	private static enum ToolbarOption {
		CAN_EDIT, CAN_COPY_QUERY, CAN_COPY_WIDGET
	}

	private static final Logger logger = Logger.getLogger(AdHocSearchTabWidgetContainer.class);
	
	
	public AdHocSearchTabWidgetContainer() 	{
		super();
	}
	
	
	@Override
	public void postRegistration( final PageContext pc )
	{
		this.pc = pc;
		
		if(!(pc instanceof SearchPageContext))
			throw new IllegalArgumentException("Not a search result page: page context is not an instance of SearchPageContext");
		
		if(tabWidgets.get(SearchResultWidget.class)!=null)
			addTab(SearchResultWidget.class, "result", "Table View", "nav_table");

		if(tabWidgets.get(PivotWidget.class)!=null)
			addTab(PivotWidget.class, "pivot", "Pivot View", "nav_pivot");
		
		if(tabWidgets.get(BarChartWidget.class)!=null)
			addTab(BarChartWidget.class, "bar chart", "Bar Chart", "nav_barchart");
		
		if(tabWidgets.get(LineChartWidget.class)!=null)
			addTab(LineChartWidget.class, "line chart", "Line Chart", "nav_linechart");
		
		if(tabWidgets.get(PieChartWidget.class)!=null)
			addTab(PieChartWidget.class, "pie chart", "Pie Chart", "nav_piechart");
		
		if(tabWidgets.get(TimelineWidget.class)!=null) 
			addTab(TimelineWidget.class, "timeline", "Timeline", "nav_timeline");
		
		if(tabWidgets.get(GMapWidget.class)!=null) 
			addTab(GMapWidget.class, "gmap", "Google Map", "nav_gmap");
		
	    // for JUnit-test only, since we cannot set the pagecontext-session artificially
	    if (pc.session != null)
	    {
	    	Object state = pc.session.getSessionState("activeLabel");
	    	if (state!=null)
	    		tabPane.setActiveLabelWithoutRender((String)state);
	    }	    
	}

	
	
	private void addTab(Class<? extends Widget<?>> clazz, String divText, String label, String cssClass) {
		
		StringBuilder htmlBuilder = new StringBuilder("<div style=\"width:100%; height:45px; font-size:0;\">");
		htmlBuilder.append(divText);
		htmlBuilder.append("</div><div class=\"wikiViewHover\">");
		htmlBuilder.append(label);
		htmlBuilder.append("</div>");
		
		Set<ToolbarOption> toolbarOptions = getToolbarOptions(clazz);
		
		if(!toolbarOptions.isEmpty()) {
			// Show toolbar
			LazyWidgetContainerComponentHolder componentHolder;
			componentHolder = new LazyWidgetContainerComponentHolder((SearchPageContext)pc, tabWidgets.get(clazz), tabWidgets.get(this.getWidgetClassToCopy(clazz)), toolbarOptions);
			tabPane.addLazyView(
					label, 
					componentHolder,
					cssClass);
		} else {
			// Just show the widget itself
			tabPane.addView(
					label, 
					tabWidgets.get(clazz).getComponentUAE(Rand.getIncrementalFluidUUID()),
					cssClass);
		}
	}
	
	
	private Set<ToolbarOption> getToolbarOptions(Class<? extends Widget<?>> widgetClassToShow) {
		Set<ToolbarOption> res = new HashSet<ToolbarOption>();
		if(canCopyQuery(widgetClassToShow))
			res.add(ToolbarOption.CAN_COPY_QUERY);
		if(canCopyWidget(widgetClassToShow))
			res.add(ToolbarOption.CAN_COPY_WIDGET);
		if(canEditWidgetConfig(widgetClassToShow))
			res.add(ToolbarOption.CAN_EDIT);
		return res;
	}
	
	private boolean canCopyQuery(Class<? extends Widget<?>> widgetClassToShow) {
		if(widgetClassToShow.equals(PivotWidget.class) || widgetClassToShow.equals(GMapWidget.class))
			return false;
		if(widgetClassToShow.equals(SearchResultWidget.class)) {
			String queryType = ((SearchPageContext)pc).queryType;
			if(StringUtil.isNullOrEmpty(queryType))
				return false;
			
			return queryType.equals("SELECT") || queryType.equals("CONSTRUCT");
		}
		return true;
	}
	
	private boolean canCopyWidget(Class<? extends Widget<?>> widgetClassToShow) {
		if(widgetClassToShow.equals(PivotWidget.class))
			return false;
		if(widgetClassToShow.equals(SearchResultWidget.class))
			return tabWidgets.containsKey(TableResultWidget.class);
		return true;
	}
	
	private boolean canEditWidgetConfig(Class<? extends Widget<?>> widgetClassToShow) {
		if(widgetClassToShow.equals(PivotWidget.class))
			return false;
		if(widgetClassToShow.equals(SearchResultWidget.class))
			return false;
		return true;
	}
	
	/**
	 * In some cases a widget which is explicitly shown on the search result page cannot be copied to another page 
	 * (particularly, the SearchResultWidget, which is linked to the search result page). 
	 * Instead, the configuration of another widget has to be copied and inserted to produce the same output (e.g. TableResultWidget).
	 * This method returns the class of the corresponding widget.
	 * 
	 * @param widgetToShow
	 * @return
	 */
	private Class<? extends Widget<?>> getWidgetClassToCopy(Class<? extends Widget<?>> widgetClassToShow) {
		if(widgetClassToShow.equals(SearchResultWidget.class))
			return TableResultWidget.class;
		return widgetClassToShow;
	}
	
	// Due to the implementation of FTabPane2Lazy, the component held by a ComponentHolder should only contain a single child. 
	// Because of this, we embed a container, which includes the toolbar buttons and the widget component,
	// within a supercontainer and return this supercontainer.
	private static class LazyWidgetContainerComponentHolder implements ComponentHolder {
		private Widget<?> widget;
		private FContainer cachedWidgetContainer;
		private Widget<?> widgetToCopy = null;
		private Set<ToolbarOption> toolbarOptions;
		private final SearchPageContext pc;
			
		// private FPopupWindow popup;
			
		private static final String[] NO_CSS_URLS = new String[0];

		public LazyWidgetContainerComponentHolder(SearchPageContext pc, Widget<?> widget, Widget<?> widgetToCopy, Set<ToolbarOption> toolbarOptions) {
			this.widget = widget;
			this.widgetToCopy = widgetToCopy;
			this.toolbarOptions = toolbarOptions;
			this.pc = pc;
		}
		
		@Override
		public FComponent getComponent() {

			if(cachedWidgetContainer == null) 
				cachedWidgetContainer = embedWidgetInContainer(widget, widgetToCopy);
			return cachedWidgetContainer;
		}

		@Override
		public String[] jsURLs() {
			return ((AbstractWidget<?>)widget).jsURLs().toArray(new String[0]);
		}

		@Override
		public String[] cssURLs() {
			return NO_CSS_URLS;
		}
			
		private FContainer embedWidgetInContainer(final Widget<?> widgetToShow, final Widget<?> widgetToCopy) {

			String widgetComponentId = Rand.getIncrementalFluidUUID();
			
			// embeddingContainer includes the widget component itself and the menu toolbar.
			final FContainer embeddingContainer = new FContainer(Rand.getIncrementalFluidUUID());
			FContainer menuContainer = new FContainer(Rand.getIncrementalFluidUUID());
			embeddingContainer.add(menuContainer);

			// widgetWrapperContainer includes the actual widget component.
			// Used to avoid making conflicting style changes on the widget component itself.
			FContainer widgetWrapperContainer = new FContainer(Rand.getIncrementalFluidUUID());
			embeddingContainer.add(widgetWrapperContainer);
			
			// embeddingSuperContainer provides an extra wrapper over
			// the embeddingContainer and serves as the actual top-level tab container.
			// This is required because the tab container must contain only a
			// single child component:
			// otherwise switching between tabs fails.
			FContainer embeddingSuperContainer = new FContainer(Rand.getIncrementalFluidUUID());
				
			embeddingSuperContainer.add(embeddingContainer);
			
			FLinkButton buttonEdit = null;
			FLinkButton buttonSaveChart = null;
			FLinkButton buttonSaveQuery = null;
			FLinkButton buttonEditQuery = null;
			
			if(toolbarOptions.contains(ToolbarOption.CAN_EDIT))
				buttonEdit = createReconfigureButton(
						(AbstractWidget<?>)widgetToShow,
						widgetComponentId, 
						widgetWrapperContainer);
			
			if(toolbarOptions.contains(ToolbarOption.CAN_COPY_WIDGET))	
				buttonSaveChart = createSaveWidgetConfigButton(
						(AbstractWidget<?>)widgetToCopy,
						widgetComponentId, 
						widgetWrapperContainer);
			
			if(toolbarOptions.contains(ToolbarOption.CAN_COPY_QUERY))
				buttonSaveQuery = createSaveQueryButton(
						(AbstractWidget<?>)widgetToCopy, 
						widgetComponentId, 
						widgetWrapperContainer);
			
			if(toolbarOptions.contains(ToolbarOption.CAN_COPY_QUERY))
				buttonEditQuery = createEditQueryButton(
						(AbstractWidget<?>)widgetToCopy, 
						widgetComponentId, 
						widgetWrapperContainer);
				
			if(buttonSaveChart!=null)
				menuContainer.add(buttonSaveChart, "buttonlink floatRight");
			if(buttonSaveQuery!=null)
				menuContainer.add(buttonSaveQuery, "buttonlink floatRight");
			if(buttonEdit!=null)
				menuContainer.add(buttonEdit, "buttonlink floatRight");
			if(buttonEditQuery!=null)
				menuContainer.add(buttonEditQuery, "buttonlink floatRight");

			widgetWrapperContainer.addStyle("clear", "both");
			widgetWrapperContainer.addStyle("padding-top", "5px");
			FComponent widgetComponent = widgetToShow.getComponentUAE(widgetComponentId);
			widgetWrapperContainer.add(widgetComponent);
			
			return embeddingSuperContainer;
		}
		
		private static FPopupWindow getCopyToClipboardPopup(FComponent component, String title, String content) {
			FPopupWindow popup = component.getPage().getPopupWindowInstance();
			popup.removeAll();
			popup.setTop("60px");
			popup.setWidth("40%");
			popup.setLeft("40%");
			popup.setTitle(title);
			popup.add(new FLabel(Rand.getIncrementalFluidUUID(), "Copy to clipboard: Ctrl+C"));
			popup.add(new FHTML(Rand.getIncrementalFluidUUID(), "<br/>"));
			
			FTextArea textArea = new FTextArea(Rand.getIncrementalFluidUUID(), content);
			textArea.rows = 6;
			
			popup.add(textArea);
			popup.addCloseButton("OK");
			
			FClientUpdate update = new FClientUpdate(Prio.VERYEND, "document.getElementById(\""+textArea.getId()+"_comp\").select();");
			popup.addClientUpdate(update);
		
			return popup;
		}
		
		private String getQuery(AbstractWidget<?> widget) {
			
			String query = "";
			
			if(widget == null) {
				if(pc.queryType.equals("SELECT") || pc.queryType.equals("CONSTRUCT"))
					return pc.query;
			} else {
				Operator operator = widget.getMapping();
				
				Operator queryNode = null;
					
				if(operator.isStructure()) {
					queryNode = operator.getStructureItem("query");
				} 
				
				if(queryNode!=null) {
					query = queryNode.serialize();
					query = query.substring(1, query.length()-1);
				}
			
			}
			
			return query;
			
		}
		 
		private static FLinkButton createReconfigureButton(
				 final AbstractWidget<?> widget,
				 final String widgetComponentId,
				 final FContainer embeddingContainer) {		
			 return new FLinkButton("edit-"+widgetComponentId, 
					 UIUtil.getImageHTMLRelative("/ajax/icons/edit.png", "Edit Widget") + " Edit Widget") {
				@Override
				public void onClick() {
					FComponent widgetComponent = embeddingContainer.getComponent(embeddingContainer.getId()+"_"+widgetComponentId);
					ReconfigureWidgetConfigurationForm.showReconfigureWidgetConfigurationForm(getPage().getPopupWindowInstance(), widget, widgetComponent);
				
				}
			};
		 }
		 
		private static FLinkButton createSaveWidgetConfigButton(
				 final AbstractWidget<?> widget,
				 final String widgetComponentId,
				 final FContainer embeddingContainer) {
			return new FLinkButton(
						"saveChart-"+widgetComponentId, 
						UIUtil.getImageHTMLRelative("/images/copy_widget.png", "Copy widget") + " Copy Widget") {
				
				@Override
				public void onClick() {
					try {
						Operator mappingOperator = ((AbstractWidget<?>)widget).getMapping();
						
						String widgetName = EndpointImpl.api().getWidgetService().getWidgetName(widget.getClass().getName());
						String serialized = OperatorFactory.toWidgetWikiSnippet(mappingOperator, widgetName);			
						
						FPopupWindow popup = getCopyToClipboardPopup(this, "Widget configuration", serialized);
		                
		                popup.populateView();
		                popup.show();
						
					} catch(RemoteException e) {
						logger.warn(e.getMessage());
						logger.debug("Details: ", e);
					}
				}
			};
		 }
		 
		 private FLinkButton createSaveQueryButton(
				 final AbstractWidget<?> widget,
				 final String widgetComponentId,
				 final FContainer embeddingContainer) {
			 return new FLinkButton(
						"saveQuery-"+widgetComponentId, 
						UIUtil.getImageHTMLRelative("/images/copy_SPARQL.png", "Copy Query") + " Copy Query") {
					
					@Override
					public void onClick() {
						
						String query = getQuery(widget);
						
						if(StringUtil.isNotNullNorEmpty(query)) {
							FPopupWindow popup = getCopyToClipboardPopup(this, "Query", query);
				                
			                popup.populateView();
			                popup.show();							
						}

					}
				};
		 }
		 
		 private FLinkButton createEditQueryButton(
				 final AbstractWidget<?> widget,
				 final String widgetComponentId,
				 final FContainer embeddingContainer) {
			 return new FLinkButton(
						"editQuery-"+widgetComponentId, 
						 UIUtil.getImageHTMLRelative("/ajax/icons/edit.png", "Edit Query") + " Edit Query") {
					
					@Override
					public void onClick() {
						
						String query = getQuery(widget);
						
						if(StringUtil.isNotNullNorEmpty(query)) {

							// Creating a dummy form which would sent the initial query as POST
							// Could not find a nicer solution: 
							// apparently, jQuery AJAX post cannot imitate a form submission without a form (?)
							// TODO: better way?
							StringBuilder htmlBuilder = new StringBuilder();
							htmlBuilder.append("<form action=\"");
							htmlBuilder.append(EndpointImpl.api().getRequestMapper().getContextPath());
							htmlBuilder.append("/sparql\" method=\"post\">");
							htmlBuilder.append("<input type=\"hidden\" name=\"initQuery\" value=\"");
							String sQuery = StringEscapeUtils.escapeHtml(query);
							htmlBuilder.append(sQuery);
							htmlBuilder.append("\"/></form>");

							StringBuilder jsSubmitBuilder = new StringBuilder();
							jsSubmitBuilder.append("var form=$('");
							jsSubmitBuilder.append(StringEscapeUtils.escapeJavaScript(htmlBuilder.toString()));
							jsSubmitBuilder.append("');");
							jsSubmitBuilder.append("$('body').append(form);");
							jsSubmitBuilder.append("$(form).submit();");
							
							addClientUpdate(new FClientUpdate(jsSubmitBuilder.toString()));
							populateView();
						}

					}
				};
		 }
			
	}
	
}
