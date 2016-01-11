package uk.co.lucasweb.modeshape.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.NodeManager;
import test.TestServlet;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.jcr.Repository;
import javax.transaction.UserTransaction;
import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Richard Lucas
 */
@RunWith(Arquillian.class)
public class NodeManagerIT {

    private static final Logger LOGGER = Logger.getLogger(NodeManagerIT.class);

    @Resource
    UserTransaction userTransaction;
    @Resource(mappedName = "java:/jcr/test-app-repo")
    Repository repository;
    @Resource(mappedName = "java:jboss/ee/concurrency/executor/default")
    private ManagedExecutorService managedExecutorService;

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, "test-app.war")
                .addClasses(NodeManager.class)
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/jboss-deployment-structure.xml"), "jboss-deployment-structure.xml")
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/beans.xml"), "beans.xml");
    }

    @Test
    public void shouldAddNodesUsingUnmanagedThreads() throws Exception {
        // This fails fairly frequently
        NodeManager nodeManager = new NodeManager(repository, userTransaction);
        IntStream.range(0, 200).parallel().forEach(nodeManager::addNodes);
    }

    @Test
    public void shouldAddNodesUsingManagedThreads() throws Exception {
        // This works

        NodeManager nodeManager = new NodeManager(repository, userTransaction);

        List<Future<?>> result = IntStream.range(0, 200)
                .mapToObj(i -> managedExecutorService.submit(() -> nodeManager.addNodes(i)))
                .collect(Collectors.toList());
        result.forEach(future -> {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}