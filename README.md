# Spring Boot Kafka Demo

Demo project to show Spring Kafka usage in real life scenarios.

### Requirements
* Java 17 (we have Spring Boot 3 on the board)
* Docker up & running

### Structure

There are two branches in this repository...

**Simple** - shows minimal implementation to start with

Features:

- Producer / Consumer example
- Automatic Kafka deployment with Docker Compose *(Spring Boot 3.1 feature)*
- Integration test with Kafka on Testcontainers

**Advanced** - shows extended implementation, more close to production use

Features. Everything from Simple, plus:
- Full properties for consumer and producer in `application.yaml`
- Retries for consumer
- Failed messages are logged and saved to MongoDB

There are minimum files here, so you can see more code at a glance, 
but do not forget to keep a good project structure.

Additional notes:

- Kafka messages unique identifiers.
  Consider having some translation UUID field in your Kafka message headers or as a message key, 
  you will be able to look for all operations related to this identifier.

- You should ensure that every log has context information.  
  Consider adding a user identifiers, such as id or email in headers,  
  to be able to log it even before parsing and processing the message.
