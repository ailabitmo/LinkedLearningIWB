<%@page import="java.io.*"%>
<%@page import="org.apache.log4j.Logger"%>
<html>
	<%
		/*
			jetty is configured to show this page in case any kind of unhandled exception occurs
		*/
	%>
	<head>
    	<!-- The stylesheet containing general layout for all pages -->
		<link href="/stylesheet_fvmstorage.css" rel="stylesheet" type="text/css">
    </head>
	<body>
		<div id="doc_head2">
			<div id="product_logo_grey"></div>
		</div>
		<div class="content" style="font-size:1.5em; padding:25px;">
			<h2>Processing this request could not complete</h2>
			<p>
				An error occurred while processing the information required for this request.<br>
				This can happen if a resource is currently unavailable. Please try again in a few seconds.<br/>
			</p>
			<p>
				If the problem persists, please contact your administrator.
			</p>

		<div id="techinfo" style="display:none;">
<%
	Throwable th = (Throwable)request.getAttribute("javax.servlet.error.exception");
	Logger.getLogger(this.getClass()).error(th.getMessage(), th);
%>
		</div>
		</div>

		<div id="fluidops_slogan"></div>
		<div id="fluidops_logo_large"></div>
	</body>
</html>
