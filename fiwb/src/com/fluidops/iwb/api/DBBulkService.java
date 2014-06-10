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

import java.io.File;

import org.openrdf.repository.RepositoryConnection;


/**
 * Bulk operations on the RDF database.
 */
public interface DBBulkService
{
	/**
	 * Updates an ontology in the database if the given file ontology in RDF/XML
	 * is a newer version or not present yet. 
	 * <p>
	 * The context of the imported ontology always is:
	 * <ul>
	 *  <li>{@code "http://www.fluidops.com/ontologyContext/" + ontologyFile.getName()}
	 * </ul>
	 * <p>
	 * The given file is queried for two properties:<code>owl:versionInfo</code>
	 * and <code>owl:versionIRI</code>. <code>owl:versionInfo</code> should refer to 
	 * a literal which is interpreted as <b>positive integer</b> (including 0) whereas 
	 * <code>owl:versionIRI</code> should refer to a URI which matches the following 
	 * pattern: <code>[anyURI]/[yyyymmdd]</code>. The trailing 8 characters are interpreted 
	 * as date encoded as digits (yyyy=year, mm=month, dd=day). Version <code>A</code> 
	 * of ontology <code>O</code> is newer than Version <code>B</code> if and only if,
	 * the version literal or version date (as Integer) of <code>A</code> are greater 
	 * than the version literal or the version date (as Integer) of <code>B</code>. If 
	 * an ontology contains both a version literal and a version date, then the version 
	 * date is preferred.
	 * <p>
	 * A given file version <code>A</code> of ontology <code>O</code> is imported if
	 * <ul>
	 *  <li> <code>O</code> is not present in the database or
	 *  <li> database version <code>B</code> of <code>O</code> has no version information or
	 *  <li> <code>A</code> is newer than database version <code>B</code> or
	 *  <li> version Literal and IRI of <code>A</code> and of database version <code>B</code>
	 *       cannot be interpreted as Integer. 
	 * </ul>
	 * A given file version <code>A</code> of ontology <code>O</code> is not imported if
	 * <code>A</code> has no version information and database version <code>B</code> of 
	 * <code>O</code> has version information. 
	 * 
	 * @see <a href="http://www.w3.org/TR/owl-ref/#VersionInformation">OWL1 Version Information</a>
	 * @see <a href="http://www.w3.org/TR/owl2-syntax/#Ontology_IRI_and_Version_IRI">OWL2 Version IRI</a>
	 * @param ontologyFile An ontology as file in RDF/XML format.
	 * @throws RuntimeException if malformed RDF/XML is provided
     */
    void updateOntology(File ontologyFile);
    
    /**
     * Load the given file into the database. The data is loaded into the context:
     * {@code "urn:bootstrap-" + dbFile.getName()}. If this context already exists it is deleted.
     * 
     * @param dbFile A file in any format supported by Sesame.
     * @see RepositoryConnection#add(File, String, org.openrdf.rio.RDFFormat, org.openrdf.model.Resource...)
     */
    void bootstrapDB(File dbFile);
    
    /**
     * This basically calls {@link #bootstrapDB(File)} and deleted the file after a successful import.
     * @param dbFile gets deleted after successful import.
     */
    void bootstrapDBAndRemove(File dbFile);
    
    /**
     * Calls {@link #bootstrapDB(File)} for each file in {@code dir}
     * (non-recursively)
     * 
     * @param dir
     *            directory from which files are loaded.
     */
    void bootstrapDBAllFrom(File dir);
    
    /**
     * Calls {@link #bootstrapDBAndRemove(File)} for each file in {@code dir}
     * (non-recursively)
     * 
     * @param dir
     *            directory from which files are loaded.
     */
    void bootstrapDBAllFromAndRemove(File dir);
}
