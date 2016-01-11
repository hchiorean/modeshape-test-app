package uk.co.lucasweb.modeshape.test;

import com.google.common.io.CharStreams;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.NodeManager;
import test.TestServlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * @author Richard Lucas
 */
@RunAsClient
@RunWith(Arquillian.class)
public class NodeManagerClientIT {

    private static final Logger LOGGER = Logger.getLogger(NodeManagerClientIT.class);

    @ArquillianResource
    URL baseURL;

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, "test-app.war")
                .addClasses(TestServlet.class, NodeManager.class)
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml").importRuntimeDependencies().resolve().withTransitivity().asFile())
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/jboss-deployment-structure.xml"), "jboss-deployment-structure.xml")
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/beans.xml"), "beans.xml")
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/jboss-web.xml"), "jboss-web.xml")
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/web.xml"), "web.xml");
    }

    @Test
    public void shouldAddNodesConcurrentlyUsingServlet() {
        IntStream.range(0, 200).parallel().forEach(this::addNode);
    }

    private void addNode(int i) {
        CloseableHttpClient httpClient = HttpClientBuilder
                .create()
                .build();

        String url = baseURL + "run";
        HttpPost httpPost = new HttpPost(url);
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (200 != statusCode) {
                String reason = Optional.of(response.getEntity())
                        .map(httpEntity -> {
                            try {
                                return httpEntity.getContent();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .map(is -> {
                            try (InputStreamReader reader = new InputStreamReader(is)) {
                                return CharStreams.toString(reader);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }).orElse("none");
                throw new RuntimeException("Failed. " + reason);
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            httpPost.abort();
            throw new RuntimeException("Failed");
        }
    }
}