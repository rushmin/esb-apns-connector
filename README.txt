
Ant targets
===========

NOTE : Before you run the following Ant targets create a file named build.properties and add the following entry

esb.home=/esb/home/directory

1) copy-connector - Builds the connector package and copies to the server.

2) copy-config - Copies ESB artificats like proxies etc.. to test the connnector.


Running Integration Tests
=========================

1) Create a directory named {PROJECT_HOME}/repository and copy wso2esb-4.8.1.zip in to that directory.

2) Use 'mvn clean test' to run the test cases. 
