package test;

import java.util.UUID;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.transaction.NotSupportedException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import org.jboss.logging.Logger;

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
            addNode("/", session);
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
            int status = userTransaction.getStatus();
            if (status != Status.STATUS_NO_TRANSACTION) {
                throw new IllegalStateException("Expected the transaction to have been committed");
            }

            session = repository.login();
            queryNodes(session);
            session.logout();
        } catch (Exception e) {
            LOGGER.error("error", e);
            throw new RuntimeException(e);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private void startTransaction() {
        try {
            LOGGER.info("Beginning transaction from client...");
            userTransaction.begin();
        } catch (SystemException | NotSupportedException e) {
            LOGGER.error("Failed to start transaction", e);
        }
    }

    private void commitTransaction() throws Exception {
        LOGGER.info("Committing transaction from client...");
        userTransaction.commit();
    }

    private void rollbackTransaction() {
        try {
            LOGGER.info("Rolling back transaction from client...");
            userTransaction.rollback();
        } catch (Exception e) {
            LOGGER.error("Failed to rollback transaction", e);
        }
    }

    private String queryNodes(Session session) {
        try {
            QueryManager queryManager = session.getWorkspace().getQueryManager();

            Query query = queryManager.createQuery("SELECT node.* FROM [mix:title] AS node", Query.JCR_SQL2);
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
