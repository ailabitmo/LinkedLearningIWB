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

package com.fluidops.iwb.api;

import static com.fluidops.iwb.api.ReadWriteDataManagerImpl.execute;
import static com.fluidops.util.StringUtil.replaceNonIriRefCharacter;
import static java.lang.String.format;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.sail.memory.MemoryStore;

import com.fluidops.iwb.api.Context.ContextLabel;
import com.fluidops.iwb.api.Context.ContextType;
import com.fluidops.iwb.api.ReadWriteDataManagerImpl.ReadWriteDataManagerCallback;
import com.fluidops.iwb.api.ReadWriteDataManagerImpl.ReadWriteDataManagerVoidCallback;
import com.fluidops.iwb.model.Vocabulary;
import com.fluidops.iwb.model.Vocabulary.SYSTEM_ONTOLOGY;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class DBBulkServiceImpl implements DBBulkService
{
    private static final ValueFactory valueFactory = ValueFactoryImpl.getInstance();
    private static final Logger logger = Logger.getLogger(DBBulkServiceImpl.class);
    private final Supplier<Repository> repository;
    
    /**
     * Expect exact 8 digits after the last slash of the version IRI
     */
    private static final Pattern VERSION_IRI_PATTERN = Pattern.compile(".*/(\\d{8})$");


    public DBBulkServiceImpl(final Repository repository)
    {
        this(Suppliers.ofInstance(repository));
    }
    
    /**
	 * @param repository
	 *            A {@link Supplier} for the {@link Repository}. This allows for
	 *            lazy initialization, if the {@link DBBulkService} needs to be
	 *            constructed before the {@link Repository} is actually
	 *            available.
	 */
    public DBBulkServiceImpl(Supplier<Repository> repository) {
		this.repository = repository;
	}

    @Override
    public void updateOntology(final File ontologyFile)
    {
    	execute(repository.get(), new ReadWriteDataManagerVoidCallback()
    	{
    		@Override
    		public void doWithDataManager(ReadWriteDataManager dataManager)
    		{
    			
    			/*
    			 * Version INFO is an incremental integer scheme.
    			 * Version IRI is an IRI in the form:
    			 * 
    			 *   [anyURI]/[yyyymmdd]
    			 * 
    			 * The last segment of the IRI is an integer that
    			 * encodes a date. It is transformed into an integer and 
    			 * is compared with other version information.
    			 * 
    			 * (It is unlikely that Version INFO has gone beyond 
    			 * 20.0120.000 iterations. Thus, Version IRI versioning information
    			 * should always outweigh the Version INFO scheme.)
    			 * 
    			 * Please note: The ontology is loaded at least twice into a memory repo.
    			 * This may slow down the system start up. If needed alter accordingly.
    			 */
    			
    			assert dataManager.getRepository() != null;

    			URI ontologyURI = determineOntologyURI(ontologyFile);
    			// Precedence: 1. Version IRI scheme, 2. Version INFO scheme
    			URI [] versionPredicateURIs = new URI[] { Vocabulary.OWL.VERSION_IRI, Vocabulary.OWL.VERSION_INFO };

    			Value fileVersion = versionInFile(ontologyFile, ontologyURI, versionPredicateURIs);
    			Value dbVersion = versionInDb(dataManager, ontologyURI, versionPredicateURIs);

    			if (dbVersion == null) {
    				// No version in DB. Accept everything.
    				logger.debug("No version information present in the database. Storing ontology from file '" + ontologyFile + ".");
    				updateOntology(ontologyFile,dataManager);
    				return;
    			} 

    			if (fileVersion == null) {
    				// DB version present but no file version, ignore file ontology.
    				logger.warn("Ignoring ontology in file '" + ontologyFile + " because no version information is present compared to the corresponding ontology in the database.");
    				return;
    			} 

    			// Extract version information and compare the results
    			int fileVersionNumber = extractIntegerVersionNumber(fileVersion, ontologyURI);
    			int dbVersionNumber = extractIntegerVersionNumber(dbVersion, ontologyURI);
    			
    			if(fileVersionNumber == -1 && dbVersionNumber == -1){
    				// Ignore version information 
    				logger.debug("Found unsupported versioning paradigm. Storing ontology " + ontologyFile + ".");
    				updateOntology(ontologyFile,dataManager);
    				return;
    			}
    			
    			if(fileVersionNumber <= dbVersionNumber){
    				logger.debug("Ignoring ontology '" + ontologyFile + " because its version is not newer than the DB's");
    				return;
    			}
    			
    			logger.debug("Storing ontology " + ontologyFile + " because its version is newer than the DB's");
    			updateOntology(ontologyFile,dataManager);
    		}

    	});
    	// shouldnt we update the keyword index as well?
    }

    @Override
    public void bootstrapDB(final File bootstrapFile)
    {
        logger.info("Trying to load/update file '" + bootstrapFile + "' into DB...");
        final URI sourceURI = valueFactory.createURI("urn:bootstrap-" + bootstrapFile.getName());

        try {
	        execute(repository.get(), new ReadWriteDataManagerCallback<Context>()
	        {
	            @Override
	            public Context callWithDataManager(ReadWriteDataManager dataManager)
	            {
	                Context newContext = dataManager.updateDataForSrc(sourceURI, null, Context.ContextType.SYSTEM,
	                        ContextLabel.RDF_IMPORT, null, bootstrapFile, null);
	                dataManager.calculateVoIDStatistics(newContext.getURI());
	                return newContext;
	            }
	        });
        } catch (RuntimeException e) {
        	// wrap exception in a more helpful text, bug 9577
        	throw new RuntimeException("Error loading RDF file " + bootstrapFile + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public void bootstrapDBAndRemove(File dbFile)
    {
        bootstrapDB(dbFile);
        if (!dbFile.delete()) {
            logger.info(format("Cannot delete '%s' after successful import."
                    + " Remove manually or it will be imported again.", dbFile));
        }
    }
    
    private void updateOntology(File ontologyFile, ReadWriteDataManager dataManager){
        logger.info("Trying to load/update ontology '" + ontologyFile + "' into DB...");
        dataManager.updateDataForSrc(filenameToContextUri(ontologyFile), null, ContextType.SYSTEM,
                ContextLabel.ONTOLOGY_IMPORT, RDFFormat.RDFXML, ontologyFile, null);
    }
    
	private int extractIntegerVersionNumber(Value version, URI ontologyUri) {
		if(Literal.class.isInstance(version)){
			try{
				return Literal.class.cast(version).intValue();
			}catch(NumberFormatException nfe){
				logger.warn("Version of ontology '" + ontologyUri.stringValue() + "' is a literal value but cannot be parsed to Integer: " + version.stringValue());
				logger.debug("Details: " + nfe.getMessage(), nfe);
				// Assumption: Version information of winning ontology version is always positive.
				// Returning -1 results in a loss of the corresponding ontology.
				return -1;
			}
		}
		String uri = version.stringValue();
		Matcher m = VERSION_IRI_PATTERN.matcher(uri);
		if (!m.matches()) {
			logger.warn("The version literal of ontology '" + ontologyUri.stringValue() + "' is neither a number, nor an IRI that ends with a [yyyymmdd] pattern.");
			// Assumption: Version information of winning ontology version is always positive.
			// Returning -1 results in a loss of the corresponding ontology.
			return -1;
		}
		return Integer.parseInt(m.group(1));
	}
    
    private Statement getSingleStatement(File ontologyFile, Resource subject, URI predicate, Value object){
		Repository fileRepo = null;
		RepositoryConnection conn = null;
		RepositoryResult<Statement> results = null;
    	try {
    		fileRepo = newMemoryRepository();
			conn = fileRepo.getConnection();
			conn.add(ontologyFile, null, RDFFormat.RDFXML);
			
			results = conn.getStatements(subject, predicate, object, false);
			return results.hasNext()?results.next():null;

    	}catch(Exception e){
    		logger.error("Retrieving single result from "+ontologyFile+" with subject "+
    					  subject+", predicate "+predicate+" and object "+object+" caused an exception.",e);
    		throw new RuntimeException(e);
    	}finally{
    		closeQuietly(results);
    		ReadWriteDataManagerImpl.closeQuietly(conn);
    		shutdownQuietly(fileRepo);
    	}
    }
    
    private <T extends Value> T getSingleSubject(File ontologyFile, Resource subject, URI predicate, Value object, Class<T> type){
    	Statement singleStatment = getSingleStatement(ontologyFile, subject, predicate, object);
    	if(singleStatment == null) return null;
		return type.cast(singleStatment.getSubject());
    }
    
    private <T extends Value> T getSingleObject(File ontologyFile, Resource subject, URI predicate, Value object, Class<T> type){
    	Statement singleStatment = getSingleStatement(ontologyFile, subject, predicate, object);
    	if(singleStatment == null) return null;
		return type.cast(singleStatment.getObject());
    }
    
    private URI determineOntologyURI(File ontologyFile){
			// Either get ontology URI in file or use file name as URI
    	    URI fileOntologyURI  = getSingleSubject(ontologyFile, null, RDF.TYPE, OWL.ONTOLOGY, URI.class);
    	    return fileOntologyURI == null? filenameToOntologyUri(ontologyFile):fileOntologyURI;
    }
    
    private Value versionInFile(File ontologyFile, URI ontologyURI, URI ... versionPredicateURIs)
    {
    	for (URI versionPredicateURI : versionPredicateURIs) {
         	Value singleResult = getSingleObject(ontologyFile, ontologyURI, versionPredicateURI, null, Value.class);
           	if(singleResult==null) continue;
           	return singleResult;
        }
      
       // None of the version predicates have been found 
       return null;
    }

    private void closeQuietly(RepositoryResult<Statement> statements)
    {
        if(statements != null) {
            try
            {
                statements.close();
            }
            catch (RepositoryException e)
            {
                // ignore
            }
        }
    }

    private Repository newMemoryRepository() throws RepositoryException
    {
        Repository tmpRepository = new SailRepository(new MemoryStore());
        tmpRepository.initialize();
        return tmpRepository;
    }

    private void shutdownQuietly(Repository tmp)
    {
        try
        {
            if(tmp!= null) tmp.shutDown();
        }
        catch (RepositoryException e)
        {
        }
    }

    private Value versionInDb(ReadWriteDataManager dataManager, URI ontologyUri, URI ... versionPredicateURIs)
    {
    	for (URI versionPredicateURI : versionPredicateURIs) {
    		Statement versionStmt = dataManager.searchOne(ontologyUri, versionPredicateURI, null);
    		if (versionStmt == null) continue;
    		return versionStmt.getObject();
    	}
    	
    	// None of the version predicates have been found 
    	return null;
    }

    private URI filenameToOntologyUri(File ontologyFile)
    {
        return valueFactory.createURI(replaceNonIriRefCharacter(SYSTEM_ONTOLOGY.ONTOLOGY_NAME_PREFIX + ontologyFile.getName(), '_'));
    }

    private URI filenameToContextUri(File ontologyFile)
    {
        return valueFactory.createURI(replaceNonIriRefCharacter(SYSTEM_ONTOLOGY.ONTOLOGY_CONTEXT_PREFIX + ontologyFile.getName(), '_'));
    }

    @Override
    public void bootstrapDBAllFrom(File dir)
    {
        bootstrapDBAllFrom(dir, false);
    }

    @Override
    public void bootstrapDBAllFromAndRemove(File dir)
    {
        bootstrapDBAllFrom(dir, true);
    }
    
    private void bootstrapDBAllFrom(File dir, boolean removeAfterImport)
    {
        if(!dir.exists()) return;
        File[] filesToBootstrap = dir.listFiles();
        if(filesToBootstrap == null) throw new IllegalStateException(dir + " is not a readable directory"); 
        for (File dbFile : filesToBootstrap)
        {
            if(dbFile.isFile()) {
                if(removeAfterImport) bootstrapDBAndRemove(dbFile); else bootstrapDB(dbFile);
            }
        }
    }
}

