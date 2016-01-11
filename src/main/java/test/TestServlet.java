package test;

import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.jcr.Repository;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.UserTransaction;
import java.io.IOException;

/**
 * @author Richard Lucas
 */
@WebServlet(urlPatterns = {"/run"}, loadOnStartup = 1)
public class TestServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(TestServlet.class);

    @Resource
    UserTransaction userTransaction;
    @Resource(mappedName = "java:/jcr/test-app-repo")
    Repository repository;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        try {
            NodeManager nodeManager = new NodeManager(repository, userTransaction);
            nodeManager.addNodes(0);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
