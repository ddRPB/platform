package org.labkey.pipeline.importer;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.InvalidFileException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.io.IOException;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderImportContext extends AbstractFolderContext
{
    private File _folderXml;

    public FolderImportContext(User user, Container c, File folderXml, Logger logger)
    {
        super(user, c, null, logger);
        _folderXml = folderXml;
    }

    public FolderImportContext(User user, Container c, FolderDocument folderDoc, Logger logger)
    {
        super(user, c, folderDoc, logger);
    }

    @Override
    public synchronized FolderDocument getDocument() throws ImportException, InvalidFileException
    {
        FolderDocument folderDoc = super.getDocument();

        // XStream can't seem to serialize the FolderDocument XMLBean, so we initially set to null and parse the file on demand
        if (null == folderDoc)
        {
            try
            {
                folderDoc = readFolderDocument(_folderXml);
            }
            catch (IOException e)
            {
                throw new ImportException("Exception loading folder.xml file", e);
            }

            setDocument(folderDoc);
        }

        return folderDoc;
    }

    // Assume file was referenced in folder.xml file   // TODO: Context should hold onto the root -- shouldn't have to pass it in
    public File getFolderFile(File root, File dir, String name) throws ImportException
    {
        return getFolderFile(root, dir, name, _folderXml.getName());
    }

    public File getFolderFile(File root, File dir, String name, String source) throws ImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new ImportException(source + " refers to a file that does not exist: " + ImportException.getRelativePath(root, file));

        if (!file.isFile())
            throw new ImportException(source + " refers to " + ImportException.getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }

    public File getFolderDir(File root, String dirName) throws ImportException
    {
        File dir = null != dirName ? new File(root, dirName) : root;

        if (!dir.exists())
            throw new ImportException(_folderXml.getName() + " refers to a directory that does not exist: " + ImportException.getRelativePath(root, dir));

        if (!dir.isDirectory())
            throw new ImportException(_folderXml.getName() + " refers to " + ImportException.getRelativePath(root, dir) + ": expected a directory but found a file");

        return dir;
    }

    private FolderDocument readFolderDocument(File folderXml) throws ImportException, IOException, InvalidFileException
    {
        if (!folderXml.exists())
            throw new ImportException(folderXml.getName() + " file does not exist.");

        FolderDocument folderDoc;

        try
        {
            folderDoc = FolderDocument.Factory.parse(folderXml, XmlBeansUtil.getDefaultParseOptions());
            XmlBeansUtil.validateXmlDocument(folderDoc);
        }
        catch (XmlException e)
        {
            throw new InvalidFileException(folderXml.getParentFile(), folderXml, e);
        }
        catch (XmlValidationException e)
        {
            throw new InvalidFileException(folderXml.getParentFile(), folderXml, e);
        }

        return folderDoc;
    }
}
