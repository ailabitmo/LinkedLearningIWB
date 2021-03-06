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

package com.fluidops.iwb.api.solution;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.List;

/**
 * Service to handle solutions.  
 */
public interface SolutionService extends ExternalSolutionService
{
    /** The directory where solution artifacts are found (relative to the application-install dir) */
    public static final String DEFAULT_APPS_DIR_REL_PATH = "apps";
    
    /**
     * The name of the installation logger
     */
    public static final String INSTALL_LOGGER_NAME = "install";

    /**
     * Install these parts of the solution that allow installation at
     * start-time.
     * 
     * @param solution
     *            a handle to the solution. The specific target depends on the
     *            implementation. This could for example be a zip-file or the
     *            "unzipped" solution-folder.
     * @return an {@link InstallationResult} or null, if the handle
     *         {@code solution} could not be processed at all (e.g. since it was
     *         in an "unknown" format).
     */
    InstallationResult install(File solution);
    
    /**
     * Detects a solution "handle". This is of the same "type" as the handle
     * that is passed to {@link #install(File)}.
     * 
     * @return a "handle" to a solution or <code>null</code>, if none was found.
     */
    File detectSolution();
    
    /**
     * Detect solution "handles" that are available for installation. These are 
     * {@link URI}s, and can represent local {@link File}s or {@link URL}s.
     * 
     * @return an array of {@link URI}s to solutions or <code>null</code>, if none were found.
     */
    List<URI> detectSolutions();

    void addHandler(SolutionHandler<?> handler);
    
    /**
     * Returns the installation status of the given solution. If the solution
     * is not known nor installed, this method returns {@code null}
     * 
     * @param solution
     * @return
     */
    // TODO define exception handling: what happens if solution does not exist
    InstallationResult getSolutionStatus(URI solution);
    
    /**
     * Returns the installed/known solutions, including those that are not yet 
     * installed (see {@link #detectSolution()}.
     * 
     * @return a {@link List} of all known solutions, may be empty. Never returns {@code null}
     */
    List<URI> getSolutions();
    
    /**
     * Returns the installed solutions
     * 
     * @return a {@link List} of all known solutions, may be empty. Never returns {@code null}
     */
    List<URI> getInstalledSolutions();
    
    /**
     * Returns the SolutionInfo about the given solution. 
     * 
     * @param solution
     * @return
     */
    // TODO define exception handling: what happens if solution does not exist
    SolutionInfo getSolutionInfo(URI solution);
}
