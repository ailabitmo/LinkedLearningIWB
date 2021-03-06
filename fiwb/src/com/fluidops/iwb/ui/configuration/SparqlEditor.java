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

package com.fluidops.iwb.ui.configuration;

import java.net.URLEncoder;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.fluidops.ajax.FClientUpdate;
import com.fluidops.ajax.FClientUpdate.Prio;
import com.fluidops.ajax.FEvent;
import com.fluidops.ajax.components.FButton;
import com.fluidops.ajax.components.FContainer;
import com.fluidops.ajax.components.FDialog;
import com.fluidops.ajax.components.FHTML;
import com.fluidops.api.security.SHA512;
import com.fluidops.iwb.api.EndpointImpl;
import com.fluidops.util.Rand;

/**
 * Component for rendering the FLINT SPARQL interface in an iframe.
 * The actual inserted query can be retrieved via a registered
 * {@link SubmitListener}.
 * 
 * @author anna.gossen, as
 *
 */
public class SparqlEditor extends FContainer {

	private static final Logger logger = Logger.getLogger(SparqlEditor.class.getName());
	
	protected static final String SPARQL_IFRAME_ID = "sparql-iframe";
	protected static final String SUBMIT_EVENT_PARAM_NAME = "submit";
	protected static final String PREVIEW_EVENT_PARAM_NAME = "preview";
	
	protected String query = "";
	protected String submitBtnLabel = "Done";
	/**
	 * A (optional) callback which is invoked once a client submits the query
	 */
	protected SubmitListener submitListener;
	
	private String frameId;
	
	public SparqlEditor(String id) {
		this(id, "");
	}

	public SparqlEditor(String id, String query) {
		super(id);
		this.query = query;
		initialize();
	}
	
	private void initialize() {
		
		frameId = "sparql-iframe-" +Rand.getIncrementalFluidUUID();
		FHTML editor = new FHTML("sparql",
				"<iframe id='" + frameId + "' frameborder='0' width='100%' height='450px' src='"
				+ EndpointImpl.api().getRequestMapper().getContextPath() + "/sparqleditor' " 
				+ "onload=\"var iframe = document.getElementById('" + frameId + "');" 
						+ " var flint = iframe.contentWindow.flintEditor;"
						+ " flint.getEditor().getCodeEditor().setValue('"+ StringEscapeUtils.escapeJavaScript(StringEscapeUtils.escapeHtml(query)) + "');"
						+ "\">" +
				"</iframe>");
		add(editor);

		FButton submit = new FButton("update", submitBtnLabel)
		{
			@Override
			public void onClick()
			{
				addClientUpdate(new FClientUpdate(
						Prio.VERYBEGINNING,
						  	getQueryStringJavascript()+" catchPostEventIdEncode('"+SparqlEditor.this.getId()+"',9, query,'"+SUBMIT_EVENT_PARAM_NAME+"');"));
			}
		};
		add(submit, "floatLeft");	
		
		FButton preview = new FButton("preview", "Preview")
		{
			@Override
			public void onClick()
			{				
				addClientUpdate(new FClientUpdate(
						Prio.VERYBEGINNING,
							getQueryStringJavascript()+" catchPostEventIdEncode('"+SparqlEditor.this.getId()+"',9, query,'"+PREVIEW_EVENT_PARAM_NAME+"');"));
			}
		};
		add(preview, "floatLeft");	

	}
	
	/**
	 * @return
	 */
	protected String getQueryStringJavascript() {
		return "var frame = document.getElementById('" + frameId + "');"
				+ " frame.contentWindow.flintEditor.getEditor().getCodeEditor().save(); "
				+ " var query = frame.contentWindow.document.getElementById('flint-code').value;";
	}

	/**
	 * @param submitBtnLabel the submitBtnLabel to set
	 */
	public void setSubmitBtnLabel(String submitBtnLabel) {
		this.submitBtnLabel = submitBtnLabel;
	}

	
	/**
	 * Set a custom {@link SubmitListener} which is invoked once the
	 * user clicks the submit button.
	 * 
	 * @param submitListener the submitListener to set
	 */
	public void setSubmitListener(SubmitListener submitListener) {
		this.submitListener = submitListener;
	}


	@Override
	public void handleClientSideEvent(FEvent event) {
		
		// case1: callback when the user clicked on the "Done" button
		query = event.getPostParameter(SUBMIT_EVENT_PARAM_NAME);
		if(query != null && submitListener!=null)
		{
			submitListener.onSubmit(query);
			return;
		}
		
		// case2: if the 'preview' button is clicked
		query = event.getPostParameter(PREVIEW_EVENT_PARAM_NAME);
		if(query != null ) {
			showPreview();
		}
	}
	
	
	/**
	 * In case the entered query does not contain the '??' variable
	 * the user is redirected to the search results page in a new tab,
	 * otherwise an error message in a popup window is displayed.
	 * 
	 */
	private void showPreview() {
		
		if (query.contains("??")) {
			FDialog.showMessage(getPage(), "Info", "The preview of results for this query is not possible: reference to the current resource encountered ('??').", "Ok");
			return;
		} 
		
		String location = EndpointImpl.api().getRequestMapper().getContextPath()+"/sparql" +
				"?query="+query+"&format=auto&infer=false";

		String tokenBase = com.fluidops.iwb.util.Config.getConfig().getServletSecurityKey() + query;
		String securityToken = null;
		try {
			securityToken = SHA512.encrypt(tokenBase);
			location += "&st=" + URLEncoder.encode(securityToken.toString(), "UTF-8");
			addClientUpdate(new FClientUpdate("window.open('"+ location +"', '_blank');"));

		} catch (Exception e) {
			logger.debug("Error while preparing preview of SPARQL query:" + e.getMessage(), e);
			throw new IllegalStateException("Error while preparing preview of SPARQL query: " + e.getMessage(), e);
		}			
	}


	/**
	 * Interface to provide custom functionality once a user
	 * has submitted a query using the button.
	 * 
	 * @author as
	 */
	public static interface SubmitListener {
		public void onSubmit(String query);
	}
	
}
