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

package com.fluidops.iwb.provider;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.repository.util.RDFLoader;
import org.openrdf.rio.ParserConfig;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParserRegistry;
import org.openrdf.rio.helpers.RDFHandlerBase;

import com.fluidops.iwb.api.ReadDataManagerImpl;
import com.fluidops.iwb.datasource.RDFDataSource;
import com.fluidops.iwb.model.ParameterConfigDoc;
import com.fluidops.iwb.model.ParameterConfigDoc.Type;
import com.fluidops.iwb.model.TypeConfigDoc;
import com.fluidops.iwb.ui.configuration.SelectValuesFactory;
import com.google.common.base.Function;
import com.google.common.collect.Lists;


/**
 * Provider for RDF data. This provider imports data from a given {@link RDFDataSource}
 * using the provider framework.
 * 
 * Note: for legacy reasons we support the configuration using explicit URL and format.
 */
@TypeConfigDoc( "Reads RDF data from an existing RDF source that is accessible via a URL." )
public class RDFProvider extends AbstractFlexProvider<RDFProvider.Config>  {
	
	private static final long serialVersionUID = 7415666290518242634L;

	public static class Config extends AbstractFlexProvider.DataSourceProviderConfig implements Serializable {
		
		private static final long serialVersionUID = -256843331311012640L;

		@ParameterConfigDoc(desc = "URL of the RDF data source", 
				required = true)
		public String url;

		@ParameterConfigDoc(desc = "The RDF format used by the source", 
				type = Type.DROPDOWN, 
				selectValuesFactory = RDFFormatSelectValuesFactory.class)
		public String format;
	}
	
	
	@Override
	public void gather(final List<Statement> res) throws Exception {
		
		InputStream rdfStream = null;
		RDFFormat rdfFormat;
		String baseUri;
		try {
			
			// use either DataSource or legacy initialization
			if (config.dataSource!=null) {
				RDFDataSource ds = config.lookupAndRefreshDataSource(RDFDataSource.class);
				
				rdfFormat = ds.getRDFFormat();			
				if (rdfFormat==null)
					throw new IllegalStateException(String.format("Data source '%s' does not provide a valid RDF Format", ds.getIdentifier()));
				
				rdfStream = ds.getRDFStream();
				baseUri = providerID.stringValue();
			} else {
				
				// legacy support
				URL url = new URL(config.url);

				URLConnection conn = url.openConnection();
				conn.setConnectTimeout(1000);
				String contentType = conn.getContentType();

				rdfFormat = getRDFFormat(url, contentType);
				if (rdfFormat == null)
					throw new IllegalStateException(String.format("Cannot determine RDF format for URL '%s'. Please specify a format manually.", config.url));
				
				rdfStream = conn.getInputStream();
				baseUri = config.url;
			}
			
			// load the RDF data from the rdf stream
			ParserConfig parserConfig = new ParserConfig();
			RDFLoader loader = new RDFLoader(parserConfig, ValueFactoryImpl.getInstance());	
			loader.load(rdfStream, baseUri, rdfFormat, new RDFHandlerBase() {
						@Override
						public void handleStatement(Statement st) throws RDFHandlerException {
							res.add(st);
						}
					});
			
		} finally {
			IOUtils.closeQuietly(rdfStream);
		}
	}

	
	/**
	 * Try to determine the RDF Format using the following strategies:
	 * 
	 * a) explicitly specified in the configuration
	 * b) using {@link RDFFormat#forMIMEType(String)}
	 * c) using {@link RDFFormat#forFileName(String)} on the file name
	 * 
	 * @param url
	 * @param contentType
	 * @return
	 */
	protected RDFFormat getRDFFormat(URL url, String contentType) {
		
		// try to determine RDFFormat
        RDFFormat rdfFormat = null;
        
        // ideally, the format is already specified in the config
        if(config.format!=null)
           rdfFormat = ReadDataManagerImpl.parseRdfFormat(config.format);
        
        // next most reliable is MIME type of content
        if(rdfFormat==null)
            rdfFormat = RDFFormat.forMIMEType(contentType);
        
        //As alternative try file name
        if(rdfFormat==null)
            rdfFormat = RDFFormat.forFileName(url.getFile());
        
        return rdfFormat;
	}

	@Override
	public void setLocation(String location) {
		config.url = location;
	}

	@Override
	public String getLocation() {
		return config.url;
	}

	@Override
	public Class<? extends Config> getConfigClass() {
		return Config.class;
	}

	/**
	 * A {@link SelectValuesFactory} for {@link RDFFormat}s that are registered in
	 * {@link RDFParserRegistry}. The values are returned by their name.
	 * 
	 * @author as
	 * @see RDFFormat
	 * @see RDFParserRegistry
	 */
	public static class RDFFormatSelectValuesFactory implements SelectValuesFactory {
		@Override
		public List<String> getSelectValues() {
			
			return Lists.transform(supportedRDFFormats(), new Function<RDFFormat, String>() {
				@Override
				public String apply(RDFFormat rdfFormat) {
					return rdfFormat.getName();
				}			
			});
		}
		
		/**
		 * Return the supported {@link RDFFormat}s given by the {@link RDFParserRegistry}.
		 * 
		 * @return
		 */
		public static List<RDFFormat> supportedRDFFormats() {
			return Lists.newArrayList(RDFParserRegistry.getInstance().getKeys());
		}
	}
}
