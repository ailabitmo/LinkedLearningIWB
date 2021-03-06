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
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.ParseException;
import org.openrdf.OpenRDFException;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryResult;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.impl.GraphQueryResultImpl;
import org.openrdf.query.impl.TupleQueryResultImpl;

import com.fluidops.ajax.FSession;
import com.fluidops.ajax.components.FHTML;
import com.fluidops.ajax.components.FPage;
import com.fluidops.ajax.components.FPopupWindow;
import com.fluidops.iwb.api.EndpointImpl;
import com.fluidops.iwb.api.ReadDataManagerImpl;
import com.fluidops.iwb.api.ReadDataManagerImpl.SparqlQueryType;
import com.fluidops.iwb.api.query.AdHocSearchResultsWidgetSelectorImpl;
import com.fluidops.iwb.keywordsearch.KeywordSearchProvider;
import com.fluidops.iwb.keywordsearch.SearchProvider;
import com.fluidops.iwb.keywordsearch.SearchProviderFactory;
import com.fluidops.iwb.keywordsearch.SparqlSearchProvider;
import com.fluidops.iwb.layout.AdHocSearchTabWidgetContainer;
import com.fluidops.iwb.model.MultiPartMutableTupleQueryResultImpl;
import com.fluidops.iwb.model.MutableTupleQueryResultImpl;
import com.fluidops.iwb.model.Vocabulary;
import com.fluidops.iwb.page.PageContext;
import com.fluidops.iwb.page.SearchPageContext;
import com.fluidops.iwb.server.RedirectService.RedirectType;
import com.fluidops.iwb.widget.AbstractWidget;
import com.fluidops.iwb.widget.SearchResultWidget;
import com.fluidops.iwb.widget.Widget;
import com.fluidops.security.XssSafeHttpRequest;
import com.fluidops.util.Rand;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Hybrid search servlet combining structured and unstructured queries.
 * 
 * @author Andreas Schwarte, christian.huetter, andriy.nikolov
 */
public class HybridSearchServlet extends IWBHttpServlet
{

	public static class BooleanQueryResult implements QueryResult<Boolean>
	{
		private boolean res;
		private boolean seen;

		public BooleanQueryResult(boolean res) {
			this.res = res;
			seen = false;
		}

		@Override
		public void close() {
			// nothing to do
		}

		@Override
		public boolean hasNext() {
			return !seen;
		}

		@Override
		public Boolean next() {
			if (seen) throw new NoSuchElementException();
			seen = true;
			return res;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	private static class ErrorRecord {
		public int errorCode;
		public String message;
		public String queryTarget;
		
		public ErrorRecord(int errorCode, String message, String queryTarget) {
			this.errorCode = errorCode;
			this.message = message;
			this.queryTarget = queryTarget;
		}
	}

	private static final long serialVersionUID = -1145307972797973995L;
	private static final Logger log = Logger.getLogger(HybridSearchServlet.class);


	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}
	    
	@Override
	protected SearchPageContext createPageContext(HttpServletRequest req,
			HttpServletResponse resp) {
        // the main object passed between processing steps
		PageContext superPc=super.createPageContext(req, resp);
        SearchPageContext tempPc= new SearchPageContext();
		tempPc.title = getPageTitle();
		tempPc.setRequest(superPc.getRequest());
		tempPc.httpResponse = superPc.httpResponse;
		tempPc.repository=superPc.repository;
				
        // page context
		tempPc.contextPath = req.getContextPath();
		// Generic resource associated with the search result page.
		// To be changed to a meaningful unique identifier of the query request.
		tempPc.value = Vocabulary.SYSTEM.SEARCH_VALUE_CONTEXT;
		return tempPc;
		
	}

	@Override
	protected SearchPageContext getPageContext() {
		return (SearchPageContext)super.getPageContext();
	}

	/**
	 * Retrieve the query from request, do the token based security check and
	 * evaluate the query.
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException, ServletException 
	 */
	protected void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException
	{
		// register search page
		String uri = null;
		if (req.getDispatcherType() == DispatcherType.FORWARD)
			uri = (String) req.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
		else
			uri = req.getRequestURI();
		
		// Done to avoid processing of a request from TimelineWidget.
		if(uri.endsWith("__history__.html")) {
			return;
		}
		
		FSession.registerPage(uri, FPage.class);

		FSession.removePage(req);
		
		// get session and page
		FSession session = FSession.getSession(req);
		FPage page;
		try {
			page = (FPage)session.getComponentById(uri, req);
		} catch (Exception e) {
			log.warn("Could not get the page for request: " + uri, e);
	        resp.sendRedirect(RedirectService.getRedirectURL(RedirectType.PAGE_NOT_FOUND, req));
			return;
		}

		SearchPageContext pc = getPageContext();

		// register FPage
		pc.page = page;
		pc.session = session;

		// get and decode query
		pc.query = getQueryFromRequest(req);
		if (pc.query == null) { 
			// use empty query if no query is given
			pc.query = "";
		}
        
		pc.query = pc.query.trim();
		PageContext.setThreadPageContext(pc);
		
		List<String> queryTargets = SearchProviderFactory.getDefaultQueryTargets(); 
		
		String[] vals;
		if((vals = req.getParameterValues("queryTarget"))!=null && (vals.length > 0)) {
			queryTargets = Lists.newArrayList(vals);
		}
		
		SparqlQueryType qt = null;
		
		// Currently, the special query language protocol can be passed in two ways:
		// - as a prefix to the query: e.g., "sql:".
		// - as a request parameter "queryLanguage".
		// If no query language is given explicitly, then the default routine is performed:
		// First, we check that the query is a valid SPARQL query and then, if it is not the case, it is interpreted as a 
		// keyword query.
		pc.queryLanguage = determineQueryLanguage(pc.query, req);
		
		// If the query language was provided as a prefix, we no longer need the prefix
		if(!pc.queryLanguage.equals("DEFAULT") && pc.query.toUpperCase().startsWith(pc.queryLanguage.toUpperCase()+":")) {
			pc.query = pc.query.substring(pc.queryLanguage.length()+1).trim();
		}
		
		if(pc.queryLanguage.equals("SPARQL") || pc.queryLanguage.equals("DEFAULT")) {
			try
			{
				qt = ReadDataManagerImpl.getSparqlQueryType(pc.query, true);
				// We managed to parse it as SPARQL, so it's SPARQL anyway.
				pc.queryLanguage = "SPARQL";
				// since ASK queries are used in bigowlim for several control functionalities
				// we do not want to support them right now. Same for UPDATE
				if (qt == SparqlQueryType.ASK || qt == SparqlQueryType.UPDATE)	{
		            error(resp, 403, "Not allowed to execute ASK or UPDATE queries.");
		            return;
				}
			}
			catch (MalformedQueryException e)
			{
				// if the SPARQL prefix was provided explicitly: throw an error 
				if(pc.queryLanguage.equals("SPARQL")) {
					error(resp, 400, "Malformed query:\n\n" + pc.query + "\n\n" + e.getMessage());
					return;
				}
				// ignore: not a valid SPARQL query, treat it as keyword query
			}
		}
		
        //////////////////SECURITY CHECK/////////////////////////
		// remarks:
		// queries are checked, keywords and invalid queries are always allowed
		String securityToken = req.getParameter("st");
		if (!EndpointImpl.api().getUserManager().hasQueryPrivileges(pc.query, qt, securityToken)) {
			error(resp, 403, "Not enough rights to execute query.");
            return;
        }		
		
		// value to be used in queries instead of ??
        String _resolveValue = req.getParameter("value");
        Value resolveValue = _resolveValue!=null ? ValueFactoryImpl.getInstance().createURI(_resolveValue) : null;
		
        boolean infer = false;		// default value for inferencing is false
    	
    	if (req.getParameter("infer")!=null)
    		infer = Boolean.parseBoolean(req.getParameter("infer"));
    	
    	pc.infer = infer;
    	
    	List<ErrorRecord> errorRecords = Lists.newArrayListWithCapacity(queryTargets.size());
    	
		// empty query --> empty result
		if (pc.query.isEmpty())
		{
			pc.queryLanguage = "KEYWORD";
			pc.queryType = "KEYWORD";
				
				
			try {
				pc.queryResult = new MutableTupleQueryResultImpl(
					new TupleQueryResultImpl(
							Collections.<String> emptyList(), 
							Collections.<BindingSet> emptyList().iterator()));
			} catch(QueryEvaluationException e) {
				log.warn("Could not produce a mutable tuple query result");
				log.debug("Details: ", e);
			}
		}
			// allowed SPARQL queries
		else if (pc.queryLanguage.equals("SPARQL"))
		{
			pc.queryType = qt.toString();
			
			QueryResult<?> queryRes = null;
			
			List<SparqlSearchProvider> sparqlProviders = 
					SearchProviderFactory.getInstance().getSparqlSearchProviders(queryTargets);
				
			for(SparqlSearchProvider sparqlProvider : sparqlProviders) {

				try {
					QueryResult<?> currentQueryRes = sparqlProvider.search(pc.query, qt, resolveValue, infer);
					queryRes = ReadDataManagerImpl.mergeQueryResults(queryRes, currentQueryRes);
				} catch(MalformedQueryException e) {
					// If a SPARQL query is malformed, no need to send it to all search providers
					error(resp, 400, e.getMessage());
					return;
				} catch(Exception e) {
					errorRecords.add(createErrorRecord(e, sparqlProvider, pc));
				}
					
			}
				
			if(queryRes == null) {
				if(qt == SparqlQueryType.CONSTRUCT) { 
					queryRes = new GraphQueryResultImpl(
									EndpointImpl.api().getNamespaceService().getRegisteredNamespacePrefixes(), 
									Collections.<Statement>emptyList()); 
				} else {
					queryRes = new MutableTupleQueryResultImpl(
									Lists.newArrayList("Results"), 
									Collections.<BindingSet>emptyList());
				}
			}
			pc.queryResult = queryRes;
				
		}
		// query with a pre-defined custom protocol
		else if(!pc.queryLanguage.equals("DEFAULT")) 
		{
			pc.queryType = "KEYWORD";
			
			QueryResult<?> queryRes = null;
			
			List<SearchProvider> providers = SearchProviderFactory
					.getInstance()
					.getSearchProvidersSupportingQueryLanguage(queryTargets, pc.queryLanguage);
			
			QueryResult<?> currentQueryResult;
			
			for(SearchProvider provider : providers) {
				// If the query protocol was provided, we assume that the target knows how to deal with it.
				try {
					currentQueryResult = provider.search(pc.queryLanguage, pc.query);
					queryRes = ReadDataManagerImpl.mergeQueryResults(queryRes, currentQueryResult);
				} catch(Exception e) {
					errorRecords.add(createErrorRecord(e, provider, pc));
				}
			}
				
			pc.queryResult = (queryRes!=null) ? queryRes : createEmptyKeywordQueryResult();
				
		}
		// keyword query
		else
		{
			
			try {
				handleKeywordQuery(pc, queryTargets);
				
			} catch (ParseException e) {
				error(resp, 400, "Malformed keyword query:\n\n" + pc.query + "\n\n" + e.getMessage());
			} catch (SearchException e) {
				errorRecords.add(createErrorRecord(e, pc));
			}
		}
			
		resp.setStatus(HttpServletResponse.SC_OK);
			
        // calculate facets
		// legacy code faceted search, currently not active
//			String facetsAsString = "";
//			if (Config.getConfig().getFacetedSearch().equals("standard")) 
//			{
//				FacetCalculator facetter = new FacetCalculator( pc );
//				FContainer facetContainer = facetter.getFacetContainer();
//				page.register(facetContainer);
//				facetsAsString = facetContainer.htmlAnchor().toString();
//				facetContainer.drawAdvHeader(true);
//				facetContainer.drawHeader(false);
//			}

		// page title
		pc.title = (pc.queryLanguage.equals("SPARQL") && !pc.queryType.equals("KEYWORD") || pc.query.isEmpty()) 
				? "Search result"
				: "Search result: " + StringEscapeUtils.escapeHtml(pc.query);

        // TODO: activeLabel

		// select widgets to display search results
		selectWidgets(pc, infer);
        
        // layout result page
        populateContainer(pc, errorRecords);
        
        // print response
        EndpointImpl.api().getPrinter().print(pc, resp);
    }
	
	
	/**
	 * Helper method that processes KEYWORD queries using the 
	 * {@link KeywordSearchProvider}s which are available for
	 * the given query targets.
	 */
	void handleKeywordQuery(SearchPageContext pc, List<String> queryTargets) throws ParseException, SearchException {
		pc.queryLanguage = "KEYWORD";
		pc.queryType = "KEYWORD";
		
		MultiPartMutableTupleQueryResultImpl queryRes = null;
		
		TupleQueryResult currentQueryResult;
			
		List<KeywordSearchProvider> providers = SearchProviderFactory
				.getInstance()
				.getKeywordSearchProviders(queryTargets);

		for(KeywordSearchProvider provider : providers) {
			try {
				currentQueryResult = provider.search(pc.query);
				queryRes = ReadDataManagerImpl.mergeQueryResults(queryRes, currentQueryResult, provider.getShortName());
			} catch (ParseException e) { 
				throw e;
			} catch (Exception e) {
				throw new SearchException(e, provider);
			}
		}
			
		pc.queryResult = (queryRes!=null) ? queryRes : createEmptyKeywordQueryResult();
	}
	
	
	private static ErrorRecord createErrorRecord(SearchException e, SearchPageContext pc) {
		// special exception to transport information
		return createErrorRecord( (Exception) e.getCause(), e.searchProvider, pc);
	}
	
	private static ErrorRecord createErrorRecord (Exception e, SearchProvider provider, SearchPageContext pc) {
		int errorCode = 500;
		String errorMessage;
		if (e instanceof IllegalArgumentException) {
			errorMessage = "Search provider returned illegal output: " + e.getMessage();
		} else if(e instanceof MalformedQueryException) {
			errorCode = 400;
			errorMessage = "Malformed query:\n\n" + pc.query + "\n\n" + e.getMessage();
		} else if(e instanceof ParseException) {
			errorCode = 400;
			errorMessage = "Malformed keyword query:\n\n" + pc.query + "\n\n" + e.getMessage();
		} else if(e instanceof QueryEvaluationException) {
			errorMessage = "Error occured while processing the query:\n\n" + pc.query + "\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
		} else if(e instanceof OpenRDFException) {
			// e.g. if client connection was closed, must not be an error
			errorMessage = "Error occured while processing the query:\n\n" + pc.query + "\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
		} else {
			errorMessage = "Unexpected error occured while processing the query:\n\n" + pc.query + "\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
		}
		log.info(errorMessage);
		log.debug("Details: ", e);
		return new ErrorRecord(errorCode, errorMessage, provider.getShortName());
	}
	
	private static MutableTupleQueryResultImpl createEmptyKeywordQueryResult() {
		
		return new MutableTupleQueryResultImpl(
				Lists.newArrayList("Subject", "Property", "Value", "Type"), 
				Collections.<BindingSet>emptyList());
		
	}

	/**
	 * Returns the query string from the requests, parameter "query" or "q"
	 * 
	 * @param req
	 * @return
	 * 			the query string or null if no query is specified
	 */
	protected String getQueryFromRequest(HttpServletRequest req)
	{
		// for this servlet we do not use XSS Filter, any output must be controlled.
		if (req instanceof XssSafeHttpRequest) {
			req = ((XssSafeHttpRequest)req).getXssUnsafeHttpRequest();
		}
		
		String query = req.getParameter("q");
		if (query==null) {
			return req.getParameter("query");
		}
		return query;
	}

	/**
	 * Perform widget selection
	 * 
	 * @see com.fluidops.iwb.api.WidgetSelectorImpl.selectWidgets(PageContext)
	 * @param pc
	 */
	private void selectWidgets(SearchPageContext pc, boolean infer)
	{
		pc.widgets = Sets.newLinkedHashSet();
		
		// always add result table
		pc.widgets.add(new SearchResultWidget());
		
		AdHocSearchResultsWidgetSelectorImpl impl = new AdHocSearchResultsWidgetSelectorImpl();
		
		try {
			impl.selectWidgets(pc);
		} catch(Exception e) {
			log.warn(e.getMessage());
			log.debug(e);
		}
		
	}
	
	/**
	 * Populate widget container
	 * 
	 * @see com.fluidops.iwb.api.LayouterImpl.populateContainer(PageContext)
	 * @param pc
	 */
	private void populateContainer(PageContext pc, List<ErrorRecord> errorRecords)
	{
		pc.container = new AdHocSearchTabWidgetContainer();
		pc.page.register(pc.container.getContainer());
		
		if(errorRecords!=null && !errorRecords.isEmpty()) {
			initializeErrorPopup(pc, errorRecords);
		}
		// add widgets
 		for (Widget<?> w : pc.widgets)
 		{
 			if (!(w instanceof AbstractWidget<?>))
 				throw new RuntimeException("Widgets does not extend AbstractWidget<?>: " + w);
 			
 		    w.setPageContext(pc);
			pc.container.add( (AbstractWidget<?>) w, "search" + Rand.getIncrementalFluidUUID());
 		}
 		
 		pc.container.postRegistration(pc);
 	}
	
	private static void initializeErrorPopup(PageContext pc, List<ErrorRecord> errorRecords) {
		FPopupWindow popup = pc.page.getPopupWindowInstance();
		popup.removeAll();
		
		popup.setTitle("Search error");
		
		StringBuilder errorTableBuilder = new StringBuilder();
		errorTableBuilder.append("The following errors occurred while searching: ");
		errorTableBuilder.append("<table>");
		for(ErrorRecord errorRecord : errorRecords) {
			errorTableBuilder.append("<tr><td>");
			errorTableBuilder.append("<img src='");
			errorTableBuilder.append(pc.contextPath);
			errorTableBuilder.append("/images/error.png'/>");
			errorTableBuilder.append("</td><td>");
			errorTableBuilder.append("Could not process the query on " +errorRecord.queryTarget + ". Error " + errorRecord.errorCode + ", cause: " + errorRecord.message);
			errorTableBuilder.append("</td></tr>");
		}
		errorTableBuilder.append("</table>");
		popup.add(new FHTML(Rand.getIncrementalFluidUUID(), errorTableBuilder.toString()));
		popup.addCloseButton("OK");
		
		popup.show();
		
	}
		

	/**
	 * Send the specified error message to the client (if the connection is
	 * still open).
	 * 
	 * @param resp
	 * @param errorCode
	 * @param message
	 */
	protected static void error(HttpServletResponse resp, int errorCode, String message) {
		
		try {
			log.info("Error (" + errorCode + "): " + message);
			if (!resp.isCommitted())
				resp.sendError(errorCode, message);
		} catch (IllegalStateException e) {
			// should never occur
			log.warn("Error message could not be send. Stream is committed: " + message);
		} catch (IOException e) {
			log.error("Error message could not be sent", e);
		}
	}
	
	private static String determineQueryLanguage(String query, HttpServletRequest req) {
		
		String queryLanguage = req.getParameter("queryLanguage");
		
		if(queryLanguage!=null && SearchProviderFactory.getInstance().isValidQueryLanguage(queryLanguage))
				return queryLanguage.toUpperCase();
		
		queryLanguage = "DEFAULT";
		
		int index;
		if((index=query.indexOf(':'))!=-1) {
			String tmp = query.substring(0, index);
			if(SearchProviderFactory.getInstance().getValidQueryLanguages().contains(tmp.toUpperCase()))
				queryLanguage = tmp.toUpperCase();
		}
		
		return queryLanguage;
	}
	
	
	@Override
	protected String getPageTitle() {
		return "Hybrid Search Servlet";
	}  
	
    
	/**
	 * A special exception which contains additional information
	 * about the {@link SearchProvider}. This exception allows
	 * to create {@link ErrorRecord}s using 
	 * {@link HybridSearchServlet#createErrorRecord(SearchException, SearchPageContext)
	 */
	static class SearchException extends Exception {

		private static final long serialVersionUID = -144313072352868756L;
		public final SearchProvider searchProvider;
		
		public SearchException(Exception cause, SearchProvider provider) {
			super(cause);
			this.searchProvider = provider;
		}
		
	}
}
