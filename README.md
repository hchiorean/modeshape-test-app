# Modeshape Concurrency Issue Test Application

This is a simple application that demonstrates a concurrency issue in Modeshape. The issue occurs when running ModeShape 4.5.0.Final in Wildfly 8.2.1 using the Wildfly Kit.

The problem appears when a number of threads are making changes to the same node tree using user transactions. This can result in a child node having a reference to a parent node, the the parent node
not having a reference to the child node. Once this occurs any attempt to access the child node results in a org.modeshape.jcr.cache.NodeNotFoundInParentException.

The problem does not appear to occur when run outside of Wildfly.

### Pre-Requisites

* Java 8
* Maven
* Wildfly 8.2.1 with the Modeshape 4.5.0.Final Wildfly Kit installed

### Running the application

1. Copy the standalone directory in the root of the project to the root of your wildfly deployment
2. Run ```mvn verify```
 
This normally fails the first time but may need to be run more than once to re-create the issue.
