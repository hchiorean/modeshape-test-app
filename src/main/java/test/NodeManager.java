package test;

import org.jboss.logging.Logger;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * @author Richard Lucas
 */
public class NodeManager {

    private static final Logger LOGGER = Logger.getLogger(NodeManager.class);

    private final Repository repository;
    private final UserTransaction userTransaction;


    public NodeManager(Repository repository, UserTransaction userTransaction) {
        this.repository = repository;
        this.userTransaction = userTransaction;
    }

    public void addNodes(int i) {
        Session session = null;
        try {
            session = repository.login();
            startTransaction();
            Node level1 = addNode("/", session);
            String path = level1.getPath();
            Session s = session;
            IntStream.range(0, 10).forEach(j -> addNode(path, s));
            commitTransaction();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rollbackTransaction();
        } finally {
            if (session != null) {
                session.logout();
                session = null;
            }
        }

        try {
            session = repository.login();
            queryNodes(session);
            session.logout();
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private void startTransaction() {
        try {
            userTransaction.begin();
            LOGGER.info("Begin");
        } catch (SystemException | NotSupportedException e) {
            LOGGER.error("Failed to start transaction", e);
        }
    }

    private void commitTransaction() throws Exception {
        userTransaction.commit();
        LOGGER.info("Commit");
    }

    private void rollbackTransaction() {
        try {
            userTransaction.rollback();
            LOGGER.info("Rollback");
        } catch (Exception e) {
            LOGGER.error("Failed to rollback transaction", e);
        }
    }

    private String queryNodes(Session session) {
        try {
            QueryManager queryManager = session.getWorkspace().getQueryManager();

            Query query = queryManager.createQuery("SELECT node.* FROM [mix:title] AS node WHERE ISDESCENDANTNODE('/')", Query.JCR_SQL2);
            QueryResult result = query.execute();

            return "nodes: " + result.getNodes().getSize();
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private Node addNode(String parentNodePath, Session session) {
        try {
            return distributedCreate(parentNodePath, UUID.randomUUID().toString(), session);
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private Node distributedCreate(String parentNodePath, String uuid, Session session) throws RepositoryException {

        String nodeLevelOneName = uuid.substring(0, 2);
        String nodeLevelOnePath = parentNodePath + "/" + nodeLevelOneName;

        String nodeLevelTwoName = uuid.substring(2, 4);
        String nodeLevelTwoPath = nodeLevelOnePath + "/" + nodeLevelTwoName;

        String nodeLevelThreeName = uuid.substring(4, 6);
        String nodeLevelThreePath = nodeLevelTwoPath + "/" + nodeLevelThreeName;

        addLevel(parentNodePath, nodeLevelOneName, nodeLevelOnePath, session);
        addLevel(nodeLevelOnePath, nodeLevelTwoName, nodeLevelTwoPath, session);
        addLevel(nodeLevelTwoPath, nodeLevelThreeName, nodeLevelThreePath, session);
        session.save();

        Node parent = session.getNode(parentNodePath);
        Node node = parent.addNode(uuid);
        node.addMixin("mix:title");
        node.setProperty("jcr:title", "test");
        session.save();

        return node;
    }

    private void addLevel(String currentNodeLevelPath, String nextNodeLevelName, String nextNodeLevelPath, Session session) throws RepositoryException {
        if (!session.nodeExists(nextNodeLevelPath)) {
            Node parentNode = session.getNode(currentNodeLevelPath);
            parentNode.addNode(nextNodeLevelName);
        }
    }
}
