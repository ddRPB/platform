/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.search.model;

import org.apache.log4j.Category;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.collections.Cache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.*;
import org.labkey.api.webdav.Resource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import javax.servlet.ServletContextEvent;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 11:09:03 AM
 *
 * The Crawler has several components
 *
 * 1) DirectoryCrawler
 *  The directory crawler looks for new directories, and file updates.
 *  By default every known directory will be scanned for new folders every 12hrs
 *
 * 2) FileUpdater
 *  When a new directory or file is found it is queued up for indexing, this is where throttling 
 *  will occur (when implemented)
 *
 * The SearchService also has it's own thread pool we use when we find files to index, but the
 * background crawling is pretty different and needs its own scheduling behavior.
 */
public class DavCrawler implements ShutdownListener
{
//    SearchService.SearchCategory folderCategory = new SearchService.SearchCategory("Folder", "Folder");

    long _defaultWait = TimeUnit.SECONDS.toMillis(60);
    long _defaultBusyWait = TimeUnit.SECONDS.toMillis(5);

    RateLimiter _listingRateLimiter = new RateLimiter(10, 1000); // 10/second

    // to make testing easier, break out the interface for persisting crawl state
    public interface SavePaths
    {
        final static java.util.Date nullDate = new java.sql.Timestamp(DateUtil.parseStringJDBC("1899-12-31"));
        final static java.util.Date oldDate =  new java.sql.Timestamp(DateUtil.parseStringJDBC("1967-10-04"));

        // collections

        /** update path (optionally create) */
        boolean updatePath(Path path, java.util.Date lastIndexed, java.util.Date nextCrawl, boolean create);

        /** insert path if it does not exist */
        boolean insertPath(Path path, java.util.Date nextCrawl);
        void updatePrefix(Path path, Date next, boolean forceIndex);
        void deletePath(Path path);

        /** <lastCrawl, nextCrawl> */
        public Map<Path, Pair<Date,Date>> getPaths(int limit);
        public Date getNextCrawl();

        // files
        public Map<String,Date> getFiles(Path path);
        public boolean updateFile(Path path, Date lastIndexed);
    }
    

    final static Category _log = Category.getInstance(DavCrawler.class);

    
    DavCrawler()
    {
        ContextListener.addShutdownListener(this);
        _crawlerThread.setDaemon(true);
        _crawlerThread.start();
    }


    static DavCrawler _instance = new DavCrawler();
    boolean _shuttingDown = false;

    
    public static DavCrawler getInstance()
    {
        return _instance;
    }

    
    public void shutdownStarted(ServletContextEvent servletContextEvent)
    {
        _shuttingDown = true;
        if (null != _crawlerThread)
            _crawlerThread.interrupt();
    }

    
    /**
     * Aggressively scan the file system for new directories and new/updated files to index
     * 
     * @param path
     * @param force if (force==true) then don't check lastindexed and modified dates
     */


    public void startFull(Path path, boolean force)
    {
        _log.debug("START FULL: " + path);

        if (null == path)
            path = WebdavService.get().getResolver().getRootPath();

        _paths.updatePrefix(path, null, force);

        addPathToCrawl(path, null);
    }


    /**
     * start a background process to watch directories
     * optionally add a path at the same time
     */
    public void addPathToCrawl(Path start, Date nextCrawl)
    {
        _log.debug("START CONTINUOUS " + start.toString());

        if (null != start)
            _paths.updatePath(start, null, nextCrawl, true);
        pingCrawler();
    }


    LinkedList<Pair<Resource,Date>> _recent = new LinkedList<Pair<Resource,Date>>();

    
    class IndexDirectoryJob implements Runnable
    {
        SearchService.IndexTask _task;
        Path _path;
        boolean _full;
        Date _lastCrawl=null;
        Date _nextCrawl=null;
        Date _indexTime = null;
        
        /**
         * @param path
         */
        IndexDirectoryJob(Path path, Date last, Date next)
        {
            _path = path;
            _lastCrawl = last;
            _full = next.getTime() <= SavePaths.oldDate.getTime();
        }


        public void submit()
        {
            _task = getSearchService().createTask("Index " + _path.toString());
            _task.addRunnable(this, SearchService.PRIORITY.crawl);
            _task.setReady();
        }


        public void run()
        {
            _log.debug("IndexDirectoryJob.run(" + _path + ")");

            final Resource r = getResolver().lookup(_path);

            if (null == r || !r.isCollection() || !r.shouldIndex())
            {
                _paths.deletePath(_path);
                return;
            }

            _listingRateLimiter.add(1,true);

            _indexTime = new Date(System.currentTimeMillis());
            long changeInterval = (r instanceof WebdavResolver.WebFolder) ? Cache.DAY / 2 : Cache.DAY;
            long nextCrawl = _indexTime.getTime() + (long)(changeInterval * (0.5 + 0.5 * Math.random()));
            _nextCrawl = new Date(nextCrawl);

            _task.onSuccess(new Runnable() {
                public void run()
                {
                    _paths.updatePath(_path, _indexTime, _nextCrawl, true);
                    r.setLastIndexed(_indexTime.getTime());
                    addRecent(r);
                }
            });

            // if this is a web folder, call enumerate documents
            if (r instanceof WebdavResolver.WebFolder)
            {
                Container c = ContainerManager.getForId(r.getContainerId());
                if (null == c)
                    return;
                getSearchService().indexContainer(_task, c,  _full ? null : _lastCrawl);
            }

            // get current index status for files
            // CONSIDER: store lastModifiedTime in crawlResources
            // CONSIDER: store documentId in crawlResources
            Map<String,Date> map = _paths.getFiles(_path);

            for (Resource child : r.list())
            {
                if (_shuttingDown)
                    return;
                if (child.isFile())
                {
                    Date lastIndexed = map.remove(child.getName());
                    if (null == lastIndexed)
                        lastIndexed = SavePaths.nullDate;
                    long lastModified = child.getLastModified();
                    if (lastModified <= lastIndexed.getTime())
                        continue;
                    if (skipFile(child))
                    {
                        // just index the name and that's all
                        final Resource wrap = child;
                        ActionURL url = new ActionURL(r.getExecuteHref(null));
                        child = new SimpleDocumentResource(r.getPath(), r.getDocumentId(), r.getContainerId(), r.getContentType(),
                                new byte[0], url, null){
                            @Override
                            public void setLastIndexed(long ms)
                            {
                                wrap.setLastIndexed(ms);
                            }
                        };
                    }
                    _task.addResource(child, SearchService.PRIORITY.background);
                }
                else if (!child.exists()) // happens when pipeline is defined but directory doesn't exist
                {
                    continue;
                }
                else if (!child.shouldIndex())
                {
                    continue;
                }
                else if (skipContainer(child))
                {
                    continue;
                }
                else
                {
                    long childCrawl = SavePaths.oldDate.getTime();
                    if (!(r instanceof WebdavResolver.WebFolder))
                        childCrawl += child.getPath().size()*1000; // bias toward breadth first
                    if (_full)
                    {
                        _paths.updatePath(child.getPath(), null, new Date(childCrawl), true);
                        pingCrawler();
                    }
                    else
                    {
                        _paths.insertPath(child.getPath(), new Date(childCrawl));
                    }
                }
            }

            // as for the missing
            SearchService ss = getSearchService();
            for (String missing : map.keySet())
            {
                Path path = Path.parse(missing);
                String docId =  "dav:" + missing;
                ss.deleteResource(docId);
            }
        }
    }


    void addRecent(Resource r)
    {
        synchronized (_recent)
        {
            Date d = new Date(System.currentTimeMillis());
            while (_recent.size() > 20)
                _recent.removeFirst();
            while (_recent.size() > 0 && _recent.getFirst().second.getTime() < d.getTime()-10*60000)
                _recent.removeFirst();
            _recent.add(new Pair(r,d));
        }
    }


    final Object _crawlerEvent = new Object();

    void pingCrawler()
    {
        synchronized (_crawlerEvent)
        {
            _crawlerEvent.notifyAll();
        }
    }


    void _wait(Object event, long wait)
    {
        if (wait == 0)
            return;
        try
        {
            synchronized (event)
            {
                event.wait(wait);
            }
        }
        catch (InterruptedException x)
        {
        }
    }


    Thread _crawlerThread = new Thread("DavCrawler")
    {
        @Override
        public void run()
        {
            long delay = 0;
            
            while (!_shuttingDown)
            {
                try
                {
                    SearchService ss = getSearchService();
                    if (null != ss && !((AbstractSearchService)ss).waitForRunning())
                        continue;
                    _wait(_crawlerEvent, delay);
                    delay = _defaultBusyWait;
                    if (null == ss || ss.isBusy())
                        continue;
                    delay = findSomeWork();
                }
                catch (Throwable t)
                {
                    _log.error("Unexpected error", t);
                }
            }
        }
    };
    

    long findSomeWork()
    {
        _log.debug("findSomeWork()");

        boolean fullCrawl = false;
        Map<Path,Pair<Date,Date>> map = _paths.getPaths(100);

        if (map.isEmpty())
        {
            return _defaultWait;
        }

        List<Path> paths = new ArrayList<Path>(map.size());
        for (Map.Entry<Path,Pair<Date,Date>> e : map.entrySet())
        {
            Path path = e.getKey();
            Date lastCrawl = e.getValue().first;
            Date nextCrawl = e.getValue().second;
            boolean full = nextCrawl.getTime() < SavePaths.oldDate.getTime();
            fullCrawl |= full;

            _log.debug("crawl: " + path.toString());
            paths.add(path);
            new IndexDirectoryJob(path, lastCrawl, nextCrawl).submit();
        }
        long delay = (fullCrawl || map.size() > 0) ? 0 : _defaultWait;
        return delay;
    }
    

    static boolean skipContainer(Resource r)
    {
        String name = r.getName();

        if ("@wiki".equals(name))
            return true;

        if (".svn".equals(name))
            return true;

        if (".Trash".equals(name))
            return true;

        // if symbolic link
        //  return true;
        
        if (name.startsWith("."))
            return true;
        
        return false;
    }

    
    static boolean skipFile(Resource r)
    {
        // let's not index large files, or files that probably aren't useful to index
        String contentType = r.getContentType();
        if (contentType.startsWith("image/"))
            return true;
        String name = r.getName();
        String ext = "";
        int i = name.lastIndexOf(".");
        if (i != -1)
            ext = name.substring(i+1).toLowerCase();
        if (ext.equals("mzxml") || ext.equals("mzml"))
            return true;
        return false;
    }
    

    //
    // dependencies
    //

    SavePaths _paths = new org.labkey.search.model.SavePaths();
    WebdavResolver _resolver = null;
    SearchService _ss = null;

    void setSearchService(SearchService ss)
    {
        _ss = ss;
    }
    
    SearchService getSearchService()
    {
        if (null == _ss)
            _ss = ServiceRegistry.get().getService(SearchService.class);
        return _ss;
    }

    void setResolver(WebdavResolver resolver)
    {
        _resolver = resolver;
    }
    
    WebdavResolver getResolver()
    {
        if (_resolver == null)
            _resolver = WebdavService.get().getResolver();
        return _resolver;
    }


    public Map getStats()
    {
        SearchService ss = getSearchService();
        boolean paused = !ss.isRunning();
        
        Map m = new LinkedHashMap();
        try
        {
            DbSchema s = DbSchema.get("search");
            long now = System.currentTimeMillis();

            Integer uniqueCollections = Table.executeSingleton(s, "SELECT count(*) FROM search.crawlcollections", null, Integer.class);
            m.put("Number of unique folders/directories", uniqueCollections);
            
            if (!paused)
            {
                Date nextHour = new Date(now + 60*60000);
                Integer countNext = Table.executeSingleton(s, "SELECT count(*) FROM search.crawlcollections where nextCrawl < ?", new Object[]{nextHour}, Integer.class);
                m.put("Directories scheduled for scan in next 60 minutes", countNext);
            }

            Pair<Resource,Date>[] recent;
            synchronized(_recent)
            {
                recent = new Pair[_recent.size()];
                _recent.toArray(recent);
                Arrays.sort(recent, new Comparator<Pair<Resource,Date>>(){
                    public int compare(Pair<Resource,Date> o1, Pair<Resource,Date> o2)
                    {
                        return o1.second.compareTo(o2.second);
                    }
                });
            }
            StringBuilder activity = new StringBuilder();
            long time = now - 60*60000;
            for (Pair<Resource,Date> p : recent)
            {
                Resource r = p.first;
                Date d = p.second;
                if (d.getTime() < time) continue;
                long dur = now-d.getTime();
                dur -= dur % 1000;
                String ago = DateUtil.formatDuration(dur);
                activity.append("<a href='" + PageFlowUtil.filter(r.getLocalHref(null)) + "'>" + PageFlowUtil.filter(r.getName()) + "</a> (" + ago + " ago)<br>\n");
            }
            if (paused)
            {
                activity.append("PAUSED");
                if (recent.length > 0)
                    activity.append (" (queue may take a while to clear)");
            }
            m.put("Recent crawler activity", activity.toString());
        }
        catch (SQLException x)
        {
            _log.error("Unexpected error", x);
        }
        return m;
    }
}