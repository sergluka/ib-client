# Description

“ib-client” is a Java wrapper around native TWS API provided by Interactive Brokers company, the leader between electronic trading platforms. 

# The native API dissadvantages

The native API is powerful but is hard and inconvenient to use. Before any business logic can be written, a developer has to write a large amount of boilerplate code, resolve concurrency issues, handle reconnects, filter out order statuses duplicates, check incoming messages, etc.

# 'ib-client' advantages

1. Hides native API complexitiy
2. Uses Java 8
3. Use only 2 lines to connect
4. Automatically restores connection to TWS
5. Use power of reactive programming by using  [Reactor](https://projectreactor.io/)
6. Build-in and verbose logging
7. Production ready. 'ib-client' is used as a base of 20+ projects

# Examples

You can see usages example in [tests](src/test-integration/java/com/finplant/ib/IbClientTest.groovy)
