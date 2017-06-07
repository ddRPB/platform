package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.ValidationException;

import java.io.File;

/**
 * Created by klum on 6/2/2017.
 */
public class AssayUploadFileResolver
{
    /**
     * Resolves files for attachment or file property columns
     *
     * @param o the value to resolve
     * @param property the DomainProperty of the destination column
     */
    @Nullable
    public static File resolve(Object o, Container container, DomainProperty property) throws ValidationException
    {
        if (o == null)
            return null;

        String uri = property.getType().getTypeURI();
        if (uri.equals(PropertyType.FILE_LINK.getTypeUri()) || uri.equals(PropertyType.ATTACHMENT.getTypeUri()))
        {
            File fileToResolve = null;

            if (o instanceof File)
                fileToResolve = (File)o;
            else if (o instanceof String)
            {
                fileToResolve = new File(o.toString());
            }

            if (fileToResolve != null)
            {
                // For security reasons, make sure the user hasn't tried to reference a file that's not under
                // the pipeline root. Otherwise, they could get access to any file on the server

                PipeRoot root = PipelineService.get().findPipelineRoot(container);
                if (root == null)
                {
                    throw new ValidationException("Pipeline root not available in container " + container);
                }

                if (!root.isUnderRoot(fileToResolve))
                {
                    File resolved = root.resolvePath(fileToResolve.toString());
                    if (resolved == null)
                        throw new ValidationException("Cannot reference file " + fileToResolve + " from container " + container);

                    return resolved;
                }
            }
        }
        return null;
    }
}
