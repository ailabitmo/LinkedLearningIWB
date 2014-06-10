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

package com.fluidops.iwb.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.fluidops.iwb.Global;
import com.fluidops.iwb.Version;
import com.fluidops.iwb.api.EndpointImpl;
import com.fluidops.iwb.page.PageContext;
import com.fluidops.iwb.repository.PlatformRepositoryManager;
import com.fluidops.util.StringUtil;


/**
 * Base class for any HTTP Servlet in the Information Workbench. 
 * Performs security check if user is allowed to access the servlet
 * instance.
 * 
 * @author as
 *
 */
public abstract class IWBHttpServlet extends HttpServlet {

	private static final long serialVersionUID = 1024043245413129633L;
	
	protected static final Logger log = Logger.getLogger(IWBHttpServlet.class);
	
	private PageContext pc;
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		// general security check: is user allowed to access the servlet?
        if (!EndpointImpl.api().getUserManager().hasServletAccess(getClass(), null))   {
            try {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            } catch (Exception e) {
                log.warn(e.getMessage(),e);
            }
            return;
        }
        try {
        	pc= createPageContext(req, resp);
        	super.service(req, resp);
        } finally {
        	// in any case clear the PageContext stored in the thread
        	PageContext.setThreadPageContext(null);
        }
	}
	
	/**
	 * Creates a default page context from the request/response for all extensions of this servlet.
	 * This method can be overwritten, if a servlet needs a special page context (see for example {@link HybridSearchServlet}).
	 * @param req
	 * @param resp
	 * @return
	 */
	protected PageContext createPageContext(HttpServletRequest req, HttpServletResponse resp) {
		PageContext temPC = new PageContext();
		
		temPC.contextPath = EndpointImpl.api().getRequestMapper().getContextPath();
		temPC.title = getPageTitle();
		temPC.setRequest(req);
		temPC.httpResponse = resp;
		
		PageContext.setThreadPageContext(temPC);
        
    	// historical Repository
		String _historic = req.getParameter("historic");
		boolean historic = _historic!=null && Boolean.parseBoolean(_historic);
		
		//select repository
		String repositoryID =  req.getParameter("repository");
		if(!StringUtil.isNullOrEmpty(repositoryID)){
			temPC.repository = PlatformRepositoryManager.getInstance().getRepository(repositoryID);
        }
		else{
			temPC.repository = historic ? Global.historyRepository: Global.repository;
		}
		return temPC;
	}
	
	protected PageContext getPageContext() {
		return pc;
	}
	
	
	/**
	 * Provides a title for the servlet, which is also used as a page title if no other labels are provided.
	 * Subclasses may (or may not) overwrite this method in order to set a more specific title.
	 * @return the title of the page, which will also be displayed in the UI
	 */
	protected String getPageTitle(){
		return Version.getVersionInfo().getProductName()+" Servlet";
	}
}
