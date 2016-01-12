package test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
import org.jboss.logging.Logger;

/**
 * @author Horia Chiorean (hchiorea@redhat.com)
 */
public class InfinispanManager {

    private static final Logger LOGGER = Logger.getLogger(InfinispanManager.class);
    private static final String ROOT_KEY = "root";

    private final UserTransaction userTransaction;
    private final AdvancedCache<String, List<String>> localCache;
    private final AdvancedCache<String, List<String>> lockingCache;
    private final Map<String, List<String>> initialValues;

    public InfinispanManager(AdvancedCache<String, List<String>> localCache, UserTransaction userTransaction) {
        this.userTransaction = userTransaction;
        this.localCache = localCache;
        this.lockingCache = localCache.withFlags(Flag.FAIL_SILENTLY).getAdvancedCache();
        this.initialValues = new HashMap<>(localCache.size());
        // load all the existing values (i.e. which were part of a previous run) 
        for (String key : localCache.keySet()) {
            this.initialValues.put(key, localCache.get(key));            
        }
    }

    public void writeAndReadEntries(int i) {
        Map<String, List<String>> valuesWritten;
        try {
            // start the user transaction
            startTransaction();
            // write a bunch of values exclusively (i.e. making sure a lock is obtained on each key before updating the value)
            valuesWritten = writeEntries();
            // commit the user transaction
            commitTransaction();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rollbackTransaction();
            throw new RuntimeException(e);
        }
        // we now have a local variable which holds all the newly added entries 
        // that, together with everything that was loaded at startup (i.e. prior to the test run) should be still in the cache
        // even if at this point other threads are writing & adding new values
        for (String writtenEntryKey : valuesWritten.keySet()) {
            List<String> expectedValues = new ArrayList<>();
            List<String> initialValuesForKey = initialValues.get(writtenEntryKey);
            if (initialValuesForKey != null) {
                expectedValues.addAll(initialValuesForKey);
            }
            expectedValues.addAll(valuesWritten.get(writtenEntryKey));
            List<String> currentValues = new ArrayList<>(localCache.get(writtenEntryKey));
            // since we've used exclusive locking, the order on the written elements should not have been tampered with and
            // therefore the following should hold true
            if (!currentValues.containsAll(expectedValues)) {
                throw new IllegalStateException("Values " + expectedValues + " were just written and committed but not all read back...");
            }
        }
    }
    
    private Map<String, List<String>> writeEntries() throws Exception {
        // note that everything that happens in this method takes place inside a user transaction
        String uuid = UUID.randomUUID().toString();
        // generate a bunch of random keys 
        // it's important that enough of these overlap across threads to cause lock contention
        Map<String, List<String>> newValuesByKey = new HashMap<>();
        for (int i = 0; i < uuid.length() - 2 ; i += 2) {
            String id = uuid.substring(i, i + 2);
            addEntryWithChildToCache(id, newValuesByKey);
        }
        for (int i = 0; i < 3; i++) {
            // add repeatedly values under the same key to force "reentrant" lock contention
            addEntryWithChildToCache(ROOT_KEY, newValuesByKey);
        }
        return newValuesByKey;
    }
    
    private void addEntryWithChildToCache(String parentKey, Map<String, List<String>> newValuesByKey) throws Exception {
        // lock the entry first - this *should* ensure that only one thread can change a given key per transaction
        while (!lockingCache.lock(parentKey)) {
            Thread.sleep(100);
        }
        List<String> currentValue = localCache.get(parentKey);
        currentValue = currentValue == null ? new ArrayList<>() : new ArrayList<>(currentValue);
        
        String childKey = UUID.randomUUID().toString();
        currentValue.add(childKey);
        localCache.put(parentKey, currentValue);
        newValuesByKey.put(parentKey, new ArrayList<>(currentValue));
        
        localCache.put(childKey, new ArrayList<>());
        newValuesByKey.put(childKey, Collections.emptyList());   
    }

    private void startTransaction() {
        try {
            LOGGER.info("Begin");
            userTransaction.begin();
        } catch (SystemException | NotSupportedException e) {
            LOGGER.error("Failed to start transaction", e);
        }
    }

    private void commitTransaction() throws Exception {
        LOGGER.info("Commit");
        userTransaction.commit();
    }

    private void rollbackTransaction() {
        try {
            userTransaction.rollback();
            LOGGER.info("Rollback");
        } catch (Exception e) {
            LOGGER.error("Failed to rollback transaction", e);
        }
    }
}
