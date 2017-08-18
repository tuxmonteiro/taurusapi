# TaurusAPI - BlazeMeter Taurus API

## Dependency
TaurusAPI depends BZT (BlazeMeter Taurus).
http://gettaurus.org/install/Installation/

## Building & Running
```bash
mvn clean package -DskipTests spring-boot:run -Dbzt.cmd=/usr/local/bin/bzt
```

## Creating test
```bash
curl -XPOST -H 'content-type: application/json' -d @jsondata.json  -v 127.0.0.1:8080/test
```

## Show all tests
```bash
curl -v 127.0.0.1:8080/test
```

## Consulting test result
```bash
curl -v 127.0.0.1:8080/test/0
```
