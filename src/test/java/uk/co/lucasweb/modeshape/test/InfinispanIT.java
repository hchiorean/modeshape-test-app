package uk.co.lucasweb.modeshape.test;

import static org.junit.Assert.assertTrue;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.annotation.Resource;
import javax.transaction.UserTransaction;
import org.infinispan.AdvancedCache;
import org.infinispan.manager.DefaultCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.InfinispanManager;

/**
 * @author Horia Chiorean
 */
@RunWith(Arquillian.class)
public class InfinispanIT {

    @Resource
    UserTransaction userTransaction;

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, "test-app-ispn"  + UUID.randomUUID().toString() + ".war")
                         .addAsWebInfResource(new File("src/main/webapp/WEB-INF/jboss-deployment-structure.xml"), "jboss-deployment-structure.xml")
                         .addClasses(InfinispanManager.class)
                         .addAsWebInfResource(new File("src/main/webapp/WEB-INF/beans.xml"), "beans.xml");
    } 
    
    @Test
    public void shouldReadWriteConcurrently() throws Exception {
        File configFile = new File(System.getProperty("jboss.server.config.dir") + "/modeshape/test-app-repo-cache-config.xml");
        assertTrue(configFile.exists() && configFile.canRead());
        DefaultCacheManager cacheManager = new DefaultCacheManager(new FileInputStream(configFile));
        AdvancedCache<String, List<String>> cache = cacheManager.<String, List<String>>getCache("test-app-repo", true).getAdvancedCache();
        assertTrue(cache != null);
      
        InfinispanManager infinispanManager = new InfinispanManager(cache, userTransaction);
        IntStream.range(0, 200).parallel().forEach(infinispanManager::writeAndReadEntries);    
    }
}