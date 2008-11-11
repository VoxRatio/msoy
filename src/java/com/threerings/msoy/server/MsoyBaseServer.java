//
// $Id$

package com.threerings.msoy.server;

import java.security.Security;
import java.util.Formatter;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Statistics;

import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.jdbc.depot.EHCacheAdapter;
import com.samskivert.jdbc.depot.PersistenceContext;

import com.samskivert.util.RunQueue;
import com.samskivert.util.StringUtil;

import com.threerings.presents.server.ReportManager;

import com.threerings.admin.server.AdminProvider;
import com.threerings.admin.server.ConfigRegistry;

import com.threerings.whirled.server.WhirledServer;

import com.threerings.msoy.data.StatType;
import com.threerings.msoy.server.util.Retry;
import com.threerings.msoy.server.util.RetryInterceptor;

import com.threerings.msoy.admin.server.RuntimeConfig;

/**
 * Provides the set of services that are shared between the Game and World servers.
 */
public abstract class MsoyBaseServer extends WhirledServer
{
    /** Configures dependencies needed by the Msoy servers. */
    public static class Module extends WhirledServer.Module
    {
        @Override protected void configure () {
            super.configure();
            // depot dependencies (we will initialize this persistence context later when the
            // server is ready to do database operations; not initializing it now ensures that no
            // one sneaks any database manipulations into the dependency resolution phase)
            bind(PersistenceContext.class).toInstance(new PersistenceContext());
            // presents dependencies
            bind(ReportManager.class).toInstance(new ReportManager() {
                // disables state of the server report logging
                @Override public void init (RunQueue rqueue) {
                    // nada; don't schedule our interval
                }
            });
            // msoy dependencies
            bindInterceptor(Matchers.any(), Matchers.annotatedWith(Retry.class),
                            new RetryInterceptor());
        }
    }

    @Override // from WhirledServer
    public void init (final Injector injector)
        throws Exception
    {
        // before doing anything else, let's ensure that we don't cache DNS queries forever -- this
        // breaks Amazon S3, specifically.
        Security.setProperty("networkaddress.cache.ttl" , "30");

        // initialize event logger
        _eventLog.init(getIdent());

        super.init(injector);

        // initialize our persistence context
        ConnectionProvider conprov = ServerConfig.createConnectionProvider();
        _perCtx.init("msoy", conprov, new EHCacheAdapter(_cacheMgr));

        // initialize our depot repositories; running all of our schema and data migrations
        _perCtx.initializeRepositories(true);

        // set up our default object access controller
        _omgr.setDefaultAccessController(MsoyObjectAccess.DEFAULT);

        // create and set up our configuration registry, admin service and runtime config
        final ConfigRegistry confReg = createConfigRegistry();
        AdminProvider.init(_invmgr, confReg);
        _runtime.init(_omgr, confReg);

        // initialize our bureau manager
        _bureauMgr.init(getListenPorts()[0]);

        // set up the right client factories
        configSessionFactory();

        // add a reporter that conveys the statistics of our cache manager
        _reportMan.registerReporter(new ReportManager.Reporter() {
            public void appendReport (StringBuilder buf, long now, long sinceLast, boolean reset) {
                buf.append("* ehcache.CacheManager:\n");
                Formatter fmt = new Formatter(buf);
                fmt.format(HFMT, "cache", "size", "(kb)", "hit%", "hits", "inmem", "disk", "miss",
                           "evict", "avgget");
                for (String cname : _cacheMgr.getCacheNames()) {
                    Cache cache = _cacheMgr.getCache(cname);
                    Statistics stats = cache.getStatistics();
                    String sname = cname.substring(cname.lastIndexOf(".")+1);
                    long accesses = stats.getCacheHits() + stats.getCacheMisses();
                    long hpct = (accesses == 0) ? 0 : (100 * stats.getCacheHits()) / accesses;
                    fmt.format(DFMT, StringUtil.truncate(sname, 20), cache.getSize(),
                               cache.calculateInMemorySize()/1024, hpct,
                               stats.getCacheHits(), stats.getInMemoryHits(), stats.getOnDiskHits(),
                               stats.getCacheMisses(), stats.getEvictionCount(),
                               (int)stats.getAverageGetTime());
                }
            }
            protected static final String HFMT = "- %20s %5s %4s %4s %7s %6s %6s %8s %7s %6s\n";
            protected static final String DFMT = "- %20s %5d %4d %3d%% %7d %6d %6d %8d %7d %3d ms\n";
        });

        _bureauMgr.configClientFactories();
    }

    /**
     * Derived classes need to override this and configure their main client factory.
     */
    protected abstract void configSessionFactory ();

    @Override // from PresentsServer
    protected void invokerDidShutdown ()
    {
        super.invokerDidShutdown();

        // shutdown our persistence context (JDBC connections) and the cache manager
        _perCtx.shutdown();
        _cacheMgr.shutdown();

        // and shutdown our event logger now that everything else is done shutting down
        _eventLog.shutdown();
    }

    /**
     * Returns an identifier used to distinguish this server from others on this machine when
     * generating log files.
     */
    protected abstract String getIdent ();

    /**
     * Creates the admin config registry for use by this server.
     */
    protected abstract ConfigRegistry createConfigRegistry ()
        throws Exception;

    /** Used for caching things. */
    protected CacheManager _cacheMgr = CacheManager.getInstance();

    /** Provides database access to all of our repositories. */
    @Inject protected PersistenceContext _perCtx;

    /** Maintains runtime modifiable configuration information. */
    @Inject protected RuntimeConfig _runtime;

    /** Sends event information to an external log database. */
    @Inject protected MsoyEventLogger _eventLog;

    /** Handles state of the server reporting (used by the /status servlet). */
    @Inject protected ReportManager _reportMan;

    /** Manages our bureau launchers. */
    @Inject protected BureauManager _bureauMgr;

    /** This is needed to ensure that the StatType enum's static initializer runs before anything
     * else in the server that might rely on stats runs. */
    protected static final StatType STAT_TRIGGER = StatType.UNUSED;
}
