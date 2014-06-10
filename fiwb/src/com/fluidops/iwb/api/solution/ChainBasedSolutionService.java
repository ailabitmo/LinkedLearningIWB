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
import java.io.FileFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fluidops.iwb.api.solution.InstallationResult.InstallationStatus;
import com.fluidops.iwb.util.IWBFileUtil;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A {@link SolutionService} that basically tries a couple of injected
 * {@link SolutionService}s until one is successful installing/detecting ((
 * {@link #install(File)}/{@link #detectSolution()} does not return
 * <code>null</code>) a solution.
 * 
 * @see DirReferenceBasedSolutionService
 * @see ZipFileBasedSolutionService
 */
public class ChainBasedSolutionService implements SolutionService
{
	/**
	 * Logger
	 */
	private static final Logger logger = Logger.getLogger(ChainBasedSolutionService.class.getName());
			
    private final SolutionService[] servicesChain;
    
    /**
     * Map that stores the installed(known) solutions with installation result
     */
	protected Map<URI,InstallationResult> solutionState = Maps.newConcurrentMap();
	
	// implementation note: the current assumption is that 
    public ChainBasedSolutionService(SolutionService... servicesChain)
    {
        this.servicesChain = servicesChain;
        detectInstalledSolutions();
    }

    /**
     * Detects solutions installed in previous service runs.
     * Installations done in current process are registered directly.
     */
    void detectInstalledSolutions()
    {
    	File appsDir = new File(IWBFileUtil.getApplicationFolder(), SolutionService.DEFAULT_APPS_DIR_REL_PATH);
    	
    	if (!appsDir.isDirectory())
    		return;
    	
    	File[] installed = appsDir.listFiles( new FileFilter() {

			@Override
			public boolean accept(File pathname)
			{
				// Naming pattern is important: solution.zip gets renamed to solution.zip.20YYMMDD-HHMMSS
				if ( pathname.isFile() && pathname.getName().contains(".zip.20") )
					return true;

				logger.debug("Ignored file as non-solution resource: "+pathname);
				return false;
			}} );
    	
    	for ( File f : installed )
    	{    	
    		File originalName = f;
    		logger.info("Detected installed solution app: "+originalName);
    		solutionState.put( originalName.toURI(), SimpleInstallationResult.success(InstallationStatus.INSTALLED_SUCCESSFULLY) );
    	}
    	
    }

    @Override
    public InstallationResult install(File solution)
    {
        for (SolutionService service : servicesChain)
        {
            InstallationResult installationResult = service.install(solution);
            if(installationResult != null) return installationResult;
        }
        return null;
    }

    @Override
    public File detectSolution()
    {
        for (SolutionService service : servicesChain)
        {
            File detected = service.detectSolution();
            if(detected!= null) return detected;
        }
        return null;
    }
    
   	@Override
	public List<URI> detectSolutions() {
		List<URI> uris = Lists.newArrayList();
		for (SolutionService service : servicesChain)
        {
            List<URI> u = service.detectSolutions();
            if (u != null) {
            	uris.addAll(u);
            }
        }
		return uris;
	}


    @Override
    public void addHandler(SolutionHandler<?> handler) {
        for (SolutionService service : servicesChain) {
            service.addHandler(handler);
        }
    }

    @Override
	public InstallationResult install(URL solutionArtifact)
			throws RemoteException
	{
        for (SolutionService service : servicesChain)
        {
            InstallationResult installationResult = service.install(solutionArtifact);
            if (installationResult != null)
            {
            	try {
					solutionState.put( solutionArtifact.toURI(), installationResult );
				} catch (URISyntaxException e) {
					throw Throwables.propagate(e);
				}
            	return installationResult;
            }
        }
        return null;
	}

	@Override
	public InstallationResult getSolutionStatus(URI solution)
	{
		return solutionState.get( solution );
	}
	
	@Override
	public List<URI> getSolutions()
	{
		// TODO implement in handlers
		Set<URI> res = Sets.newHashSet();
		res.addAll(solutionState.keySet());
		res.addAll(detectSolutions());
		return Lists.newArrayList(res);
	}
	
	@Override
	public List<URI> getInstalledSolutions()
	{
		// TODO implement in handlers
		Set<URI> res = Sets.newHashSet();
		res.addAll(solutionState.keySet());
		return Lists.newArrayList(res);
	}
	
	@Override
	public SolutionInfo getSolutionInfo( URI solution )
	{
		File f;
		// Currently only works for local files
		
		// TODO check file scheme
		f = new File( solution );

		
		// NOTE: the following code obviously highly relies on the order of services registered in the service chain !!!
		// This is why we cast to the expected class - detect change in implementation code.
		//
		// The proper way would be to call the chain members, but unforunately readSolution(x) always returns a result,
		// so it would be difficult to judge which service returns the "better" (correct) one.
		
		if ( f.getName().endsWith( DirReferenceBasedSolutionService.DEFAULT_DIRECTORY_REFERENCE_EXTENSION /* ".ref" */ ) )
			return ((DirReferenceBasedSolutionService)servicesChain[1]).readSolutionInfo(f);
		else
			return ((ZipFileBasedSolutionService)servicesChain[0]).readSolutionInfo(f);
	}
}
