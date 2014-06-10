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

import static com.fluidops.iwb.api.solution.SimpleInstallationResult.*;
import static org.apache.log4j.Logger.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.log4j.Logger;

import com.fluidops.iwb.api.solution.InstallationResult.InstallationStatus;
import com.fluidops.util.GenUtil;
import com.fluidops.util.StringUtil;

public class CopyFolderStructureHandler extends AbstractSolutionHandler<SimpleInstallationResult>
{
    private static final Logger logger = getLogger(SolutionService.INSTALL_LOGGER_NAME);
	private final static FileFilter EXCLUDE_SVN = new FileFilter()
    {
        @Override
        public boolean accept(File pathname)
        {
            return !pathname.getName().equals(".svn");
        }
    };
    private final String rootRelPath;
    private final File applicationRoot;
    private final FileFilter fileFilter;
    private final InstallationStatus successStatus;
    
    public CopyFolderStructureHandler(File applicationRoot, String rootRelPath)
    {
        this(applicationRoot, rootRelPath, InstallationStatus.INSTALLED_SUCCESSFULLY);
    }
    
    public CopyFolderStructureHandler(File applicationRoot, String rootRelPath, String wildcardPattern)
    {
    	this(applicationRoot, rootRelPath, new WildcardFileFilter(wildcardPattern));
    }
    
    public CopyFolderStructureHandler(File applicationRoot, String rootRelPath, FileFilter fileFilter)
    {
    	this(applicationRoot, rootRelPath, fileFilter, InstallationStatus.INSTALLED_SUCCESSFULLY);
    }
    
    public CopyFolderStructureHandler(File applicationRoot, String rootRelPath, InstallationStatus successStatus)
    {
    	this(applicationRoot, rootRelPath, EXCLUDE_SVN, successStatus);
    }
    
    public CopyFolderStructureHandler(File applicationRoot, String rootRelPath, String wildcardPattern, InstallationStatus successStatus)
    {
    	this(applicationRoot, rootRelPath, new WildcardFileFilter(wildcardPattern), successStatus);
    }
    
    public CopyFolderStructureHandler(File applicationRoot, String rootRelPath, FileFilter fileFilter, InstallationStatus successStatus)
    {
        this.applicationRoot = applicationRoot;
        this.rootRelPath = rootRelPath;
		this.fileFilter = fileFilter;
		this.successStatus = successStatus;
    }
    
    /**
     * This method should be overridden by subclasses to provide some meaningful name 
     * for this solution handler, e.g for error messages. This name may include configuration 
     * details such as file names to be handled.
     * 
     * <p>
     * The default implementation simply returns the solution handler's class name.
     * </p>
     * 
     * @return display name
     */
    protected String getDisplayName() {
    	return getClass().getName();
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
    	return getDisplayName();
    }
    
    @Override
    public final SimpleInstallationResult doInstall(SolutionInfo solutionInfo, File solutionDir)
    {
        try {
        	File solutionFolderDir = (StringUtil.isNotNullNorEmpty(rootRelPath) ? new File(solutionDir, rootRelPath) : solutionDir);
            File applicationFolderDir = (StringUtil.isNotNullNorEmpty(rootRelPath) ? new File(this.applicationRoot, rootRelPath) : this.applicationRoot);
            if(!solutionFolderDir.exists()) return nothing();
            
    		int count = copyFolder(solutionFolderDir, applicationFolderDir, fileFilter);
    		if (count > 0) {
    			return success(successStatus);
    		} else {
    			return nothing();
    		}
        } catch(Exception ex) {
        	logger.error("failed to handle solution " + solutionInfo.getName() + " with handler " + getDisplayName() + ": " + ex.getMessage());
        	logger.debug("details:", ex);
            return failure(ex);
        }
    }
    
    public static int copyFolder(File source, File destination, FileFilter filter) throws IOException
    {
        if(!source.isDirectory()) throw new RuntimeException("Source must be a directory: " + source);
        
        GenUtil.mkdirs(destination);
        
        int copiedFiles = 0;
        for (File srcFile : source.listFiles(filter))
        {
            File newFile = new File(destination, srcFile.getName());
            if(srcFile.isDirectory()) 
                copiedFiles += copyFolder(srcFile, newFile, filter);
            else {
            	FileUtils.copyFile(srcFile, newFile);
                copiedFiles++;
            }
        }
        
        return copiedFiles;
    }
}
